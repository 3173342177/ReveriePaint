/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MemoryBudget] 的边界测试 —— 这些数字直接决定"场通路能不能开 / 大图会不会 OOM",
 * 任何放宽都必须先过这里。
 */
class MemoryBudgetTest {

    @Test
    fun `fieldPathBudgetPx uses hard cap when heap unknown`() {
        assertEquals(MemoryBudget.FIELD_MAX_PX, MemoryBudget.fieldPathBudgetPx(0L, false))
        assertEquals(MemoryBudget.FIELD_MAX_PX_LOW_RAM, MemoryBudget.fieldPathBudgetPx(-1L, true))
    }

    @Test
    fun `fieldPathBudgetPx clamps by heap budget`() {
        val heap = 128L * 1024 * 1024
        assertEquals(heap / MemoryBudget.FIELD_BYTES_PER_PX, MemoryBudget.fieldPathBudgetPx(heap, false))
        // 堆很大时不超过历史硬上限
        assertEquals(
            MemoryBudget.FIELD_MAX_PX,
            MemoryBudget.fieldPathBudgetPx(8L * 1024 * 1024 * 1024, false),
        )
        // 低内存设备同时受两个上限约束(取小)
        assertEquals(
            MemoryBudget.FIELD_MAX_PX_LOW_RAM,
            MemoryBudget.fieldPathBudgetPx(8L * 1024 * 1024 * 1024, true),
        )
    }

    @Test
    fun `fieldPathBudgetPx can drop to zero on tiny heaps`() {
        // 32MB 堆 ⇒ 1M px 预算以下: 不返回负数
        val budget = MemoryBudget.fieldPathBudgetPx(16L * 1024 * 1024, false)
        assertTrue(budget >= 0L)
        assertTrue(budget < MemoryBudget.FIELD_MAX_PX_LOW_RAM)
    }

    @Test
    fun `commitBytesWithinBudget rejects invalid and oversize`() {
        assertFalse(MemoryBudget.commitBytesWithinBudget(0, 100))
        assertFalse(MemoryBudget.commitBytesWithinBudget(100, -1))
        assertFalse(MemoryBudget.commitBytesWithinBudget(Int.MAX_VALUE, Int.MAX_VALUE))
        // 4M px = 16MB: 在 20MB 预算内
        assertTrue(MemoryBudget.commitBytesWithinBudget(2048, 2048))
        // 6M px = 24MB: 超预算
        assertFalse(MemoryBudget.commitBytesWithinBudget(3000, 2000))
    }

    @Test
    fun `commitBytesWithinBudget honours custom budget`() {
        val w = 1024
        val h = 1024
        assertTrue(MemoryBudget.commitBytesWithinBudget(w, h, 4L * 1024 * 1024))
        assertFalse(MemoryBudget.commitBytesWithinBudget(w, h, 4L * 1024 * 1024 - 1))
        assertFalse(MemoryBudget.commitBytesWithinBudget(w, h, 0L))
    }

    @Test
    fun `decodeBudgetBytes is a quarter of heap within caps`() {
        assertEquals(
            MemoryBudget.DECODE_MAX_BYTES,
            MemoryBudget.decodeBudgetBytes(0L),
        )
        val heap = 512L * 1024 * 1024
        assertEquals(heap / 4, MemoryBudget.decodeBudgetBytes(heap))
        // 极小堆: 仍有下限(4MB), 不至于算出 0 预算
        assertEquals(4L * 1024 * 1024, MemoryBudget.decodeBudgetBytes(4L * 1024 * 1024))
        // 超大堆: 受硬上限约束
        assertEquals(
            MemoryBudget.DECODE_MAX_BYTES,
            MemoryBudget.decodeBudgetBytes(64L * 1024 * 1024 * 1024),
        )
    }

    @Test
    fun `sampleSize returns 1 when within both limits`() {
        val heap = 512L * 1024 * 1024
        assertEquals(1, MemoryBudget.sampleSize(4000, 3000, 8192, heap))
        // 8192x8192 = 256MB 超过解码硬上限(192MB) ⇒ 即便堆很大也必须降采样到 2x
        assertEquals(2, MemoryBudget.sampleSize(8192, 8192, 8192, 2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `sampleSize grows for dimension limit`() {
        val heap = 8L * 1024 * 1024 * 1024
        assertEquals(2, MemoryBudget.sampleSize(12000, 8000, 8192, heap))
        assertEquals(4, MemoryBudget.sampleSize(20000, 30000, 8192, heap))
    }

    @Test
    fun `sampleSize grows for heap budget on a big PNG`() {
        // 8000x8000 = 256MB 位图, 堆 192MB ⇒ 预算 48MB ⇒ 必须降采样(2x 时仍有 61MB, 不够)
        val heap = 192L * 1024 * 1024
        val sample = MemoryBudget.sampleSize(8000, 8000, 8192, heap)
        assertTrue("expected >= 2 but was $sample", sample >= 2)
        val decodedBytes = (8000L / sample) * (8000L / sample) * 4L
        assertTrue(
            "decoded $decodedBytes exceeds budget ${MemoryBudget.decodeBudgetBytes(heap)}",
            decodedBytes <= MemoryBudget.decodeBudgetBytes(heap),
        )
    }

    @Test
    fun `sampleSize caps at MAX_SAMPLE and never returns zero`() {
        assertEquals(MemoryBudget.MAX_SAMPLE, MemoryBudget.sampleSize(1 shl 20, 1 shl 20, 16, 1024L))
        assertEquals(1, MemoryBudget.sampleSize(0, 100, 8192, 0L))
        assertEquals(1, MemoryBudget.sampleSize(100, -5, 8192, 0L))
    }
}
