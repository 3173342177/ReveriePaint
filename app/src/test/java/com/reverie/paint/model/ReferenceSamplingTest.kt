/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import androidx.compose.ui.graphics.Color
import com.reverie.paint.ui.painting.colorToHex
import com.reverie.paint.ui.painting.toGrayscale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceSamplingTest {

    @Test
    fun `colorToHex formats pure primary colors to standard 6 digit hex`() {
        assertEquals("#FF0000", colorToHex(Color(1f, 0f, 0f)))
        assertEquals("#00FF00", colorToHex(Color(0f, 1f, 0f)))
        assertEquals("#0000FF", colorToHex(Color(0f, 0f, 1f)))
        assertEquals("#FFFFFF", colorToHex(Color(1f, 1f, 1f)))
        assertEquals("#000000", colorToHex(Color(0f, 0f, 0f)))
    }

    @Test
    fun `colorToHex rounds intermediate values properly`() {
        assertEquals("#808080", colorToHex(Color(128f / 255f, 128f / 255f, 128f / 255f)))
        assertEquals("#1A2B3C", colorToHex(Color(0x1A / 255f, 0x2B / 255f, 0x3C / 255f)))
    }

    @Test
    fun `toGrayscale produces accurate luminance values`() {
        // Pure black
        val black = toGrayscale(0xFF000000.toInt())
        assertEquals(0f, black.red, 0.001f)
        assertEquals(0f, black.green, 0.001f)
        assertEquals(0f, black.blue, 0.001f)

        // Pure white
        val white = toGrayscale(-1)
        assertEquals(1f, white.red, 0.001f)
        assertEquals(1f, white.green, 0.001f)
        assertEquals(1f, white.blue, 0.001f)

        // Standard NTSC/Rec601 weights: 0.213 R, 0.715 G, 0.072 B
        val redGray = toGrayscale(0xFFFF0000.toInt())
        assertEquals(0.213f, redGray.red, 0.005f)
        assertEquals(redGray.red, redGray.green, 0.001f)
        assertEquals(redGray.green, redGray.blue, 0.001f)

        val greenGray = toGrayscale(0xFF00FF00.toInt())
        assertEquals(0.715f, greenGray.red, 0.005f)
        assertEquals(greenGray.red, greenGray.green, 0.001f)
        assertEquals(greenGray.green, greenGray.blue, 0.001f)
    }
}
