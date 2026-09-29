/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

internal object SyncTimeFormat {
    enum class Unit { JUST_NOW, MINUTES, HOURS, DAYS }

    data class Relative(
        val unit: Unit,
        val value: Long,
    )

    fun relative(
        nowMs: Long,
        thenMs: Long,
    ): Relative? {
        if (thenMs <= 0L) return null
        val minutes = ((nowMs - thenMs).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1L -> Relative(Unit.JUST_NOW, 0L)
            minutes < 60L -> Relative(Unit.MINUTES, minutes)
            minutes < 60L * 24L -> Relative(Unit.HOURS, minutes / 60L)
            else -> Relative(Unit.DAYS, minutes / (60L * 24L))
        }
    }
}
