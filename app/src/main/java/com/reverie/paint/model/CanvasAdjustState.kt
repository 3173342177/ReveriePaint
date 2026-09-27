/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import androidx.annotation.StringRes
import com.reverie.paint.R
import kotlin.math.roundToInt

enum class CanvasAdjustMode {
    CROP_EXPAND,
    RESAMPLE_SCALE,
}

enum class AnchorPosition(val dx: Float, val dy: Float) {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);

    companion object {
        fun computeOffset(
            oldW: Int,
            oldH: Int,
            newW: Int,
            newH: Int,
            anchor: AnchorPosition,
        ): Pair<Int, Int> {
            val deltaW = newW - oldW
            val deltaH = newH - oldH
            val cropX = -kotlin.math.round(deltaW * anchor.dx).toInt()
            val cropY = -kotlin.math.round(deltaH * anchor.dy).toInt()
            return cropX to cropY
        }
    }
}

enum class AspectRatioPreset(
    val ratioW: Float?,
    val ratioH: Float?,
    @get:StringRes val labelRes: Int,
) {
    FREE(null, null, R.string.canvas_ratio_free),
    ORIGINAL(null, null, R.string.canvas_ratio_original),
    RATIO_1_1(1f, 1f, R.string.canvas_ratio_1_1),
    RATIO_4_3(4f, 3f, R.string.canvas_ratio_4_3),
    RATIO_3_4(3f, 4f, R.string.canvas_ratio_3_4),
    RATIO_16_9(16f, 9f, R.string.canvas_ratio_16_9),
    RATIO_9_16(9f, 16f, R.string.canvas_ratio_9_16),
    RATIO_3_2(3f, 2f, R.string.canvas_ratio_3_2),
    RATIO_2_3(2f, 3f, R.string.canvas_ratio_2_3),
    RATIO_A4(210f, 297f, R.string.canvas_ratio_a4);

    fun calculateHeight(width: Int, origW: Int, origH: Int): Int {
        if (this == ORIGINAL) {
            return if (origW > 0) (width.toFloat() * origH / origW).roundToInt() else width
        }
        val rw = ratioW
        val rh = ratioH
        return if (rw != null && rh != null && rw > 0f) {
            (width.toFloat() * rh / rw).roundToInt()
        } else {
            width
        }
    }

    fun calculateWidth(height: Int, origW: Int, origH: Int): Int {
        if (this == ORIGINAL) {
            return if (origH > 0) (height.toFloat() * origW / origH).roundToInt() else height
        }
        val rw = ratioW
        val rh = ratioH
        return if (rw != null && rh != null && rh > 0f) {
            (height.toFloat() * rw / rh).roundToInt()
        } else {
            height
        }
    }
}

enum class ResampleFilter(
    val filterType: Int,
    @get:StringRes val labelRes: Int,
) {
    BICUBIC(0, R.string.canvas_resample_bicubic),
    NEAREST(1, R.string.canvas_resample_nearest),
    BILINEAR(2, R.string.canvas_resample_bilinear),
}

data class CanvasAdjustState(
    val mode: CanvasAdjustMode = CanvasAdjustMode.CROP_EXPAND,
    val origWidth: Int = 1000,
    val origHeight: Int = 1000,
    val targetWidth: Int = 1000,
    val targetHeight: Int = 1000,
    val lockAspectRatio: Boolean = false,
    val selectedPreset: AspectRatioPreset = AspectRatioPreset.FREE,
    val anchor: AnchorPosition = AnchorPosition.CENTER,
    val resampleFilter: ResampleFilter = ResampleFilter.BICUBIC,
    val cropX: Int = 0,
    val cropY: Int = 0,
) {
    fun withTargetDimensions(newW: Int, newH: Int): CanvasAdjustState {
        val clampedW = newW.coerceIn(1, 16384)
        val clampedH = newH.coerceIn(1, 16384)
        val (cx, cy) = AnchorPosition.computeOffset(origWidth, origHeight, clampedW, clampedH, anchor)
        return copy(
            targetWidth = clampedW,
            targetHeight = clampedH,
            cropX = cx,
            cropY = cy,
        )
    }

    fun withTargetWidth(newW: Int): CanvasAdjustState {
        val clampedW = newW.coerceIn(1, 16384)
        val newH = if (lockAspectRatio || selectedPreset != AspectRatioPreset.FREE) {
            if (selectedPreset != AspectRatioPreset.FREE) {
                selectedPreset.calculateHeight(clampedW, origWidth, origHeight)
            } else if (origWidth > 0) {
                (clampedW.toFloat() * targetHeight / targetWidth).roundToInt().coerceIn(1, 16384)
            } else targetHeight
        } else {
            targetHeight
        }
        val (cx, cy) = AnchorPosition.computeOffset(origWidth, origHeight, clampedW, newH, anchor)
        return copy(
            targetWidth = clampedW,
            targetHeight = newH,
            cropX = cx,
            cropY = cy,
        )
    }

    fun withTargetHeight(newH: Int): CanvasAdjustState {
        val clampedH = newH.coerceIn(1, 16384)
        val newW = if (lockAspectRatio || selectedPreset != AspectRatioPreset.FREE) {
            if (selectedPreset != AspectRatioPreset.FREE) {
                selectedPreset.calculateWidth(clampedH, origWidth, origHeight)
            } else if (origHeight > 0) {
                (clampedH.toFloat() * targetWidth / targetHeight).roundToInt().coerceIn(1, 16384)
            } else targetWidth
        } else {
            targetWidth
        }
        val (cx, cy) = AnchorPosition.computeOffset(origWidth, origHeight, newW, clampedH, anchor)
        return copy(
            targetWidth = newW,
            targetHeight = clampedH,
            cropX = cx,
            cropY = cy,
        )
    }

    fun withPreset(preset: AspectRatioPreset): CanvasAdjustState {
        if (preset == AspectRatioPreset.FREE) {
            return copy(selectedPreset = preset)
        }
        val newH = preset.calculateHeight(targetWidth, origWidth, origHeight).coerceIn(1, 16384)
        val (cx, cy) = AnchorPosition.computeOffset(origWidth, origHeight, targetWidth, newH, anchor)
        return copy(
            selectedPreset = preset,
            targetHeight = newH,
            cropX = cx,
            cropY = cy,
            lockAspectRatio = true,
        )
    }

    fun withAnchor(newAnchor: AnchorPosition): CanvasAdjustState {
        val (cx, cy) = AnchorPosition.computeOffset(origWidth, origHeight, targetWidth, targetHeight, newAnchor)
        return copy(anchor = newAnchor, cropX = cx, cropY = cy)
    }

    fun withDirectCrop(x: Int, y: Int, w: Int, h: Int): CanvasAdjustState {
        val clampedW = w.coerceIn(1, 16384)
        val clampedH = h.coerceIn(1, 16384)
        return copy(
            cropX = x,
            cropY = y,
            targetWidth = clampedW,
            targetHeight = clampedH,
            selectedPreset = AspectRatioPreset.FREE,
        )
    }

    fun reset(): CanvasAdjustState {
        return CanvasAdjustState(
            mode = mode,
            origWidth = origWidth,
            origHeight = origHeight,
            targetWidth = origWidth,
            targetHeight = origHeight,
            lockAspectRatio = mode == CanvasAdjustMode.RESAMPLE_SCALE,
            selectedPreset = AspectRatioPreset.FREE,
            anchor = AnchorPosition.CENTER,
            resampleFilter = resampleFilter,
            cropX = 0,
            cropY = 0,
        )
    }
}
