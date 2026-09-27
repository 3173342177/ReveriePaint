/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasAdjustStateTest {

    @Test
    fun `anchor offsets calculated correctly for canvas expansion`() {
        val oldW = 1000
        val oldH = 1000
        val newW = 1200
        val newH = 1200

        // Center expansion (50% from each side)
        val (cx, cy) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.CENTER)
        assertEquals(-100, cx)
        assertEquals(-100, cy)

        // Top-left fixed (expands towards bottom-right)
        val (tlX, tlY) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.TOP_LEFT)
        assertEquals(0, tlX)
        assertEquals(0, tlY)

        // Bottom-right fixed (expands towards top-left)
        val (brX, brY) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.BOTTOM_RIGHT)
        assertEquals(-200, brX)
        assertEquals(-200, brY)

        // Top-center fixed
        val (tcX, tcY) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.TOP_CENTER)
        assertEquals(-100, tcX)
        assertEquals(0, tcY)
    }

    @Test
    fun `anchor offsets calculated correctly for canvas cropping`() {
        val oldW = 1000
        val oldH = 1000
        val newW = 800
        val newH = 800

        // Center crop
        val (cx, cy) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.CENTER)
        assertEquals(100, cx)
        assertEquals(100, cy)

        // Top-left crop
        val (tlX, tlY) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.TOP_LEFT)
        assertEquals(0, tlX)
        assertEquals(0, tlY)

        // Bottom-right crop
        val (brX, brY) = AnchorPosition.computeOffset(oldW, oldH, newW, newH, AnchorPosition.BOTTOM_RIGHT)
        assertEquals(200, brX)
        assertEquals(200, brY)
    }

    @Test
    fun `aspect ratio preset calculations operate properly`() {
        val pOrig = AspectRatioPreset.ORIGINAL
        assertEquals(1000, pOrig.calculateHeight(2000, 2000, 1000))
        assertEquals(2000, pOrig.calculateWidth(1000, 2000, 1000))

        val pFree = AspectRatioPreset.FREE
        assertEquals(1500, pFree.calculateHeight(1500, 2000, 1000))
        assertEquals(1500, pFree.calculateWidth(1500, 2000, 1000))
    }

    @Test
    fun `state update with aspect ratio lock propagates dimensions and offsets`() {
        val state = CanvasAdjustState(
            origWidth = 1000,
            origHeight = 1000,
            targetWidth = 1000,
            targetHeight = 1000,
            lockAspectRatio = true,
            anchor = AnchorPosition.CENTER,
        )

        val updated = state.withTargetWidth(1200)
        assertEquals(1200, updated.targetWidth)
        assertEquals(1200, updated.targetHeight)
        assertEquals(-100, updated.cropX)
        assertEquals(-100, updated.cropY)
    }
}
