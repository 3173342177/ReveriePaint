/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1 回归: 画布变换的单一写入者仲裁。
 *
 * 对应实现: CanvasTouchView.cancelCanvasTransformAnimators() + 两个动画帧回调里的令牌自检。
 * 覆盖: 新手势打断动画、同帧多写入者竞争、取消后无残留写入。
 */
class CanvasTransformWriterGuardTest {

    @Test
    fun `starts with no writer and token zero is never valid`() {
        val guard = CanvasTransformWriterGuard()
        assertEquals(0, guard.current)
        // 忘记取令牌的写入者不得被误判为有效
        assertFalse(guard.isActive(0))
    }

    @Test
    fun `new gesture invalidates the in-flight animation writer`() {
        val guard = CanvasTransformWriterGuard()
        // fit 动画入场
        val fitAnimation = guard.begin()
        assertTrue(guard.isActive(fitAnimation))

        // 新手势起点: 取消所有画布变换动画
        val newGesture = guard.invalidateAll()
        assertFalse("旧动画（含已入队的帧）必须整帧丢弃", guard.isActive(fitAnimation))
        assertTrue(guard.isActive(newGesture))
    }

    @Test
    fun `only the latest writer of a frame may write canvas transform`() {
        val guard = CanvasTransformWriterGuard()
        val fit = guard.begin()
        val snap = guard.begin()
        val newGestureToken = guard.invalidateAll()

        // 同一帧内三个写入者依次尝试写画布变换
        var writes = 0
        if (guard.isActive(fit)) writes++
        if (guard.isActive(snap)) writes++
        if (guard.isActive(newGestureToken)) writes++
        assertEquals("同一帧内只允许一个写入者生效", 1, writes)
    }

    @Test
    fun `invalidate leaves no residual writer for queued frames`() {
        val guard = CanvasTransformWriterGuard()
        val queuedFrameToken = guard.begin()

        repeat(3) { guard.invalidateAll() }

        assertFalse("取消后已入队的帧回调也必须被丢弃", guard.isActive(queuedFrameToken))
        // 反复失效不会让任何历史令牌"复活"
        assertFalse(guard.isActive(1))
        assertFalse(guard.isActive(2))
        assertFalse(guard.isActive(3))
    }

    @Test
    fun `tokens never repeat so an old writer cannot revive`() {
        val guard = CanvasTransformWriterGuard()
        val seen = linkedSetOf<Int>()
        repeat(64) { seen.add(guard.begin()) }
        assertEquals("令牌必须单调递增且不重复", 64, seen.size)

        val latest = guard.current
        assertTrue(guard.isActive(latest))
        seen.filter { it != latest }.forEach { assertFalse(guard.isActive(it)) }
    }

    @Test
    fun `later animation keeps writing while the replaced one writes nothing`() {
        val guard = CanvasTransformWriterGuard()
        val fit = guard.begin()
        // 吸附收敛动画后启动, 取得独占写入权
        val snap = guard.begin()

        var fitWrites = 0
        var snapWrites = 0
        repeat(10) {
            if (guard.isActive(fit)) fitWrites++
            if (guard.isActive(snap)) snapWrites++
        }
        assertEquals(0, fitWrites)
        assertEquals(10, snapWrites)
    }
}
