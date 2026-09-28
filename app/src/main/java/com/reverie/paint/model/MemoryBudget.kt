/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

/**
 * 重内存路径的**纯逻辑预算计算**(无 Android 依赖, 可单测)。
 *
 * 存在的理由: 液化"GPU 场一次性落盘"与"高清图片导入"都会在瞬时同时持有**多份**
 * 同尺寸大缓冲(引擎源像素 / Java 源数组 / GL 源纹理 / 16F 位移场 / 抬笔回读),
 * 真机高压测试下崩溃几乎都发生在这些峰值上。把"能开多大"的判定收敛成一处纯函数:
 *
 *  - 调用方(```CanvasTouchView``` / ```ImageImportHelper```)只负责提供堆上限与设备分级;
 *  - 阈值在此按"保守字节/像素"估算, 宁可让大画布回退到流式经典路径, 也不冒险逼近 OOM;
 *  - 纯计算 ⇒ 可以被 JVM 单测穷举边界(见 ```MemoryBudgetTest```)。
 *
 * 注意: 这里的所有数字都是**稳定性下限**, 不是性能最优值。放宽任何一项前请先跑
 * ```docs/LIQUIFY-STABILITY.md``` 里的高压回归。
 */
object MemoryBudget {

    /**
     * 场通路每文档像素的保守内存开销(字节)。
     *
     * 拆解(4M px 文档 → 约 128MB 常驻/瞬时峰值, 与真机观测一致):
     *  引擎源 device 4 + 引擎 RGBA 裁剪 4 + Java 源数组 4 + GL 源纹理 4
     *  + RGBA16F 位移场 8 + 抬笔回读(Java 数组 + Direct + 纹理) 约 12 → 取上界 32。
     */
    const val FIELD_BYTES_PER_PX = 32L

    /** 场通路像素上限(与历史行为一致): 4M px。 */
    const val FIELD_MAX_PX = 4L * 1024 * 1024

    /** 低内存设备(ActivityManager.isLowRamDevice)的场通路上限。 */
    const val FIELD_MAX_PX_LOW_RAM = 1L * 1024 * 1024

    /** 抬笔回读单次允许的最大字节数(一次要同时存在 Java 数组 + Direct 缓冲 + FBO 纹理)。 */
    const val COMMIT_MAX_BYTES = 20L * 1024 * 1024

    /** 解码位图允许占用的堆预算系数: 堆上限 / [DECODE_HEAP_DIVISOR]。 */
    const val DECODE_HEAP_DIVISOR = 4L

    /** 解码预算硬上限(堆再大也不让单张图吃满, 留出液化/撤销/投影的余量)。 */
    const val DECODE_MAX_BYTES = 192L * 1024 * 1024

    /** 解码采样率上限(2 的幂; 64 = 最长边压到 1/64)。 */
    const val MAX_SAMPLE = 64

    /**
     * 场通路(整篇文档源 + 位移场 + 回读)允许的文档像素上限。
     *
     * @param maxHeapBytes ```Runtime.getRuntime().maxMemory()```; <= 0 表示未知(只按设备分级)。
     * @param lowRam      ```ActivityManager.isLowRamDevice```。
     * @return 像素上限; 0 表示"这台设备上不可用" ⇒ 调用方必须回退经典逐 dab 路径。
     */
    fun fieldPathBudgetPx(maxHeapBytes: Long, lowRam: Boolean): Long {
        val hard = if (lowRam) FIELD_MAX_PX_LOW_RAM else FIELD_MAX_PX
        if (maxHeapBytes <= 0L) return hard
        val fromHeap = maxHeapBytes / FIELD_BYTES_PER_PX
        return minOf(hard, fromHeap).coerceAtLeast(0L)
    }

    /**
     * 抬笔回读矩形是否在单次预算内(按整块 `w * h * 4` 字节判断, 含溢出保护)。
     *
     * @return false = 调用方应回退"重放补点"的流式收口, 而不是分配这块内存。
     */
    fun commitBytesWithinBudget(w: Int, h: Int, budgetBytes: Long = COMMIT_MAX_BYTES): Boolean {
        if (w <= 0 || h <= 0 || budgetBytes <= 0L) return false
        val bytes = w.toLong() * h.toLong() * 4L
        return bytes in 1..budgetBytes
    }

    /**
     * 单张解码位图允许占用的字节预算(ARGB_8888)。
     *
     * @param maxHeapBytes 堆上限; <= 0 表示未知 ⇒ 返回 [DECODE_MAX_BYTES]。
     */
    fun decodeBudgetBytes(maxHeapBytes: Long): Long {
        if (maxHeapBytes <= 0L) return DECODE_MAX_BYTES
        return (maxHeapBytes / DECODE_HEAP_DIVISOR).coerceIn(4L * 1024 * 1024, DECODE_MAX_BYTES)
    }

    /**
     * 采样率(2 的幂), 同时满足:
     *  1. 解码后最长边 ≤ [dimensionLimit](画布尺寸硬上限);
     *  2. 解码后像素字节 ≤ [decodeBudgetBytes] (堆安全预算)。
     *
     * 任一条件不满足就加倍采样率, 直到 [MAX_SAMPLE] 仍不满足则返回 [MAX_SAMPLE]
     * (由调用方决定是继续尝试还是放弃 —— 本函数保证不会算出"必然 OOM"的采样率)。
     */
    fun sampleSize(w: Int, h: Int, dimensionLimit: Int, maxHeapBytes: Long): Int {
        if (w <= 0 || h <= 0) return 1
        val budget = decodeBudgetBytes(maxHeapBytes)
        val dim = if (dimensionLimit <= 0) Int.MAX_VALUE else dimensionLimit
        var sample = 1
        while (sample < MAX_SAMPLE) {
            val sw = w / sample
            val sh = h / sample
            if (sw <= 0 || sh <= 0) return sample
            val dimOk = sw <= dim && sh <= dim
            val memOk = sw.toLong() * sh.toLong() * 4L <= budget
            if (dimOk && memOk) break
            sample *= 2
        }
        return sample
    }
}
