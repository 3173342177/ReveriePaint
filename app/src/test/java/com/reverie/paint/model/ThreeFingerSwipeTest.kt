/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.reverie.paint.model

import org.junit.Assert.*
import org.junit.Test

class ThreeFingerSwipeTest {
    private fun tracker(density: Float = 1f) = ThreeFingerSwipe().apply {
        begin(1000L, density)
        for (i in 0..2) setStart(i, i * 40f * density, 100f * density)
    }

    @Test fun `three fingers moving down together trigger once`() {
        val gesture = tracker()
        for (i in 0..2) gesture.update(i, i * 40f + 4f, 156f)
        gesture.evaluate(1200L)
        assertTrue(gesture.swiped)
        assertFalse(gesture.isTap(1200L))
        gesture.evaluate(1800L)
        assertTrue(gesture.swiped)
    }

    @Test fun `tap jitter remains a redo tap`() {
        val gesture = tracker()
        for (i in 0..2) gesture.update(i, i * 40f + 2f, 103f)
        gesture.evaluate(1100L)
        assertTrue(gesture.isTap(1100L))
        assertFalse(gesture.swiped)
    }

    @Test fun `one moving finger cannot open menu or redo`() {
        val gesture = tracker()
        gesture.update(0, 0f, 160f)
        gesture.evaluate(1200L)
        assertFalse(gesture.swiped)
        assertFalse(gesture.isTap(1200L))
    }

    @Test fun `horizontal and upward swipes are rejected`() {
        for ((x, y) in listOf(60f to 100f, 0f to 40f)) {
            val gesture = tracker()
            for (i in 0..2) gesture.update(i, i * 40f + x, y)
            gesture.evaluate(1200L)
            assertTrue(gesture.rejected)
            assertFalse(gesture.swiped)
        }
    }

    @Test fun `pinching and unequal finger travel do not open menu`() {
        val gesture = tracker()
        for (i in 0..2) gesture.update(i, i * 40f, 150f + i * 30f)
        gesture.evaluate(1200L)
        assertFalse(gesture.swiped)
        assertFalse(gesture.isTap(1200L))
    }

    @Test fun `gesture thresholds scale with screen density`() {
        val gesture = tracker(3f)
        for (i in 0..2) gesture.update(i, i * 120f, 360f)
        gesture.evaluate(1100L)
        assertFalse(gesture.swiped)
        for (i in 0..2) gesture.update(i, i * 120f, 456f)
        gesture.evaluate(1200L)
        assertTrue(gesture.swiped)
    }

    @Test fun `late slide cannot become menu after a long hold`() {
        val gesture = tracker()
        for (i in 0..2) gesture.update(i, i * 40f, 160f)
        gesture.evaluate(1700L)
        assertTrue(gesture.rejected)
        assertFalse(gesture.swiped)
    }

    @Test fun `fourth pointer or cancellation invalidates a recognized swipe`() {
        val gesture = tracker()
        for (i in 0..2) gesture.update(i, i * 40f, 160f)
        gesture.evaluate(1200L)
        gesture.reject()
        assertFalse(gesture.swiped)
        assertFalse(gesture.isTap(1200L))
    }

    @Test fun `new gesture does not inherit previous movement`() {
        val gesture = tracker()
        gesture.reject()
        gesture.begin(2000L, 1f)
        for (i in 0..2) gesture.setStart(i, i * 40f, 100f)
        assertTrue(gesture.isTap(2100L))
        assertFalse(gesture.swiped)
    }
    @Test fun `invalid travel on any pointer rejects before an unfinished pointer returns`() {
        for (pointer in 0..2) {
            for ((dx, dy) in listOf(60f to 0f, 0f to -20f)) {
                val gesture = tracker()
                gesture.update(pointer, pointer * 40f + dx, 100f + dy)
                gesture.evaluate(1100L)
                assertTrue("pointer $pointer must reject the gesture", gesture.rejected)
                for (i in 0..2) gesture.update(i, i * 40f, 160f)
                gesture.evaluate(1200L)
                assertFalse(gesture.swiped)
                assertFalse(gesture.isTap(1200L))
            }
        }
    }

    @Test fun `valid staggered travel waits for every finger without rejection`() {
        val gesture = tracker()
        for (i in 2 downTo 0) {
            gesture.update(i, i * 40f, 160f)
            gesture.evaluate(1100L + (2 - i) * 50L)
            assertFalse(gesture.rejected)
            assertEquals(i == 0, gesture.swiped)
        }
    }
}
