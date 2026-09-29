/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * M2 回归: 双指轻点(撤销)与旋转/捏合运动的判定。
 *
 * 对应实现: CanvasTouchView 中 `isPinchMotion` 的旋转判据改为
 * TwoFingerGesturePolicy.rotationRadiusPx / rotationArcPx / isMotion。
 * 覆盖: 小间距双指旋转不再被轻点撤销吞掉、轻点抖动仍可撤销、判据只更灵敏不更迟钝、
 * 大指距与旧公式等价、判定为旋转后 90° 吸附收敛能落到精确倍数。
 */
class TwoFingerGesturePolicyTest {

    private val density = 2.75f

    /** 与 CanvasTouchView 中一致的容差(px) */
    private val arcTolerance = 3f * density
    private val moveTolerance = 6f * density
    private val spreadTolerance = 3f * density

    /** 参考半径下限(px) */
    private val floorRadius = TwoFingerGesturePolicy.MIN_ROTATION_RADIUS_DP * density

    /** 旧公式: 弧长 = 角差 × 指距 × 0.5 */
    private fun legacyArcPx(spacingPx: Float, angleDiffDeg: Float): Float =
        Math.toRadians(angleDiffDeg.toDouble()).toFloat() * (spacingPx * 0.5f)

    private fun motion(
        spacingPx: Float,
        angleDiffDeg: Float,
        centroidMovedPx: Float = 0f,
        distanceChangePx: Float = 0f,
        fingerCount: Int = 2,
    ): Boolean = TwoFingerGesturePolicy.isMotion(
        centroidMovedPx = centroidMovedPx,
        spreadMovedPx = distanceChangePx * 0.5f,
        rotationArcPx = TwoFingerGesturePolicy.rotationArcPx(
            angleDiffDeg,
            TwoFingerGesturePolicy.rotationRadiusPx(spacingPx, density, fingerCount),
        ),
        moveTolerancePx = moveTolerance,
        spreadTolerancePx = spreadTolerance,
        arcTolerancePx = arcTolerance,
    )

    @Test
    fun `close fingers rotation is no longer swallowed by two-finger tap`() {
        val spacingPx = 40f
        val angleDeg = 20f

        // 旧公式: 半径仅 20px, 20° 只折算 7.0px, 低于 8.25px 容差 → 被当成轻点(撤销 + 不收敛)
        val legacy = legacyArcPx(spacingPx, angleDeg)
        assertTrue("旧公式确实低于容差 ($legacy)", legacy < arcTolerance)

        // 修复后: 半径取参考下限, 同一角度被正确识别为旋转
        assertTrue("小间距旋转必须判为运动", motion(spacingPx, angleDeg))
    }

    @Test
    fun `rotation just above the tolerance counts as motion`() {
        val tolDeg = Math.toDegrees((arcTolerance / floorRadius).toDouble()).toFloat()
        assertTrue(motion(40f, tolDeg * 1.1f))
        assertTrue(motion(40f, tolDeg * 2f))
    }

    @Test
    fun `sloppy two-finger tap still counts as tap so undo keeps working`() {
        val tolDeg = Math.toDegrees((arcTolerance / floorRadius).toDouble()).toFloat()
        // 手指并拢的轻点: 手部抖动带来的角差、质心位移与指距变化都在容差内
        assertFalse(
            "容差内的抖动必须仍按轻点处理(否则双指撤销会失效)",
            motion(
                spacingPx = 40f,
                angleDiffDeg = tolDeg * 0.5f,
                centroidMovedPx = moveTolerance * 0.5f,
                distanceChangePx = spreadTolerance,
            ),
        )
    }

    @Test
    fun `gravity of the rotation tolerance stays within a usable band`() {
        val tolDeg = Math.toDegrees((arcTolerance / floorRadius).toDouble()).toFloat()
        // 上限保证小间距旋转能被及时识别为运动; 下限保证双指轻点仍有可用的抖动余量
        assertTrue("tolerance too large: $tolDeg", tolDeg <= 3f)
        assertTrue("tolerance too small: $tolDeg", tolDeg >= 1f)
    }

