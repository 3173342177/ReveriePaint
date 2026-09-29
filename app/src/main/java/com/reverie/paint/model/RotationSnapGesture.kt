/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import kotlin.math.abs

/** 手势级吸附状态：净转角判定意图，并在激活时保持画面角度连续。 */
class RotationSnapGesture {
    var rawDegrees = 0f
        private set
    var isActive = false
        private set

    private var appliedDegrees = 0f
    private var signedTravel = 0f
    private var lastThreshold = 0f

    fun begin(rotation: Float) {
        rawDegrees = rotation
        appliedDegrees = rotation
        signedTravel = 0f
        lastThreshold = 0f
        isActive = false
    }

    fun update(delta: Float, threshold: Float): Float {
        signedTravel += delta
        val freeAngle = appliedDegrees + delta
        if (threshold <= 0f) {
            rawDegrees = freeAngle
            appliedDegrees = freeAngle
            isActive = false
            lastThreshold = threshold
            return appliedDegrees
        }

        if (!isActive) {
            // 往返噪声互相抵消；只有离开手势起始角才说明用户确实在旋转。
            isActive = abs(signedTravel) >= ENGAGE_DEGREES
            appliedDegrees = freeAngle
            rawDegrees = if (isActive) rawForApplied(freeAngle, threshold) else freeAngle
        } else if (threshold != lastThreshold) {
            // 设置变化也不能把当前画面硬切到另一条曲线。
            appliedDegrees = freeAngle
            rawDegrees = rawForApplied(freeAngle, threshold)
        } else {
            rawDegrees += delta
            appliedDegrees = RotationSnap.apply(rawDegrees, threshold)
        }
        lastThreshold = threshold
        return appliedDegrees
    }

    /** 单调曲线的逆映射，只在激活或阈值变化时计算，不在逐帧热路径迭代。 */
    private fun rawForApplied(applied: Float, threshold: Float): Float {
        if (!RotationSnap.isWithinThreshold(applied, threshold)) return applied
        val target = RotationSnap.nearestMultiple(applied)
        if (applied == target) return target
        var low = target - threshold
        var high = target + threshold
        repeat(24) {
            val mid = (low + high) * 0.5f
            if (RotationSnap.apply(mid, threshold) < applied) low = mid else high = mid
        }
        return (low + high) * 0.5f
    }

    companion object {
        const val ENGAGE_DEGREES = 0.6f
    }
}
