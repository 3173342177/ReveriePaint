/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * 液化"两条渲染路径同一套数学"的守门测试(画质一致性的可量化部分)。
 *
 * 背景: 同一个 dab 的形变幅度在**引擎侧**(`applyLiquifyDab`, C++)与**场预览侧**
 * ([`LiquifyPath.fieldDabGain`], Kotlin + Shader)必须逐项一致 —— 否则拖动期看到的形变
 * 与抬笔后落盘的形变不一致(真机表现: 抬笔瞬间"跳一下"), 也会让"预览 = 所见即所得"失效。
 *
 * 这里把**引擎侧的公式**作为参考实现写在测试里(来自 `ReverieCoreMiscTools.cpp` 的
 * `applyLiquifyDab`): 任何一侧单独改动都会被这里挡下来。
 *
 * 引擎侧参考口径:
 *  - 推拉(mode 0): 位移 = (target - from) × 强度, 幅度曲线**不参与**;
 *  - 膨胀/收缩(1/2): 0.35 × 幅度;
 *  - 顺时针/逆时针(3/4): 0.6 × 幅度;
 *  - 幅度 = 0.2 + 0.8 × min(1, dist / size), size = max(8, brushSize)。
 */
class LiquifyModeConsistencyTest {

    private val push = LiquifyPath.MODE_PUSH
    private val inflate = LiquifyPath.MODE_INFLATE
    private val shrink = LiquifyPath.MODE_SHRINK
    private val cw = LiquifyPath.MODE_TWIRL_CW
    private val ccw = LiquifyPath.MODE_TWIRL_CCW

    /** 引擎侧幅度曲线(参考实现)。 */
    private fun engineAmplitude(distance: Float, brushSize: Float): Float {
        val size = maxOf(LiquifyPath.MIN_BRUSH_SIZE, brushSize)
        return 0.2f + 0.8f * min(1f, distance / size)
    }

    /** 引擎侧"一次 dab 的幅度增益"(参考实现, 与 applyLiquifyDab 的系数逐项对齐)。 */
    private fun engineGain(mode: Int, distance: Float, brushSize: Float): Float = when (mode) {
        inflate, shrink -> 0.35f * engineAmplitude(distance, brushSize)
        cw, ccw -> 0.6f * engineAmplitude(distance, brushSize)
        else -> 1f
    }

    @Test
    fun `push gain is distance independent`() {
        for (d in listOf(0f, 1f, 30f, 500f)) {
            assertEquals(1f, LiquifyPath.fieldDabGain(push, d, 60f), 1e-6f)
        }
    }

    @Test
    fun `field gain matches engine formula for all modes`() {
        val sizes = listOf(8f, 20f, 60f, 200f, 400f)
        for (size in sizes) {
            for (d in listOf(0f, 0.5f, size * 0.3f, size, size * 3f)) {
                for (mode in listOf(push, inflate, shrink, cw, ccw)) {
                    assertEquals(
                        "mode=$mode size=$size dist=$d",
                        engineGain(mode, d, size),
                        LiquifyPath.fieldDabGain(mode, d, size),
                        1e-5f,
                    )
                }
            }
        }
    }

    @Test
    fun `amplitude keeps a fixed floor at zero distance`() {
        // 引擎在"没动"时仍有 0.2 的幅度(按住不动也会持续形变) —— 两条路径都必须保留这个底,
        // 否则"按住不放"的手感在预览与提交之间会不一致(PS/CSP 的按住行为也是持续形变)
        assertEquals(0.35f * 0.2f, LiquifyPath.fieldDabGain(inflate, 0f, 60f), 1e-6f)
        assertEquals(0.6f * 0.2f, LiquifyPath.fieldDabGain(cw, 0f, 60f), 1e-6f)
    }

    @Test
    fun `substep scaling preserves total deformation`() {
        // 细分前后总形变量守恒: n × amplitude(d/n) × scale == amplitude(d)
        // (不守恒的话, 快拖就会被细分成"过度形变", 慢拖则相反 —— 手感随速度漂移)
        val sizes = listOf(12f, 60f, 200f)
        val distances = listOf(20f, 90f, 400f)
        for (size in sizes) {
            for (d in distances) {
                val n = LiquifyPath.substepCount(d, size)
                assertEquals(
                    "substeps=$n size=$size dist=$d",
                    engineAmplitude(d, size),
                    n * engineAmplitude(d / n, size) *
                        LiquifyPath.substepStrengthScale(d, size, n, inflate),
                    1e-3f,
                )
            }
        }
    }

    @Test
    fun `push substep scaling is exactly one`() {
        for (n in 1..LiquifyPath.MAX_SUBSTEPS) {
            assertEquals(1f, LiquifyPath.substepStrengthScale(300f, 60f, n, push), 1e-6f)
        }
    }

    @Test
    fun `substep spacing never exceeds a third of the field radius`() {
        // 断触保护(强化版): 相邻补点间距必须 ≤ 影响半径的 1/3 —— 这才保证相邻核的
        // 重叠度 ≥ 2/3(test16 的"串珠状断触"正是重叠不足的表现)。旧口径只要求"小于半径"
        // (重叠 ≥ 0), 太松, 挡不住断触回归。
        for (size in listOf(8f, 16f, 60f, 200f, 400f)) {
            val step = maxOf(LiquifyPath.MIN_STEP, size * LiquifyPath.STEP_RATIO)
            val radius = LiquifyPath.fieldDabRadius(size)
            assertTrue(
                "size=$size step=$step radius=$radius",
                step <= radius / 3f,
            )
        }
    }

