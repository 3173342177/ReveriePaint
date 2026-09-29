/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.reverie.paint.model

import kotlin.math.abs
import kotlin.math.hypot

/** Tracks three fixed pointer IDs. The host owns event delivery and consumes until all fingers lift. */
class ThreeFingerSwipe {
    private val startX = FloatArray(3)
    private val startY = FloatArray(3)
    private val dx = FloatArray(3)
    private val dy = FloatArray(3)
    private var startedAt = 0L
    private var density = 1f
    var moved = false
        private set
    var rejected = false
        private set
    var swiped = false
        private set

    fun begin(time: Long, density: Float) {
        startedAt = time
        this.density = density.coerceAtLeast(0.1f)
        moved = false
        rejected = false
        swiped = false
        dx.fill(0f)
        dy.fill(0f)
    }

    fun setStart(pointer: Int, x: Float, y: Float) {
        startX[pointer] = x
        startY[pointer] = y
    }

    fun update(pointer: Int, x: Float, y: Float) {
        dx[pointer] = x - startX[pointer]
        dy[pointer] = y - startY[pointer]
        if (hypot(dx[pointer], dy[pointer]) > 8f * density) moved = true
    }

    fun evaluate(time: Long) {
        if (rejected || swiped) return
        if (time - startedAt > 650L || time < startedAt) {
            reject()
            return
        }
        for (i in 0..2) {
            if (dy[i] < -16f * density || abs(dx[i]) > 40f * density) {
                reject()
                return
            }
        }
        // Validate every pointer before an unfinished finger can defer recognition.
        for (i in 0..2) {
            if (dy[i] < 48f * density || abs(dx[i]) > dy[i] * 0.6f) return
        }
        // A pinch/rotation or one moving finger must not become a menu swipe.
        if (dy.max() - dy.min() <= 32f * density && dx.max() - dx.min() <= 24f * density) {
            swiped = true
        }
    }

    fun reject() {
        rejected = true
        swiped = false
    }

    fun isTap(time: Long): Boolean = !rejected && !moved && time - startedAt in 0L..379L
}
