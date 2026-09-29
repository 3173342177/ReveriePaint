/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

/**
 * 双指手势"是轻点还是运动"的判定策略 (纯逻辑, 无 Android 依赖, 可 JVM 单测)。
 *
 * 画布上双指轻点 = 撤销 (Procreate 语义)。判定长期采用**像素弧长**而非角度, 因为
 * 屏幕像素才是用户真实操作的量纲; 但旧写法把弧长取为 `角差 × 指距 × 0.5`, 于是
 * 指距越近, 判据越失敏 —— 两指并拢时 (间距 40px) 可旋转 20° 以上仍被判成轻点,
 * 双指撤销会吞掉旋转手势 (既多触发一次撤销, 又让旋转结束后的 90° 吸附收敛不执行)。
 *
 * 这里改为给旋转判据一个**参考半径下限**: 等效半径 = max(指距 / 2, [MIN_ROTATION_RADIUS_DP] × density)。
 *  - 只可能比旧写法**更灵敏**, 绝不会更迟钝 (因此不会掩盖真实的旋转/捏合运动);
 *  - 指距 ≥ 2 × [MIN_ROTATION_RADIUS_DP] 时与旧写法完全等价, 常见张开指距手感不变;
 *  - 指距很小时不再随指距无限失敏: 旋转判据的等效角分辨率上限约 2.9° (取 3dp 弧长容差
 *    与 60dp 下限半径折算), 指距越大越灵敏, 因此并拢手指的旋转不会再被当作轻点。
 *
 * 三个判据 (累计位移 / 指距变化 / 旋转弧长) 保持同一像素量纲, 任一超容差即视为运动。
 */
object TwoFingerGesturePolicy {

    /**
     * 双指旋转判据的参考半径下限 (dp)。取 60dp: 指距 ≥ 120dp 时与旧公式完全等价,
     * 并拢握持时仍能以约 2.9° 的角分辨率识别旋转 (双指轻点的手部抖动通常远小于此)。
     *
     * **仅用于双指场景**: 半径下限解决的是"双指轻点撤销"与"双指旋转"互相冲突的问题;
     * 三指 (轻点重做) 与更多指的判定必须继续沿用旧公式, 否则手指并拢的三指轻点会被
     * 误判成运动而让重做失效 —— 该失效模式在旧代码注释里已被明确记录过。
     */
    const val MIN_ROTATION_RADIUS_DP = 60f

    /**
     * 旋转判据使用的等效半径 (px)。
     *
     * @param fingerCount 当前参与手势的手指数量; >= 3 时返回旧公式 `指距 / 2` (不设下限)
     *
     * 显式比较而非 `maxOf` 是为了对 NaN 保持有限输出 (NaN 场景回退到下限, 不污染后续判据)。
     */
    fun rotationRadiusPx(spacingPx: Float, density: Float, fingerCount: Int = 2): Float {
        val halfSpacing = spacingPx * 0.5f
        if (fingerCount >= 3) return halfSpacing
        val floor = MIN_ROTATION_RADIUS_DP * density
        return if (halfSpacing > floor) halfSpacing else floor
    }

    /**
     * 双指相对角差 (度) 折算为屏幕弧长 (px)。
     *
     * 非有限输入 (NaN/Inf) 一律返回 0: 判定链上任何 NaN 都会让 `>` 比较恒为 false, 从而把
     * 手势静默降级成"轻点", 因此这里主动截断, 不向下游传播。
     */
    fun rotationArcPx(angleDiffDegrees: Float, radiusPx: Float): Float {
        if (radiusPx <= 0f || !radiusPx.isFinite() || !angleDiffDegrees.isFinite()) return 0f
        return Math.toRadians(angleDiffDegrees.toDouble()).toFloat() * radiusPx
    }

    /**
     * 双指场景专用的"旋转已构成运动"判定: 旋转弧长 (带参考半径下限) 超过容差。
     *
     * 该结果只用于抑制**双指**轻点撤销 (`isTwoFingerRotation`), 不参与三指重做的门控;
     * [fingerCount] >= 3 时内部退回旧公式, 返回等价于旧行为的结果 (双保险)。
     */
    fun isStrictTwoFingerRotation(
        angleDiffDegrees: Float,
        spacingPx: Float,
        density: Float,
        arcTolerancePx: Float,
        fingerCount: Int = 2,
    ): Boolean =
        rotationArcPx(angleDiffDegrees, rotationRadiusPx(spacingPx, density, fingerCount)) > arcTolerancePx

    /**
     * 双指手势是否已构成捏合 / 旋转 / 平移运动。
     *
     * 返回 true 表示"不是轻点": 调用方据此跳过双指轻点撤销, 并让旋转结束后的吸附收敛正常执行。
     */
    fun isMotion(
        centroidMovedPx: Float,
        spreadMovedPx: Float,
        rotationArcPx: Float,
        moveTolerancePx: Float,
        spreadTolerancePx: Float,
        arcTolerancePx: Float,
    ): Boolean =
        centroidMovedPx > moveTolerancePx ||
            spreadMovedPx > spreadTolerancePx ||
            rotationArcPx > arcTolerancePx
}
