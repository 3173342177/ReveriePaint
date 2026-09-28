package com.reverie.paint.model

import org.junit.Assert.*
import org.junit.Test

class LiquifyDabBuffersTest {
    @Test
    fun `next UI frame cannot overwrite a queued engine batch`() {
        val pool = LiquifyDabBuffers()
        val input = FloatArray(LiquifyPath.DAB_STRIDE) { it + 0.25f }
        val first = pool.capture(input, 1)
        input.fill(99f)
        val second = pool.capture(input, 1)
        assertNotSame(first, second)
        assertEquals(0.25f, first[0], 0f)
        assertEquals(99f, second[0], 0f)
        pool.release(first)
        pool.release(second)
    }

    @Test
    fun `large and queued batches preserve subpixel coordinates`() {
        val pool = LiquifyDabBuffers()
        val input = FloatArray(300 * LiquifyPath.DAB_STRIDE) { it * 0.001f }
        val held = List(10) { pool.capture(input, 300) }
        input.fill(0f)
        held.forEach { batch ->
            assertEquals(1.799f, batch[1799], 1e-6f)
            pool.release(batch)
        }
    }
}
