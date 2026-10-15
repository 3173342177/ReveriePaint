/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

/**
 * 超大画布 (>100MB, 如 6000x6000、8192x8192) 分块瓦片绘制策略与视口裁剪计算。
 * 纯 Kotlin 逻辑, 无 Android 依赖, 可完整 JVM 单元测试。
 */
object LargeBitmapTilingPolicy {
    const val TILE_DIM = 2048
    const val LARGE_BITMAP_THRESHOLD_BYTES = 100L * 1024L * 1024L // 100MB HWUI 绘制阈值

    fun isLargeBitmap(byteCount: Long): Boolean = byteCount > LARGE_BITMAP_THRESHOLD_BYTES

    fun tileCount(width: Int, height: Int, tileDim: Int = TILE_DIM): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val cols = (width + tileDim - 1) / tileDim
        val rows = (height + tileDim - 1) / tileDim
        return cols to rows
    }

    /**
     * 计算瓦片与脏区矩形的相交区域。
     * @return 命中时返回 [interL, interT, interR, interB], 未相交返回 null
     */
    fun computeTileDirtyIntersection(
        tileSx: Int,
        tileSy: Int,
        tileWidth: Int,
        tileHeight: Int,
        dirtyL: Int,
        dirtyT: Int,
        dirtyR: Int,
        dirtyB: Int,
    ): IntArray? {
        val interL = maxOf(tileSx, dirtyL)
        val interT = maxOf(tileSy, dirtyT)
        val interR = minOf(tileSx + tileWidth, dirtyR)
        val interB = minOf(tileSy + tileHeight, dirtyB)
        return if (interR > interL && interB > interT) {
            intArrayOf(interL, interT, interR, interB)
        } else {
            null
        }
    }

    /**
     * 判断瓦片是否在视口可见范围 [visL, visT, visR, visB] 内。
     */
    fun isTileVisible(
        tileSx: Int,
        tileSy: Int,
        tileWidth: Int,
        tileHeight: Int,
        visL: Int,
        visT: Int,
        visR: Int,
        visB: Int,
    ): Boolean {
        return tileSx + tileWidth >= visL && tileSx <= visR &&
            tileSy + tileHeight >= visT && tileSy <= visB
    }
}