    @Test
    fun `long fast drags are not truncated into gaps`() {
        // 快速长距离拖动(单帧 500~1500px; 60fps 下相当于 9 万 px/s, 已远超人类输入范围)
        // 不允许出现"步长远大于半径"的空档: MAX_SUBSTEPS 截断后有效步长仍必须 ≤ 半径/3
        // (否则轨迹上会重新出现分离核 = 断触)。
        for (size in listOf(20f, 60f, 200f)) {
            for (distance in listOf(500f, 1000f, 1500f)) {
                val steps = LiquifyPath.substepCount(distance, size)
                val effectiveStep = distance / steps
                val radius = LiquifyPath.fieldDabRadius(size)
                assertTrue(
                    "size=$size dist=$distance steps=$steps step=$effectiveStep radius=$radius",
                    effectiveStep <= radius / 3f,
                )
            }
        }
    }

    /** 场累加 shader 的**线段核**参考实现(与 LiquifyGlesOverlay.DAB_FS 同步维护)。 */
    private fun segmentKernelT(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        radius: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val len = kotlin.math.hypot(dx, dy)
        val sx = if (len > 1e-4f) dx / len else 0f
        val sy = if (len > 1e-4f) dy / len else 0f
        val along = if (len > 1e-4f) {
            (((px - ax) * sx + (py - ay) * sy) / len).coerceIn(0f, 1f)
        } else {
            0f
        }
        val cx = ax + sx * (along * len)
        val cy = ay + sy * (along * len)
        val r = kotlin.math.hypot(px - cx, py - cy)
        if (r >= radius) return 0f
        val x = (r / radius).coerceIn(0f, 1f)
        // smoothstep(0,1,x) = x*x*(3-2x)
        val ss = x * x * (3f - 2f * x)
        return 1f - ss
    }

    @Test
    fun `segment kernel has no gaps along the drag path`() {
        // 直接对应 test16 的断触: 沿段的中线上采样, 核的衰减值必须**处处为 1**(完整覆盖),
        // 且不随采样点位置变化 —— 这就是"线段核从根上消除核间空档"的数学表述。
        val ax = 100f
        val ay = 200f
        val bx = 900f
        val by = 260f
        val radius = LiquifyPath.fieldDabRadius(60f)
        var t = 0f
        while (t <= 1.0001f) {
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            val k = segmentKernelT(px, py, ax, ay, bx, by, radius)
            assertEquals("t=$t along path", 1f, k, 1e-3f)
            t += 0.02f
        }
    }

    @Test
    fun `segment kernel stays continuous off the path`() {
        // 垂直偏移方向: 核值必须单调不增、且在半径处平滑降到 0(无台阶/无环纹突变)
        val (ax, ay, bx, by) = floatArrayOf(0f, 0f, 600f, 0f)
        val radius = LiquifyPath.fieldDabRadius(60f)
        var prev = segmentKernelT(300f, 0f, ax, ay, bx, by, radius)
        var offset = 1f
        while (offset <= radius) {
            val k = segmentKernelT(300f, offset, ax, ay, bx, by, radius)
            assertTrue("offset=$offset prev=$prev k=$k", k <= prev + 1e-4f)
            prev = k
            offset += 2f
        }
        assertEquals(0f, segmentKernelT(300f, radius + 1f, ax, ay, bx, by, radius), 1e-6f)
        // 段端点的连续性: 越过端点后是"点核"的衰减(不会突然归零)
        assertTrue(segmentKernelT(bx + radius * 0.5f, by, ax, ay, bx, by, radius) > 0f)
    }

    @Test
    fun `dab packing matches the JNI layout`() {
        // Kotlin↔native 的**批量布局契约**: (fx, fy, tx, ty, strength, mode), 步长 = DAB_STRIDE。
        // 断言按常量推导(不硬编码 6): 布局一改, 这里立刻失败 —— 这正是 JNI 越界/错位的第一道防线。
        val inputs = listOf(
            floatArrayOf(1f, 2f, 3f, 4f, 0.9f, 3f),
            floatArrayOf(5f, 6f, 7f, 8f, 0.5f, 0f),
            floatArrayOf(-9f, -10f, -11f, -12f, 1f, 4f),
        )
        val buf = FloatArray(inputs.size * LiquifyPath.DAB_STRIDE)
        var n = 0
        for (d in inputs) {
            n = LiquifyPath.packDab(buf, n, d[0], d[1], d[2], d[3], d[4], d[5].toInt())
        }
        assertEquals(inputs.size, n)
        for (i in 0 until inputs.size) {
            val b = i * LiquifyPath.DAB_STRIDE
            val d = inputs[i]
            for (k in 0 until LiquifyPath.DAB_STRIDE) {
                assertEquals("dab $i lane $k", d[k], buf[b + k], 0f)
            }
        }
        // 调用方按 count * DAB_STRIDE 扩容即可容纳(扩容契约)
        assertEquals(0, buf.size % LiquifyPath.DAB_STRIDE)
    }

    @Test
    fun `extreme inputs stay finite and bounded`() {
        val huge = 1e9f
        assertTrue(LiquifyPath.fieldDabGain(inflate, huge, 60f).isFinite())
        assertEquals(0.35f, LiquifyPath.fieldDabGain(inflate, huge, 60f), 1e-5f)
        // 极小/负尺寸都被夹到引擎下限, 不产生 0/负数
        assertTrue(LiquifyPath.fieldDabRadius(0f) >= LiquifyPath.MIN_BRUSH_SIZE)
        assertEquals(0, LiquifyPath.substepCount(0f, 60f))
        assertEquals(1, LiquifyPath.substepCount(0.001f, 60f))
    }
}
