/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationSnapGestureTest {
    @Test
    fun `activation inside detent preserves the free angle in both directions`() {
        for (threshold in listOf(5f, 30f)) {
            for (direction in listOf(-1f, 1f)) {
                val start = 90f + direction * threshold * 0.3f
                val gesture = RotationSnapGesture().apply { begin(start) }
                val before = gesture.update(direction * 0.59f, threshold)
                assertFalse(gesture.isActive)
                val activated = gesture.update(direction * 0.02f, threshold)
                assertTrue(gesture.isActive)
                assertEquals(before + direction * 0.02f, activated, 0.0001f)
                assertEquals(activated, gesture.update(0f, threshold), 0.0001f)
                val next = gesture.update(direction * 0.02f, threshold)
                assertTrue((next - activated) * direction >= -0.0001f)
            }
        }
    }

    @Test
    fun `zero net jitter never arms or qualifies for release settling`() {
        val gesture = RotationSnapGesture().apply { begin(92f) }
        repeat(1000) {
            gesture.update(0.1f, 5f)
            assertEquals(92f, gesture.update(-0.1f, 5f), 0.0001f)
            assertFalse(gesture.isActive)
        }
    }

    @Test
    fun `subthreshold excursions do not accumulate into intent`() {
        val gesture = RotationSnapGesture().apply { begin(92f) }
        repeat(100) {
            for (delta in listOf(0.4f, -0.8f, 0.4f)) {
                gesture.update(delta, 5f)
                assertFalse(gesture.isActive)
            }
        }
        gesture.update(-0.7f, 5f)
        assertTrue(gesture.isActive)
    }

    @Test
    fun `continued movement through a detent is monotonic and reverses without drift`() {
        for (threshold in listOf(5f, 30f)) {
            val gesture = RotationSnapGesture().apply { begin(90f - threshold * 0.3f) }
            val activated = gesture.update(0.7f, threshold)
            var previous = activated
            repeat(800) {
                val next = gesture.update(0.05f, threshold)
                assertTrue(next >= previous - 0.0001f)
                assertTrue(next - previous <= 0.07f)
                previous = next
            }
            repeat(800) {
                val next = gesture.update(-0.05f, threshold)
                assertTrue(next <= previous + 0.0001f)
                previous = next
            }
            assertEquals(activated, previous, 0.002f)
        }
    }

    @Test
    fun `disabled snapping remains free and never settles`() {
        val gesture = RotationSnapGesture().apply { begin(92f) }
        assertEquals(94f, gesture.update(2f, 0f), 0f)
        assertFalse(gesture.isActive)
        assertEquals(92f, gesture.update(-2f, 0f), 0f)
    }

    @Test
    fun `begin clears intent from the previous gesture`() {
        val gesture = RotationSnapGesture().apply { begin(92f) }
        gesture.update(1f, 5f)
        assertTrue(gesture.isActive)
        gesture.begin(89f)
        assertFalse(gesture.isActive)
        assertEquals(89f, gesture.rawDegrees, 0f)
        assertEquals(89.1f, gesture.update(0.1f, 5f), 0.0001f)
        assertFalse(gesture.isActive)
    }

    @Test
    fun `activation stays continuous across wrapped and negative angles`() {
        for (start in listOf(-361.5f, -1.5f, 358.5f, 718.5f)) {
            val gesture = RotationSnapGesture().apply { begin(start) }
            val activated = gesture.update(0.7f, 5f)
            assertEquals(start + 0.7f, activated, 0.0001f)
            assertEquals(activated, gesture.update(0f, 5f), 0.0002f)
            assertTrue(gesture.update(0.1f, 5f) >= activated)
        }
    }

    @Test
    fun `threshold changes preserve the displayed angle`() {
        val gesture = RotationSnapGesture().apply { begin(92f) }
        val activated = gesture.update(0.7f, 5f)
        assertEquals(activated, gesture.update(0f, 30f), 0.0001f)
        assertEquals(activated, gesture.update(0f, 30f), 0.0001f)
        assertEquals(activated, gesture.update(0f, 0f), 0.0001f)
        assertFalse(gesture.isActive)
    }
}
