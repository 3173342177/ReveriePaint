/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import androidx.annotation.StringRes
import com.reverie.paint.R

/**
 * 屏幕边缘停靠方位
 */
enum class PanelEdge(@get:StringRes val labelRes: Int) {
    TOP(R.string.panel_edge_top),
    BOTTOM(R.string.panel_edge_bottom),
    LEFT(R.string.panel_edge_left),
    RIGHT(R.string.panel_edge_right);

    val isHorizontal: Boolean
        get() = this == TOP || this == BOTTOM

    val isVertical: Boolean
        get() = this == LEFT || this == RIGHT
}

/**
 * 面板在所属边缘的具体对齐位置 (上中下 或 左中右)
 */
enum class PanelAlignment(@get:StringRes val labelRes: Int) {
    START(R.string.panel_align_start),
    CENTER(R.string.panel_align_center),
    END(R.string.panel_align_end);
}

/**
 * 面板停靠位置 (边缘 + 对齐)
 */
data class PanelPosition(
    val edge: PanelEdge,
    val alignment: PanelAlignment = PanelAlignment.CENTER,
)

/**
 * 绘画界面悬浮面板停靠状态
 */
data class FloatingUiConfig(
    val enabled: Boolean = false,
    val topBarPosition: PanelPosition = PanelPosition(PanelEdge.TOP, PanelAlignment.END),
    val toolRailPosition: PanelPosition = PanelPosition(PanelEdge.LEFT, PanelAlignment.START),
    val sliderRailPosition: PanelPosition = PanelPosition(PanelEdge.LEFT, PanelAlignment.END),
    val marginDp: Int = 12,
    val scale: Float = 1.0f,
    val topBarActions: List<QuickAction> = emptyList(),
    val panelOrder: List<String> = DEFAULT_PANEL_ORDER,
) {
    companion object {
        val DEFAULT_PANEL_ORDER = listOf("TOOLRAIL", "SLIDERRAIL", "TOPBAR")
    }
}
