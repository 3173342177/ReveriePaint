/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GesturePivotAndPersistenceTest {

    @Test
    fun `two-finger rotation and zoom keeps centroid point pinned on screen`() {
        val viewW = 1080f
        val viewH = 2400f
        var canvasPanX = 50f
        var canvasPanY = -30f
        var canvasZoom = 1.2f
        var canvasRotation = 15f

        // Initial 2-finger centroid on screen
        val prevCentroid = Offset(400f, 600f)
        val k = 1.35f // 35% zoom-in
        val dRot = 25f // 25 degree clockwise rotation
        val dPan = Offset(20f, -10f) // Centroid also translates by (20, -10)
        val newCentroid = prevCentroid + dPan

        // Exact transform math used in CanvasTouchView and ReferenceWindow
        val rad = Math.toRadians(dRot.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()

        val vx = prevCentroid.x - (viewW / 2f + canvasPanX)
        val vy = prevCentroid.y - (viewH / 2f + canvasPanY)

        val targetZoom = (canvasZoom * k).coerceIn(0.02f, 128f)
        val actualK = if (canvasZoom > 0.0001f) targetZoom / canvasZoom else 1f

        val vRotX = actualK * (vx * cosR - vy * sinR)
        val vRotY = actualK * (vx * sinR + vy * cosR)

        val newZoom = targetZoom
        val newRotation = canvasRotation + dRot
        val newPanX = newCentroid.x - vRotX - viewW / 2f
        val newPanY = newCentroid.y - vRotY - viewH / 2f

        // Verify that the vector from the old center to prevCentroid
        // when rotated by dRot and scaled by k matches the vector from new center to newCentroid
        val newCenter = Offset(viewW / 2f + newPanX, viewH / 2f + newPanY)
        val actualV = newCentroid - newCenter

        assertEquals(vRotX, actualV.x, 1e-4f)
        assertEquals(vRotY, actualV.y, 1e-4f)
    }

    @Test
    fun `when zoom hits maximum limit, further expansion does not cause canvas drift`() {
        val viewW = 1080f
        val viewH = 2400f
        val canvasPanX = 100f
        val canvasPanY = 200f
        val canvasZoom = 128f // Already at max zoom limit

        val prevCentroid = Offset(500f, 1000f)
        val k = 1.5f // User tries to zoom in 50% more with fingers at same centroid position
        val dRot = 0f
        val newCentroid = prevCentroid // No finger movement, just spreading

        val targetZoom = (canvasZoom * k).coerceIn(0.02f, 128f)
        val actualK = if (canvasZoom > 0.0001f) targetZoom / canvasZoom else 1f

        val vx = prevCentroid.x - (viewW / 2f + canvasPanX)
        val vy = prevCentroid.y - (viewH / 2f + canvasPanY)

        val vRotX = actualK * vx
        val vRotY = actualK * vy

        val newPanX = newCentroid.x - vRotX - viewW / 2f
        val newPanY = newCentroid.y - vRotY - viewH / 2f

        // Pan must remain exactly unchanged because centroid did not translate and zoom did not scale
        assertEquals(canvasPanX, newPanX, 1e-4f)
        assertEquals(canvasPanY, newPanY, 1e-4f)
        assertEquals(128f, targetZoom, 1e-4f)
    }

    @Test
    fun `eyedropper sensitivity correctly maps to responsive delays and move slop`() {
        fun computeEyedropperDelay(sensitivity: Int): Long {
            return (520L - (sensitivity.coerceIn(1, 5) - 1) * 70L).coerceIn(200L, 600L)
        }

        fun computeMoveSlopDp(sensitivity: Int): Float {
            return (1.5f + (sensitivity.coerceIn(1, 5) - 3) * 0.3f).coerceIn(0.6f, 2.5f)
        }

        assertEquals(520L, computeEyedropperDelay(1))
        assertEquals(450L, computeEyedropperDelay(2))
        assertEquals(380L, computeEyedropperDelay(3)) // Default -> 380ms
        assertEquals(310L, computeEyedropperDelay(4))
        assertEquals(240L, computeEyedropperDelay(5))

        // Level 3 is 1.5dp; lower levels have smaller thresholds (0.9dp, 1.2dp)
        assertEquals(0.9f, computeMoveSlopDp(1), 0.001f)
        assertEquals(1.2f, computeMoveSlopDp(2), 0.001f)
        assertEquals(1.5f, computeMoveSlopDp(3), 0.001f)
        assertEquals(1.8f, computeMoveSlopDp(4), 0.001f)
        assertEquals(2.1f, computeMoveSlopDp(5), 0.001f)
    }

    @Test
    fun `reference window bounds and tab state ranges`() {
        val minWidth = 160f
        val maxWidth = 600f
        val minHeight = 160f
        val maxHeight = 700f

        val clampWidth = { w: Float -> w.coerceIn(minWidth, maxWidth) }
        val clampHeight = { h: Float -> h.coerceIn(minHeight, maxHeight) }

        assertEquals(160f, clampWidth(50f), 0.01f)
        assertEquals(600f, clampWidth(1200f), 0.01f)
        assertEquals(320f, clampWidth(320f), 0.01f)

        assertEquals(160f, clampHeight(100f), 0.01f)
        assertEquals(700f, clampHeight(900f), 0.01f)
        assertEquals(400f, clampHeight(400f), 0.01f)
    }

    @Test
    fun `edge back gesture exclusion policy correctly toggles`() {
        fun shouldExcludeGesture(
            allowEdgeBack: Boolean,
            backKeyAction: BackKeyAction,
            overlayPanelsOpen: Boolean,
        ): Boolean {
            return (!allowEdgeBack || backKeyAction == BackKeyAction.NONE) && !overlayPanelsOpen
        }

        // Default: allowEdgeBack = true, backKeyAction = OPEN_SETTINGS -> edge back allowed (no exclusion)
        org.junit.Assert.assertFalse(shouldExcludeGesture(true, BackKeyAction.OPEN_SETTINGS, false))
        org.junit.Assert.assertFalse(shouldExcludeGesture(true, BackKeyAction.EXIT, false))

        // When back key action is NONE -> exclude edge back to prevent accidental back gesture
        assertTrue(shouldExcludeGesture(true, BackKeyAction.NONE, false))

        // When user explicitly turns off edge back -> exclude
        assertTrue(shouldExcludeGesture(false, BackKeyAction.OPEN_SETTINGS, false))

        // When panels are open -> never exclude, always allow edge swipe to close panels
        org.junit.Assert.assertFalse(shouldExcludeGesture(false, BackKeyAction.NONE, true))
        org.junit.Assert.assertFalse(shouldExcludeGesture(true, BackKeyAction.NONE, true))
    }

    @Test
    fun `edge touch detection and directional gesture classification`() {
        val density = 3.0f
        val edgeThreshold = 32f * density // 96px
        val viewW = 1080f

        fun isEdgeTouch(x: Float): Boolean {
            return x <= edgeThreshold || x >= viewW - edgeThreshold
        }

        assertTrue(isEdgeTouch(10f))
        assertTrue(isEdgeTouch(95f))
        org.junit.Assert.assertFalse(isEdgeTouch(100f))
        org.junit.Assert.assertFalse(isEdgeTouch(540f))
        org.junit.Assert.assertFalse(isEdgeTouch(980f))
        assertTrue(isEdgeTouch(990f))
        assertTrue(isEdgeTouch(1075f))

        // Direction classification: vertical movement along edge vs horizontal swipe back
        fun isVerticalMove(dx: Float, dy: Float): Boolean {
            val dist = kotlin.math.hypot(dx, dy)
            return abs(dy) > abs(dx) * 1.2f && dist > (8f * density)
        }

        // Horizontal swipe inward (e.g. from x=5 to x=35, dy=2)
        org.junit.Assert.assertFalse(isVerticalMove(30f, 2f))

        // Vertical drag along edge (e.g. dy=40, dx=5)
        assertTrue(isVerticalMove(5f, 40f))
    }

    @Test
    fun `reference window transform and eyedropper inverse sampling maintain exact 1-to-1 mapping under rotation and flip`() {
        val viewportW = 960f
        val viewportH = 1020f
        val bmpW = 2000f
        val bmpH = 1500f
        val totalW = bmpW
        val totalH = bmpH
        val bLeft = -bmpW / 2f
        val bTop = -bmpH / 2f
        val bWidth = bmpW
        val bHeight = bmpH

        // Test across multiple rotations, zooms, pans, and flip states
        val testAngles = listOf(-45f, -23.5f, 0f, 15f, 30f, 60f, 90f, 180f)
        val testZooms = listOf(0.5f, 1.0f, 2.5f)
        val testPans = listOf(Offset(0f, 0f), Offset(120f, -80f))
        val testFlips = listOf(false, true)

        for (rotation in testAngles) {
            for (zoom in testZooms) {
                for (pan in testPans) {
                    for (isFlipped in testFlips) {
                        val fitScale = minOf(viewportW / totalW, viewportH / totalH) * 0.92f
                        val finalScale = fitScale * zoom
                        val centerX = viewportW / 2f + pan.x
                        val centerY = viewportH / 2f + pan.y
                        val sx = if (isFlipped) -finalScale else finalScale
                        val sy = finalScale

                        val rad = Math.toRadians(rotation.toDouble())
                        val cosR = cos(rad).toFloat()
                        val sinR = sin(rad).toFloat()

                        // Pick test pixels within bitmap
                        val testPixels = listOf(
                            Offset(0f, 0f),
                            Offset(500f, 300f),
                            Offset(1000f, 750f),
                            Offset(1999f, 1499f)
                        )

                        for (pixel in testPixels) {
                            // Forward Transform (matches withTransform with pivot = Offset.Zero)
                            val worldX = bLeft + (pixel.x / bmpW) * bWidth
                            val worldY = bTop + (pixel.y / bmpH) * bHeight
                            val scaledX = worldX * sx
                            val scaledY = worldY * sy
                            val rotatedX = scaledX * cosR - scaledY * sinR
                            val rotatedY = scaledX * sinR + scaledY * cosR
                            val screenX = rotatedX + centerX
                            val screenY = rotatedY + centerY

                            // Inverse Transform (matches sampleReferenceColor)
                            val dx = screenX - centerX
                            val dy = screenY - centerY
                            val invRad = Math.toRadians(-rotation.toDouble())
                            val invCos = cos(invRad).toFloat()
                            val invSin = sin(invRad).toFloat()
                            val rx = dx * invCos - dy * invSin
                            val ry = dx * invSin + dy * invCos

                            val invWorldX = rx / sx
                            val invWorldY = ry / sy

                            val u = (invWorldX - bLeft) / bWidth
                            val v = (invWorldY - bTop) / bHeight
                            val sampledPixelX = u * bmpW
                            val sampledPixelY = v * bmpH

                            assertEquals("X mismatch at rot=$rotation zoom=$zoom flip=$isFlipped", pixel.x, sampledPixelX, 1e-2f)
                            assertEquals("Y mismatch at rot=$rotation zoom=$zoom flip=$isFlipped", pixel.y, sampledPixelY, 1e-2f)
                        }
                    }
                }
            }
        }
    }
}