    @Test
    fun `sensitivity never decreases compared with the legacy formula`() {
        listOf(20f, 40f, 80f, 120f, 240f, 400f, 800f).forEach { spacing ->
            val newRadius = TwoFingerGesturePolicy.rotationRadiusPx(spacing, density)
            val legacyRadius = spacing * 0.5f
            assertTrue(
                "spacing=$spacing 时等效半径不得小于旧公式 ($newRadius vs $legacyRadius)",
                newRadius >= legacyRadius,
            )
            // 半径不减小 ⇒ 同一角差折算的弧长不减小 ⇒ 判据只会更灵敏
            val angleDeg = 3f
            assertTrue(
                TwoFingerGesturePolicy.rotationArcPx(angleDeg, newRadius) >=
                    TwoFingerGesturePolicy.rotationArcPx(angleDeg, legacyRadius),
            )
        }
    }

    @Test
    fun `wide finger spacing behaves exactly like the legacy formula`() {
        val spacingPx = 400f
        val newRadius = TwoFingerGesturePolicy.rotationRadiusPx(spacingPx, density)
        assertEquals("大指距下与旧公式完全等价", spacingPx * 0.5f, newRadius, 1e-4f)

        listOf(1f, 2f, 3f, 5f).forEach { angleDeg ->
            assertEquals(
                "angle=$angleDeg 时结论必须与旧公式一致",
                legacyArcPx(spacingPx, angleDeg) > arcTolerance,
                motion(spacingPx, angleDeg),
            )
        }
    }

    @Test
    fun `existing move and spread criteria are preserved`() {
        // 纯位移超容差 → 运动 (不依赖旋转判据)
        assertTrue(motion(200f, 0f, centroidMovedPx = moveTolerance * 1.5f))
        // 纯指距变化超容差 → 运动
        assertTrue(motion(200f, 0f, distanceChangePx = spreadTolerance * 3f))
        // 全部在容差内 → 轻点
        assertFalse(motion(200f, 0f, centroidMovedPx = moveTolerance * 0.5f, distanceChangePx = spreadTolerance))
    }

    @Test
    fun `three or more fingers keep the legacy rotation sensitivity`() {
        // 三指及以上 (轻点重做 / 三指手势) 必须与改动前的判据逐点等价
        listOf(20f, 40f, 60f, 120f, 400f).forEach { spacing ->
            listOf(3, 4, 5).forEach { fingers ->
                assertEquals(
                    "spacing=$spacing fingers=$fingers 必须沿用旧公式",
                    spacing * 0.5f,
                    TwoFingerGesturePolicy.rotationRadiusPx(spacing, density, fingers),
                    1e-4f,
                )
            }
        }
    }

    @Test
    fun `close three-finger tap jitter stays a tap so redo is not blocked`() {
        val spacingPx = 60f
        val jitterDeg = 5f

        // 三指并拢轻点: 手部抖动带来的角差在旧公式容差内 → 仍是轻点 → 三指重做可触发
        assertFalse(
            "三指轻点抖动不得被判成运动(否则三指重做失效)",
            motion(
                spacingPx = spacingPx,
                angleDiffDeg = jitterDeg,
                centroidMovedPx = moveTolerance * 0.2f,
                distanceChangePx = spreadTolerance * 0.3f,
                fingerCount = 3,
            ),
        )

        // 同一输入若误用双指的半径下限, 就会被判成运动 —— 这正是把下限限制在双指的原因
        assertTrue(
            motion(
                spacingPx = spacingPx,
                angleDiffDeg = jitterDeg,
                centroidMovedPx = moveTolerance * 0.2f,
                distanceChangePx = spreadTolerance * 0.3f,
                fingerCount = 2,
            ),
        )
    }

    @Test
    fun `two finger radius floor does not leak into the three finger path`() {
        val spacingPx = 40f
        assertEquals(floorRadius, TwoFingerGesturePolicy.rotationRadiusPx(spacingPx, density, 2), 1e-4f)
        assertEquals(spacingPx * 0.5f, TwoFingerGesturePolicy.rotationRadiusPx(spacingPx, density, 3), 1e-4f)
    }

