/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.stylus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Procedural + sampled "paper friction" writing sound engine.
 *
 * Inspired by davi133/brush_sfx:
 * - Real pencil acoustic friction loop (48 kHz mono PCM, 0-allocation during drawing)
 * - Dynamic pressure formant modulation (heavy pressure gives rich, dampened bite; light gives airy scratch)
 * - Logarithmic velocity-to-volume response: log10(9 * speed + 1)
 * - State-Variable Filter (SVF) resonant bandpass for crisp ink pen scratch
 * - Streamed through a low-latency 48 kHz mono AudioTrack (10 ms buffer, pipeline kept warm)
 */
class PaperSoundEngine(private val context: Context? = null) {

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val CHUNK_FRAMES = 480 // 10 ms per block at 48 kHz (ultra-low latency)
        private const val TICK_FRAMES = 1728 // 36 ms soft-touch tick at 48 kHz
        private const val ATTACK = 0.85f // fast rise: audible within 1 block (~10 ms)
        private const val RELEASE = 0.22f // smooth tail after pen lift
        private const val SILENCE_GAIN = 0.002f // below this the primed loop writes zeros
    }

    /** Per-type synthesis profile: cutoff and master gain. */
    private class Profile(val cutoffHz: Float, val masterGain: Float) {
        val lowPassAlpha: Float = 1f - Math.exp((-2.0 * Math.PI * cutoffHz / SAMPLE_RATE)).toFloat()
    }

    private val profilePencil = Profile(cutoffHz = 5500f, masterGain = 1.0f) // 真实采样 + 压感共振调制
    private val profileInk = Profile(cutoffHz = 3200f, masterGain = 1.0f) // SVF 谐振尖锐金属划纸
    private val profileTick = Profile(cutoffHz = 3500f, masterGain = 0.9f)

    // ---- configuration mirrors (written from UI thread, read on worker) ----
    @Volatile private var enabled = false
    @Volatile private var volume = 0.6f
    @Volatile private var profile: Profile = profilePencil
    @Volatile private var isTickType = false

    /**
     * 场景门控: 只有真正需要音效的界面 (绘画页且应用在前台) 才保持管线预热。
     */
    @Volatile private var active = true

    // ---- live stroke state (hot path: plain volatile writes only) ----
    @Volatile private var writing = false
    @Volatile private var pendingTick = false
    @Volatile private var speedGain = 0.4f // raw stroke-speed response 0..1
    @Volatile private var strokePressure = 0.5f // stroke pressure 0..1
    @Volatile private var eraseScale = 1f // 橡皮擦音量略收
    @Volatile private var noiseLevel = 0f // random amplitude flutter state

    private val lock = Object()
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    private var track: AudioTrack? = null
    private val chunkBuf = ShortArray(CHUNK_FRAMES)
    private val tickBuf = ShortArray(TICK_FRAMES)

    // Preloaded real pencil sample loop
    private var pencilSamples: ShortArray? = null
    private var pencilSamplePos = 0

    // Synthesizer state (worker thread only)
    private var currentGain = 0f
    private var lowPassState = 0f
    private var noiseState: Int = 0x1F123BB5
    private var svfLp = 0f
    private var svfBp = 0f
    private var trackPlaying = false

    fun start() {
        if (running.get()) return
        pencilSamples = loadPencilSample()
        try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(CHUNK_FRAMES * 2 * 2)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val builder = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBufBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val newTrack = builder.build()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    newTrack.setBufferSizeInFrames(CHUNK_FRAMES * 2)
                } catch (_: Throwable) {}
            }
            track = newTrack
        } catch (_: Throwable) {
            track = null
        }
        buildTickBuffer()
        running.set(true)
        worker = Thread({
            renderLoop()
        }, "ReveriePaperSound")
        worker?.priority = Thread.NORM_PRIORITY - 1
        worker?.start()
    }

    /**
     * Begin a stroke.
     * Non-blocking, zero allocation. The primed pipeline makes this effectively
     * latency-free: only the gain target changes.
     */
    fun startStroke(isEraser: Boolean, initialPressure: Float = 0.5f) {
        if (!enabled) return
        eraseScale = if (isEraser) 0.65f else 1f
        strokePressure = initialPressure.coerceIn(0.05f, 1f)
        synchronized(lock) {
            if (isTickType) {
                pendingTick = true
            } else {
                if (!writing && currentGain <= SILENCE_GAIN) {
                    val p = strokePressure
                    val pressureGain = 0.35f + 0.65f * p
                    currentGain = 0.35f * speedGain * pressureGain * eraseScale * (volume * volume) * profile.masterGain
                }
                writing = true
            }
            lock.notifyAll()
        }
    }

    /**
     * Update per-move gain from document-space stroke speed (px/ms) and pressure.
     * Non-blocking, zero allocation: volatile writes only.
     */
    fun updateStroke(speedPxPerMs: Float, pressure: Float = 0.5f) {
        if (!enabled || isTickType) return
        strokePressure = pressure.coerceIn(0.05f, 1f)
        // brush_sfx 对数响度响应: log10(9 * speed + 1)
        val normSpeed = (speedPxPerMs / 4.0f).coerceIn(0f, 1f)
        val logSpeed = kotlin.math.log10(9.0f * normSpeed + 1.0f)
        speedGain = when (profile) {
            profileInk -> (0.08f + 0.92f * logSpeed).coerceIn(0.10f, 1f)
            else -> (0.18f + 0.82f * logSpeed).coerceIn(0.20f, 1f)
        }
    }

    /** End the current stroke; the tail decays out, then the loop writes zeros. */
    fun stopStroke() {
        synchronized(lock) {
            writing = false
            lock.notifyAll()
        }
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            writing = false
            pendingTick = false
        }
        synchronized(lock) { lock.notifyAll() }
    }

    fun configure(enabled: Boolean, volume: Float, type: StylusAudioType) {
        this.enabled = enabled
        this.volume = volume.coerceIn(0f, 1f)
        this.profile = when (type) {
            StylusAudioType.INK_PEN -> profileInk
            StylusAudioType.SOFT_TICK -> profileTick
            else -> profilePencil
        }
        this.isTickType = type == StylusAudioType.SOFT_TICK
        lowPassState = 0f
        svfLp = 0f
        svfBp = 0f
        if (!enabled) {
            writing = false
            pendingTick = false
        }
        synchronized(lock) { lock.notifyAll() }
    }

    fun release() {
        running.set(false)
        synchronized(lock) { lock.notifyAll() }
        worker?.interrupt()
        worker = null
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Throwable) {}
        track = null
    }

    // ------------------------------------------------------------------
    //  Worker thread: single owner of the AudioTrack lifecycle & PCM state
    // ------------------------------------------------------------------
    private fun renderLoop() {
        while (running.get()) {
            val t = track
            if (t == null || !enabled || !active) {
                pauseTrack(t)
                writing = false
                pendingTick = false
                currentGain = 0f
                synchronized(lock) {
                    while (running.get() && (!enabled || !active)) {
                        try { lock.wait() } catch (_: InterruptedException) { return }
                    }
                }
                continue
            }
            if (!trackPlaying) {
                try { t.play() } catch (_: Throwable) { }
                trackPlaying = true
            }

            if (pendingTick) {
                pendingTick = false
                writeAll(tickBuf, TICK_FRAMES)
                continue
            }

            if (writing || currentGain > SILENCE_GAIN) {
                renderChunk()
                writeAll(chunkBuf, CHUNK_FRAMES)
            } else {
                currentGain = 0f
                chunkBuf.fill(0)
                writeAll(chunkBuf, CHUNK_FRAMES)
            }
        }
    }

    private fun pauseTrack(t: AudioTrack?) {
        if (trackPlaying) {
            try { t?.pause() } catch (_: Throwable) {}
            trackPlaying = false
        }
    }

    private fun writeAll(buf: ShortArray, frames: Int) {
        val t = track ?: return
        try {
            var written = 0
            while (written < frames && written >= 0 && running.get()) {
                val n = t.write(buf, written, frames - written, AudioTrack.WRITE_BLOCKING)
                if (n <= 0) break
                written += n
            }
        } catch (_: Throwable) {}
    }

    /** Synthesize one 10 ms block (480 samples @ 48 kHz). */
    private fun renderChunk() {
        val prof = profile
        val p = strokePressure
        // 压感对响度与阻尼感的双重调制 (对齐 brush_sfx)
        val pressureGain = 0.35f + 0.65f * p
        val target = if (writing) {
            speedGain * pressureGain * eraseScale * (volume * volume) * prof.masterGain
        } else {
            0f
        }
        val approach = if (target > currentGain) ATTACK else RELEASE
        currentGain += (target - currentGain) * approach
        val g = currentGain

        val pSamples = pencilSamples
        if (prof == profilePencil && pSamples != null && pSamples.isNotEmpty()) {
            // Mode 1: 真实铅笔摩擦采样循环 + 压感动态共振调制
            var pos = pencilSamplePos
            val size = pSamples.size
            var lp = lowPassState
            // 压感与笔速动态调整共振滤波截止频率
            val cutoff = (3200f + 4800f * p + 2000f * speedGain).coerceIn(2000f, 18000f)
            val alpha = (1f - Math.exp((-2.0 * Math.PI * cutoff / SAMPLE_RATE))).toFloat()

            for (i in 0 until CHUNK_FRAMES) {
                val raw = pSamples[pos].toFloat() / 32768.0f
                pos = (pos + 1) % size
                lp += alpha * (raw - lp)
                // 重压时增强中低频共振 (更加饱满沙沙), 轻划保留轻灵高频
                val body = lp * (1.0f + 0.85f * p)
                val high = (raw - lp) * 0.70f
                val s = (body + high) * g * 1.35f
                chunkBuf[i] = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            pencilSamplePos = pos
            lowPassState = lp
        } else if (prof == profileInk) {
            // Mode 2: SVF 状态变量滤波器谐振 (清脆金属笔尖划纸质感)
            val centerFreq = (1200f + 950f * (speedGain * speedGain) + 350f * p).coerceIn(800f, 5000f)
            val f = (2.0 * kotlin.math.sin(Math.PI * centerFreq / SAMPLE_RATE)).toFloat()
            val damping = 0.55f // 谐振锐度

            var lp = svfLp
            var bp = svfBp
            var nState = noiseState

            for (i in 0 until CHUNK_FRAMES) {
                var x = nState
                x = x xor (x shl 13)
                x = x xor (x ushr 17)
                x = x xor (x shl 5)
                nState = x
                val white = (x / 2147483648.0).toFloat()

                val hp = white - lp - damping * bp
                bp += f * hp
                lp += f * bp

                val s = (bp * 2.2f + lp * 0.35f) * g
                chunkBuf[i] = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            svfLp = lp
            svfBp = bp
            noiseState = nState
        } else {
            // Mode 3: 算法平滑白噪声降级回退
            val alpha = prof.lowPassAlpha
            var lp = lowPassState
            var nState = noiseState
            var flutter = noiseLevel

            for (i in 0 until CHUNK_FRAMES) {
                var x = nState
                x = x xor (x shl 13)
                x = x xor (x ushr 17)
                x = x xor (x shl 5)
                nState = x
                val white = (x / 2147483648.0).toFloat()
                lp += alpha * (white - lp)

                flutter += (0.85f + (x shr 24) * (0.30f / 128f) - flutter) * 0.06f
                var s = lp * flutter * g * (1.6f - prof.lowPassAlpha * 0.5f)
                chunkBuf[i] = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            noiseState = nState
            lowPassState = lp
            noiseLevel = flutter
        }
    }

    /** Precompute the soft-touch tick: 1750 Hz sine with exponential decay @ 48 kHz. */
    private fun buildTickBuffer() {
        val freq = 1750.0
        val tau = 0.009 // 9 ms decay constant
        for (i in 0 until TICK_FRAMES) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = Math.exp(-t / tau)
            val s = Math.sin(2.0 * Math.PI * freq * t) * env * 0.5
            tickBuf[i] = (s * 32767.0).toInt().toShort()
        }
    }

    private fun loadPencilSample(): ShortArray? {
        val ctx = context ?: return null
        return try {
            ctx.assets.open("sfx/pencil_friction.wav").use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 44) {
                    val count = (bytes.size - 44) / 2
                    val shorts = ShortArray(count)
                    var bi = 44
                    for (i in 0 until count) {
                        val lo = bytes[bi++].toInt() and 0xFF
                        val hi = bytes[bi++].toInt()
                        shorts[i] = ((hi shl 8) or lo).toShort()
                    }
                    shorts
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
