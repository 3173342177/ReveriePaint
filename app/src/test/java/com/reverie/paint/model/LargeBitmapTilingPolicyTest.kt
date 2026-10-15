/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeBitmapTilingPolicyTest {

    @Test
    fun `isLargeBitmap identifies byte count greater than 100MB`() {
        val bytes6000x6000 = 6000L * 6000L * 4L // 144,000,000 bytes (~137.3MB)
        val bytes2048x2048 = 2048L * 2048L * 4L // 16,777,216 bytes (16MB)
        val bytes4096x4096 = 4096L * 4096L * 4L // 67,108,864 bytes (64MB)
        val bytes8192x8192 = 8192L * 8192L * 4L // 268,435,456 bytes (256MB)

        assertTrue(LargeBitmapTilingPolicy.isLargeBitmap(bytes6000x6000))
        assertTrue(LargeBitmapTilingPolicy.isLargeBitmap(bytes8192x8192))
        assertFalse(LargeBitmapTilingPolicy.isLargeBitmap(bytes2048x2048))
        assertFalse(LargeBitmapTilingPolicy.isLargeBitmap(bytes4096x4096))
    }

    @Test
    fun `tileCount partitions dimensions correctly for 2048 tile size`() {
        val (cols6k, rows6k) = LargeBitmapTilingPolicy.tileCount(6000, 6000)
        assertEquals(3, cols6k)
        assertEquals(3, rows6k)

        val (cols8k, rows8k) = LargeBitmapTilingPolicy.tileCount(8192, 8192)
        assertEquals(4, cols8k)
        assertEquals(4, rows8k)

        val (cols4k, rows4k) = LargeBitmapTilingPolicy.tileCount(4096, 2048)
        assertEquals(2, cols4k)
        assertEquals(1, rows4k)
    }

    @Test
    fun `computeTileDirtyIntersection accurately returns intersecting rectangle`() {
        // Tile covers [2048, 2048, 4096, 4096]
        val tileSx = 2048
        val tileSy = 2048
        val tileW = 2048
        val tileH = 2048

        // Dirty rect completely inside tile: [3000, 3000, 3100, 3100]
        val interInside = LargeBitmapTilingPolicy.computeTileDirtyIntersection(
            tileSx, tileSy, tileW, tileH,
            3000, 3000, 3100, 3100
        )
        assertArrayEquals(intArrayOf(3000, 3000, 3100, 3100), interInside)

        // Dirty rect partially overlapping tile edge: [1900, 1900, 2200, 2200]
        val interEdge = LargeBitmapTilingPolicy.computeTileDirtyIntersection(
            tileSx, tileSy, tileW, tileH,
            1900, 1900, 2200, 2200
        )
        assertArrayEquals(intArrayOf(2048, 2048, 2200, 2200), interEdge)

        // Dirty rect completely outside tile: [0, 0, 1000, 1000]
        val interOutside = LargeBitmapTilingPolicy.computeTileDirtyIntersection(
            tileSx, tileSy, tileW, tileH,
            0, 0, 1000, 1000
        )
        assertNull(interOutside)
    }

    @Test
    fun `isTileVisible performs accurate viewport culling`() {
        // Tile at [2048, 2048, 4096, 4096]
        val tileSx = 2048
        val tileSy = 2048
        val tileW = 2048
        val tileH = 2048

        // Viewport zoomed into tile: [2500, 2500, 3000, 3000]
        assertTrue(LargeBitmapTilingPolicy.isTileVisible(tileSx, tileSy, tileW, tileH, 2500, 2500, 3000, 3000))

        // Viewport on another part of the canvas: [0, 0, 1500, 1500]
        assertFalse(LargeBitmapTilingPolicy.isTileVisible(tileSx, tileSy, tileW, tileH, 0, 0, 1500, 1500))

        // Viewport covering the whole canvas: [0, 0, 6000, 6000]
        assertTrue(LargeBitmapTilingPolicy.isTileVisible(tileSx, tileSy, tileW, tileH, 0, 0, 6000, 6000))
    }

    @Test
    fun `isLargeCanvas triggers warning on large dimensions and high memory`() {
        // Standard presets shouldn't trigger
        assertFalse(LargeBitmapTilingPolicy.isLargeCanvas(1920, 1080))
        assertFalse(LargeBitmapTilingPolicy.isLargeCanvas(2048, 2048))
        assertFalse(LargeBitmapTilingPolicy.isLargeCanvas(3840, 2160)) // 4K UHD: 33MB
        assertFalse(LargeBitmapTilingPolicy.isLargeCanvas(2480, 3508)) // A4: 35MB
        assertFalse(LargeBitmapTilingPolicy.isLargeCanvas(4096, 4096)) // 4K Square: 67MB

        // Extra large canvases must trigger
        assertTrue(LargeBitmapTilingPolicy.isLargeCanvas(5000, 5000)) // 100MB (>80MB)
        assertTrue(LargeBitmapTilingPolicy.isLargeCanvas(6000, 6000)) // 144MB
        assertTrue(LargeBitmapTilingPolicy.isLargeCanvas(8192, 8192)) // 268MB
        assertTrue(LargeBitmapTilingPolicy.isLargeCanvas(5000, 4500)) // both > 4096
        assertTrue(LargeBitmapTilingPolicy.isLargeCanvas(6000, 2000)) // max >= 6000
    }

    @Test
    fun `singleLayerMegabytes calculates correct buffer size in MB`() {
        assertEquals(0, LargeBitmapTilingPolicy.singleLayerMegabytes(0, 0))
        assertEquals(16, LargeBitmapTilingPolicy.singleLayerMegabytes(2048, 2048))
        assertEquals(64, LargeBitmapTilingPolicy.singleLayerMegabytes(4096, 4096))
        assertEquals(138, LargeBitmapTilingPolicy.singleLayerMegabytes(6000, 6000))
        assertEquals(256, LargeBitmapTilingPolicy.singleLayerMegabytes(8192, 8192))
    }
}
