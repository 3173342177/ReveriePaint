/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.SyncTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncTimeFormatTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test
    fun `absent timestamp returns null`() {
        assertNull(SyncTimeFormat.relative(now, 0L))
        assertNull(SyncTimeFormat.relative(now, -1L))
    }

    @Test
    fun `under one minute is just now`() {
        assertEquals(SyncTimeFormat.Unit.JUST_NOW, SyncTimeFormat.relative(now, now - 30_000L)!!.unit)
        assertEquals(SyncTimeFormat.Unit.JUST_NOW, SyncTimeFormat.relative(now, now)!!.unit)
    }

    @Test
    fun `future timestamp clamps to just now`() {
        assertEquals(SyncTimeFormat.Unit.JUST_NOW, SyncTimeFormat.relative(now, now + 10 * minute)!!.unit)
    }

    @Test
    fun `minutes hours days buckets`() {
        val five = SyncTimeFormat.relative(now, now - 5 * minute)!!
        assertEquals(SyncTimeFormat.Unit.MINUTES, five.unit)
        assertEquals(5L, five.value)

        val ninety = SyncTimeFormat.relative(now, now - 90 * minute)!!
        assertEquals(SyncTimeFormat.Unit.HOURS, ninety.unit)
        assertEquals(1L, ninety.value)

        val day = SyncTimeFormat.relative(now, now - 25 * 60 * minute)!!
        assertEquals(SyncTimeFormat.Unit.DAYS, day.unit)
        assertEquals(1L, day.value)
    }
}
