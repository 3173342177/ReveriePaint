/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.reverie.paint.core.*
import com.reverie.paint.ui.theme.Motion
import dev.chrisbanes.haze.HazeState

/**
 * Overlay layer hosting persistent floating tool windows:
 * Reference Window, Quick Action Window, Quick Brush Window,
 * Quick Color Window, and Quick Layer Window.
 */
@Composable
internal fun BoxScope.PaintingQuickWindows(
    vm: PaintViewModel,
    hazeState: HazeState,
    onOpenFullLayerPanel: () -> Unit,
) {
    // ---- Reference Window: 打开(false->true 边沿)时居中 ----
    // 面板可自由拖动且无边界钳制, 若保留上次位置, 用户可能把它拖出屏幕后
    // 再也拖不回来。仅"从关闭切换到打开"时回到视图中心; 面板已打开的状态下
    // 进入工程/重组保持原位置不动 (位置不再持久化)。
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    LaunchedEffect(vm.referenceWindowOpen) {
        if (vm.referenceWindowOpen && !vm.referenceWindowWasOpen) {
            val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
            val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
            val panelW = with(density) { vm.referenceWindowWidth.dp.toPx() }
            val panelH = with(density) { vm.referenceWindowHeight.dp.toPx() }
            vm.referenceWindowX = ((screenW - panelW) / 2f).coerceAtLeast(0f)
            vm.referenceWindowY = ((screenH - panelH) / 2f).coerceAtLeast(0f)
        }
        vm.referenceWindowWasOpen = vm.referenceWindowOpen
    }

    // ---- Persistent Floating Reference Window (常态固定显示参考窗口) ----
    AnimatedVisibility(
        visible = vm.referenceWindowOpen,
        enter = fadeIn(Motion.enterSpring()) + androidx.compose.animation.scaleIn(Motion.enterSpring(), initialScale = 0.92f),
        exit = fadeOut(Motion.exitTween(150)) + androidx.compose.animation.scaleOut(Motion.exitTween(150), targetScale = 0.92f),
        modifier = Modifier.zIndex(70f),
    ) {
        ReferenceWindow(
            vm = vm,
            onClose = { vm.referenceWindowOpen = false; vm.persistReferenceState() },
            hazeState = hazeState,
            opacity = vm.popupPanelOpacity,
        )
    }

    // ---- Persistent Floating Quick Action Window (常驻悬浮快捷操作小窗) ----
    AnimatedVisibility(
        visible = vm.quickActionWindowOpen,
        enter = fadeIn(Motion.enterSpring()) + androidx.compose.animation.scaleIn(Motion.enterSpring(), initialScale = 0.92f),
        exit = fadeOut(Motion.exitTween(150)) + androidx.compose.animation.scaleOut(Motion.exitTween(150), targetScale = 0.92f),
        modifier = Modifier.zIndex(75f),
    ) {
        com.reverie.paint.ui.painting.quickaction.QuickActionWindow(
            vm = vm,
            onClose = {
                vm.quickActionWindowOpen = false
                vm.persistQuickActionsState()
            },
            hazeState = hazeState,
            opacity = vm.popupPanelOpacity,
        )
    }

    // ---- Persistent Floating Quick Brush Window (常驻悬浮快捷笔刷小窗) ----
    AnimatedVisibility(
        visible = vm.quickBrushWindowOpen,
        enter = fadeIn(Motion.enterSpring()) + androidx.compose.animation.scaleIn(Motion.enterSpring(), initialScale = 0.92f),
        exit = fadeOut(Motion.exitTween(150)) + androidx.compose.animation.scaleOut(Motion.exitTween(150), targetScale = 0.92f),
        modifier = Modifier.zIndex(76f),
    ) {
        com.reverie.paint.ui.painting.quickbrush.QuickBrushWindow(
            vm = vm,
            onClose = {
                vm.quickBrushWindowOpen = false
                vm.persistQuickBrushState()
            },
            hazeState = hazeState,
            opacity = vm.popupPanelOpacity,
        )
    }

    // ---- Persistent Floating Quick Color Window (常驻悬浮快捷颜色小窗) ----
    AnimatedVisibility(
        visible = vm.quickColorWindowOpen,
        enter = fadeIn(Motion.enterSpring()) + androidx.compose.animation.scaleIn(Motion.enterSpring(), initialScale = 0.92f),
        exit = fadeOut(Motion.exitTween(150)) + androidx.compose.animation.scaleOut(Motion.exitTween(150), targetScale = 0.92f),
        modifier = Modifier.zIndex(77f),
    ) {
        com.reverie.paint.ui.painting.quickcolor.QuickColorWindow(
            vm = vm,
            onClose = {
                vm.quickColorWindowOpen = false
                vm.persistQuickColorState()
            },
            hazeState = hazeState,
            opacity = vm.popupPanelOpacity,
        )
    }

    // ---- Persistent Floating Quick Layer Window (常驻悬浮快捷图层小窗) ----
    AnimatedVisibility(
        visible = vm.quickLayerWindowOpen,
        enter = fadeIn(Motion.enterSpring()) + androidx.compose.animation.scaleIn(Motion.enterSpring(), initialScale = 0.92f),
        exit = fadeOut(Motion.exitTween(150)) + androidx.compose.animation.scaleOut(Motion.exitTween(150), targetScale = 0.92f),
        modifier = Modifier.zIndex(78f),
    ) {
        com.reverie.paint.ui.painting.quicklayer.QuickLayerWindow(
            vm = vm,
            onClose = {
                vm.quickLayerWindowOpen = false
                vm.persistQuickLayerState()
            },
            onOpenFullLayerPanel = onOpenFullLayerPanel,
            hazeState = hazeState,
            opacity = vm.popupPanelOpacity,
        )
    }
}
