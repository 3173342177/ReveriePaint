// SPDX-License-Identifier: GPL-3.0-or-later
package com.reverie.paint.model

/** A leased batch belongs to the engine until release; the input array remains UI-owned. */
class LiquifyDabBuffers {
    private val free = ArrayDeque<FloatArray>().apply {
        repeat(4) { addLast(FloatArray(64 * LiquifyPath.DAB_STRIDE)) }
    }

    @Synchronized
    fun capture(source: FloatArray, count: Int): FloatArray {
        require(count >= 0 && count <= source.size / LiquifyPath.DAB_STRIDE)
        val size = count * LiquifyPath.DAB_STRIDE
        var batch = if (free.isEmpty()) FloatArray(size) else free.removeFirst()
        if (batch.size < size) batch = FloatArray(size)
        source.copyInto(batch, endIndex = size)
        return batch
    }

    @Synchronized
    fun release(batch: FloatArray) {
        if (free.size < 8) free.addLast(batch)
    }
}
