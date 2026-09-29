/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

/**
 * 画布变换的"单一写入者"仲裁器 (纯逻辑, 无 Android 依赖, 可 JVM 单测)。
 *
 * 背景: 画布变换 (zoom / rotation / pan) 会被三类来源写入 ——
 *  1. 新手势 (onTouchEvent 事件流);
 *  2. 吸附收敛动画 (snap);
 *  3. 满屏复位动画 (fit)。
 *
 * `ValueAnimator.cancel()` 之后已入队的动画帧在部分实现上仍可能被投递一次, 再加上
 * "动画帧回调" 与 "触摸事件" 在同一帧内交错, 就可能出现多个写入者同帧竞争, 表现为
 * 画布抖动或旋转回跳。这里用单调递增的令牌把"谁有权写"收敛成只有一个:
 *
 *  - 任何新写入者入场前先 [invalidateAll] (取消动画 / 新手势 ACTION_DOWN),
 *    旧令牌立刻失效;
 *  - 帧回调在写入前用 [isActive] 自检, 失效即整帧丢弃 (丢弃而非"少写一部分",
 *    因此不会留下半更新的状态)。
 *
 * 该类型只做令牌仲裁, 不持有任何画布状态。
 */
class CanvasTransformWriterGuard {

    private var token = 0

    /** 当前有效令牌; 0 表示尚未有任何写入者入场 */
    val current: Int get() = token

    /** 新写入者 (新手势 / 新动画) 入场并取得令牌 */
    fun begin(): Int {
        token += 1
        return token
    }

    /** 取消一切写入者 (终止动画 / 新手势接管), 返回新令牌供后续写入者使用 */
    fun invalidateAll(): Int = begin()

    /**
     * 某个写入者是否仍被允许写画布变换。
     *
     * 令牌 0 恒为无效: 调用方忘记入场取令牌时不会被误判为"仍然有效"。
     */
    fun isActive(writerToken: Int): Boolean = writerToken != 0 && writerToken == token
}