    @Test
    fun `default finger count keeps the previous two finger behaviour`() {
        listOf(20f, 40f, 200f).forEach { spacing ->
            assertEquals(
                TwoFingerGesturePolicy.rotationRadiusPx(spacing, density, 2),
                TwoFingerGesturePolicy.rotationRadiusPx(spacing, density),
                0f,
            )
        }
    }

    @Test
    fun `degenerate inputs stay finite and safe`() {
        // NaN 指距回退到参考下限, 不把 NaN 传播进判据
        assertEquals(floorRadius, TwoFingerGesturePolicy.rotationRadiusPx(Float.NaN, density), 1e-4f)
        // 半径为 0 时弧长为 0, 不会出现除零或无穷
        assertEquals(0f, TwoFingerGesturePolicy.rotationArcPx(30f, 0f), 0f)
        // 半径为负时同样按 0 处理
        assertEquals(0f, TwoFingerGesturePolicy.rotationArcPx(30f, -10f), 0f)
    }

    @Test
    fun `rotation recognized as motion reaches an exact quarter turn via snap settle`() {
        val spacingPx = 40f
        val angleSpanDeg = 5f
        val rawRotation = 87f
        val threshold = 5f

        // 1) 小间距旋转超过容差 → 判为运动 → 不触发双指轻点撤销 (settle 分支不再被短路)
        assertTrue(motion(spacingPx, angleSpanDeg))

        // 2) 落在吸附区内 → settle 分支的条件成立
        assertTrue(RotationSnap.isWithinThreshold(rawRotation, threshold))

        // 3) 收敛目标为最近的 90° 倍数
        val target = RotationSnap.nearestMultiple(rawRotation)
        assertEquals(90f, target, 1e-4f)

        // 4) 手势中的生效角已被磁性曲线拉向倍数, settle 后精确落位
        val appliedDuringGesture = RotationSnap.apply(rawRotation, threshold)
        assertTrue("吸附区内的生效角应被拉向 90°", appliedDuringGesture > rawRotation && appliedDuringGesture < target)
        val settled = appliedDuringGesture + RotationSnap.shortestDelta(appliedDuringGesture, target)
        assertEquals("收敛后必须精确落在 90° 倍数", 90f, settled, 1e-4f)
        assertTrue(abs(settled - target) < 1e-4f)
    }

    @Test
    fun `strict two finger rotation is inert once a third finger is present`() {
        val spacingPx = 60f
        val angleDeg = 5f

        // 双指: 加了下限的判据成立 → 抑制双指轻点撤销
        assertTrue(
            TwoFingerGesturePolicy.isStrictTwoFingerRotation(
                angleDiffDegrees = angleDeg,
                spacingPx = spacingPx,
                density = density,
                arcTolerancePx = arcTolerance,
                fingerCount = 2,
            ),
        )

        // 三指/四指: 内部退回旧公式 → 同一输入不成立, 与改动前一致 (三指重做不被误挡)
        assertFalse(
            TwoFingerGesturePolicy.isStrictTwoFingerRotation(
                angleDiffDegrees = angleDeg,
                spacingPx = spacingPx,
                density = density,
                arcTolerancePx = arcTolerance,
                fingerCount = 3,
            ),
        )
        assertFalse(
            TwoFingerGesturePolicy.isStrictTwoFingerRotation(
                angleDiffDegrees = angleDeg,
                spacingPx = spacingPx,
                density = density,
                arcTolerancePx = arcTolerance,
                fingerCount = 4,
            ),
        )
    }

    @Test
    fun `non finite angle does not leak into the rotation criterion`() {
        // NaN 若不截断, `>` 比较恒为 false 会把手势静默降级成轻点, 这里要求显式归零
        assertEquals(0f, TwoFingerGesturePolicy.rotationArcPx(Float.NaN, floorRadius), 0f)
        assertEquals(0f, TwoFingerGesturePolicy.rotationArcPx(30f, Float.NaN), 0f)
        assertEquals(0f, TwoFingerGesturePolicy.rotationArcPx(Float.POSITIVE_INFINITY, floorRadius), 0f)

        assertFalse(
            TwoFingerGesturePolicy.isStrictTwoFingerRotation(
                angleDiffDegrees = Float.NaN,
                spacingPx = 40f,
                density = density,
                arcTolerancePx = arcTolerance,
            ),
        )
    }
}
