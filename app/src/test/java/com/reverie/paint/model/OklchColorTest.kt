/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OklchColorTest {

    @Test
    fun `black and white round trip test`() {
        // Pure black
        val blackOklch = OklchColor.fromRgb(0, 0, 0)
        assertEquals(0f, blackOklch.l, 0.01f)
        assertEquals(0f, blackOklch.c, 0.01f)
        val blackRgb = blackOklch.toRgbInt()
        assertEquals(0x00, (blackRgb shr 16) and 0xFF)
        assertEquals(0x00, (blackRgb shr 8) and 0xFF)
        assertEquals(0x00, blackRgb and 0xFF)

        // Pure white
        val whiteOklch = OklchColor.fromRgb(255, 255, 255)
        assertEquals(1f, whiteOklch.l, 0.01f)
        assertEquals(0f, whiteOklch.c, 0.01f)
        val whiteRgb = whiteOklch.toRgbInt()
        assertEquals(0xFF, (whiteRgb shr 16) and 0xFF)
        assertEquals(0xFF, (whiteRgb shr 8) and 0xFF)
        assertEquals(0xFF, whiteRgb and 0xFF)
    }

    @Test
    fun `primary colors round trip test`() {
        val testColors = listOf(
            Triple(255, 0, 0),     // Red
            Triple(0, 255, 0),     // Green
            Triple(0, 0, 255),     // Blue
            Triple(255, 255, 0),   // Yellow
            Triple(0, 255, 255),   // Cyan
            Triple(255, 0, 255),   // Magenta
            Triple(128, 128, 128), // Gray
            Triple(64, 180, 220),  // Light Sky Blue
            Triple(210, 105, 30),  // Chocolate
        )

        for ((r, g, b) in testColors) {
            val oklch = OklchColor.fromRgb(r, g, b)
            assertTrue("Color ($r, $g, $b) should be in gamut", oklch.isInGamut())

            val roundRgb = oklch.toRgbInt(clip = false)
            val rr = (roundRgb shr 16) and 0xFF
            val rg = (roundRgb shr 8) and 0xFF
            val rb = roundRgb and 0xFF

            assertTrue("R channel error too large: expected $r, got $rr", abs(r - rr) <= 2)
            assertTrue("G channel error too large: expected $g, got $rg", abs(g - rg) <= 2)
            assertTrue("B channel error too large: expected $b, got $rb", abs(b - rb) <= 2)
        }
    }

    @Test
    fun `gamut clipping test`() {
        // High chroma at L=0.5, H=140 is outside sRGB
        val outOfGamut = OklchColor(0.5f, 0.35f, 140f)
        assertFalse(outOfGamut.isInGamut())

        val clipped = outOfGamut.clipToGamut()
        assertTrue(clipped.isInGamut())
        assertEquals(outOfGamut.l, clipped.l, 0.001f)
        assertEquals(outOfGamut.h, clipped.h, 0.001f)
        assertTrue("Clipped chroma should be smaller", clipped.c < outOfGamut.c)
    }

    @Test
    fun `lut precomputation and interpolation test`() {
        val lut = OklchColor.computeMaxChromaLUT(0.6f, 360)
        assertEquals(360, lut.size)
        // Check for continuity and positive values
        for (i in 0 until 360) {
            assertTrue("Chroma should be positive", lut[i] > 0f)
            assertTrue("Chroma should not exceed MAX_CHROMA", lut[i] <= OklchColor.MAX_CHROMA)
        }

        // Test smooth sampling
        val sampled0 = OklchColor.sampleMaxChromaFromLUT(lut, 0f)
        val sampledHalf = OklchColor.sampleMaxChromaFromLUT(lut, 0.5f)
        val sampled1 = OklchColor.sampleMaxChromaFromLUT(lut, 1f)
        assertTrue(sampledHalf in minOf(sampled0, sampled1)..maxOf(sampled0, sampled1))
    }
}
