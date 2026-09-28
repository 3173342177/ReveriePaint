/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationStateResetTest {

    @Test
    fun `AnimationState reset returns all animation properties to clean defaults`() {
        val anim = AnimationState()

        // Simulate dirty animation state from a previous project
        anim.enabled = true
        anim.panelOpen = true
        anim.currentTime = 15
        anim.length = 48
        anim.framerate = 24
        anim.playbackStart = 5
        anim.playbackEnd = 30
        anim.isPlaying = true
        anim.hasCustomPlaybackRange = true
        anim.selectedTrack = 2
        anim.selectedFrames = setOf(1, 2, 3)
        anim.isMultiSelectMode = true
        anim.scrollPx = 500f
        anim.pendingAddedFrame = 10
        anim.thumbRevision = 5
        anim.onionSkin = true
        anim.shiftTraceActive = true
        anim.audioAssets = listOf("bgm.mp3")
        anim.keyframeCache = mapOf(1 to listOf(0, 5, 10))
        anim.lastFrameHold = mapOf(1 to 5)
        anim.keyframeTags = mapOf(1L to 2)
        anim.isTemporaryOnionSkin = true
        anim.isFlipPeeking = true
        anim.flipOriginalTime = 4
        anim.revision = 12
        val oldPlayGen = anim.playGen

        // Call reset
        anim.reset()

        // Verify all states are reset
        assertFalse(anim.enabled)
        assertFalse(anim.panelOpen)
        assertEquals(0, anim.currentTime)
        assertEquals(1, anim.length)
        assertEquals(DEFAULT_ANIMATION_FPS, anim.framerate)
        assertEquals(0, anim.playbackStart)
        assertEquals(0, anim.playbackEnd)
        assertFalse(anim.isPlaying)
        assertTrue(anim.loopPlayback)
        assertFalse(anim.hasCustomPlaybackRange)
        assertEquals(-1, anim.selectedTrack)
        assertTrue(anim.selectedFrames.isEmpty())
        assertFalse(anim.isMultiSelectMode)
        assertEquals(0f, anim.scrollPx, 0.001f)
        assertEquals(-1, anim.pendingAddedFrame)
        assertEquals(0, anim.thumbRevision)
        assertFalse(anim.onionSkin)
        assertFalse(anim.shiftTraceActive)
        assertTrue(anim.audioAssets.isEmpty())
        assertTrue(anim.keyframeCache.isEmpty())
        assertTrue(anim.lastFrameHold.isEmpty())
        assertTrue(anim.keyframeTags.isEmpty())
        assertFalse(anim.isTemporaryOnionSkin)
        assertFalse(anim.isFlipPeeking)
        assertEquals(-1, anim.flipOriginalTime)
        assertEquals(13, anim.revision)
        assertEquals(oldPlayGen + 1, anim.playGen)
    }
}
