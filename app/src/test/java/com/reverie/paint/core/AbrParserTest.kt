/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AbrParserTest {

    @Test
    fun `decodePackBitsScanlines decodes literal and repeated runs correctly`() {
        // Test 2 scanlines of width 4:
        // Line 0: literal 2 bytes [10, 20] (n=1) + repeat 2 bytes [30] (n=-1, val=30) -> [10, 20, 30, 30]
        // Length of line 0 PackBits data: 1 (header n=1) + 2 + 1 (header n=-1) + 1 (val=30) -> 5 bytes
        // Line 1: repeat 4 bytes [99] -> n = -3 (repeats -(-3)+1 = 4 times) -> 2 bytes
        val height = 2
        val width = 4

        val line0 = byteArrayOf(
            0x01.toByte(), 10, 20,      // 2 literals: n=1
            (-1).toByte(), 30,          // 2 repeats: n=-1 (val=30)
        )
        val line1 = byteArrayOf(
            (-3).toByte(), 99           // 4 repeats: n=-3 (val=99)
        )

        val lengthsBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        lengthsBuf.putShort(line0.size.toShort())
        lengthsBuf.putShort(line1.size.toShort())

        val combined = lengthsBuf.array() + line0 + line1
        val decoded = AbrParser.decodePackBitsScanlines(combined, 0, height, width)

        assertNotNull(decoded)
        assertEquals(8, decoded!!.size)
        // Line 0
        assertEquals(10, decoded[0].toInt())
        assertEquals(20, decoded[1].toInt())
        assertEquals(30, decoded[2].toInt())
        assertEquals(30, decoded[3].toInt())
        // Line 1
        assertEquals(99, decoded[4].toInt())
        assertEquals(99, decoded[5].toInt())
        assertEquals(99, decoded[6].toInt())
        assertEquals(99, decoded[7].toInt())
    }

    @Test
    fun `encodeTipPng generates valid PNG header and chunks`() {
        val dummyTip = AbrParser.AbrDecodedTip(
            uuid = "test-uuid",
            index = 1,
            width = 4,
            height = 4,
            depth = 8,
            data = ByteArray(16) { (it * 16).toByte() },
        )

        val pngBytes = AbrParser.encodeTipPng(dummyTip)
        assertTrue(pngBytes.size > 20)

        // Verify PNG signature: 89 50 4E 47 0D 0A 1A 0A
        val expectedHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in 0 until 8) {
            assertEquals(expectedHeader[i], pngBytes[i])
        }

        // Verify IHDR is present
        val ihdrType = String(pngBytes, 12, 4, Charsets.ISO_8859_1)
        assertEquals("IHDR", ihdrType)
    }

    @Test
    fun `encodePreviewPng generates valid preview thumbnail PNG`() {
        val dummyTip = AbrParser.AbrDecodedTip(
            uuid = "test-uuid",
            index = 1,
            width = 32,
            height = 32,
            depth = 8,
            data = ByteArray(32 * 32) { 128.toByte() },
        )

        val previewBytes = AbrParser.encodePreviewPng(dummyTip, diameter = 32.0, roundness = 1.0)
        assertTrue(previewBytes.size > 50)

        // Verify PNG signature
        assertEquals(0x89.toByte(), previewBytes[0])
        assertEquals(0x50.toByte(), previewBytes[1])
        assertEquals(0x4E.toByte(), previewBytes[2])
        assertEquals(0x47.toByte(), previewBytes[3])
    }

    @Test
    fun `parse real ABR file extracts tips and presets accurately`() {
        val file = File("/home/lanrhyme/Projects/krita-source/libs/brush/tests/data/brushes_by_mar_ka_d338ela.abr")
        if (!file.exists()) return

        val result = FileInputStream(file).use { input ->
            AbrParser.parse(input, basePackName = "MarKa")
        }

        assertEquals(6, result.version)
        assertEquals(2, result.subversion)
        assertTrue("Tips count should be around 31", result.tips.size >= 30)
        assertTrue("Presets count should be around 36", result.presets.size >= 30)

        val firstPreset = result.presets.first()
        assertNotNull(firstPreset.name)
        assertTrue("Diameter should be positive", firstPreset.diameter > 0.0)
        assertTrue("Spacing should be valid fraction", firstPreset.spacing in 0.01..5.0)

        // Test tip PNG generation for one of the extracted tips
        val firstSampledTip = result.tips.first()
        val tipPng = AbrParser.encodeTipPng(firstSampledTip)
        assertTrue(tipPng.isNotEmpty())
        assertEquals(0x89.toByte(), tipPng[0])

        // Test preview PNG generation
        val previewPng = AbrParser.encodePreviewPng(firstSampledTip, firstPreset.diameter, firstPreset.roundness)
        assertTrue(previewPng.isNotEmpty())
        assertEquals(0x89.toByte(), previewPng[0])
    }
}
