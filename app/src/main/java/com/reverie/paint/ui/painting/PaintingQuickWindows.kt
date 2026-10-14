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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onOpenLayerDetails: (Long) -> Unit,
) {
    // ---- Reference Window: 打开(false->true 边沿)时居中 ----
    // 面板可自由拖动且无边界钳制, 若保留上次位置, 用户可能把它拖出屏幕后
    // 再也拖不回来。仅"从关闭切换到打开"时回到视图中心; 面板已打开的状态下
    // 进入工程/重组保持原位置不动 (位置不再持久化)。
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }

    var lastScreenW by remember { mutableFloatStateOf(screenW) }
    var lastScreenH by remember { mutableFloatStateOf(screenH) }

    // 仅在横竖屏切换/屏幕旋转的一瞬间，若小窗坐标已超出当前屏幕可视区域，才将其推回边界内
    LaunchedEffect(screenW, screenH) {
        if (screenW <= 0f || screenH <= 0f) return@LaunchedEffect
        val isScreenRotated = lastScreenW > 0f && lastScreenH > 0f &&
            (lastScreenW != screenW || lastScreenH != screenH)
        lastScreenW = screenW
        lastScreenH = screenH

        if (!isScreenRotated) return@LaunchedEffect

        // 1. Reference Window
        if (vm.referenceWindowOpen && vm.referenceWindowX > 0f) {
            val panelW = with(density) { vm.referenceWindowWidth.dp.toPx() }
            val panelH = with(density) { vm.referenceWindowHeight.dp.toPx() }
            val maxX = (screenW - panelW).coerceAtLeast(0f)
            val maxY = (screenH - panelH).coerceAtLeast(0f)
            if (vm.referenceWindowX > maxX) vm.referenceWindowX = maxX
            if (vm.referenceWindowY > maxY) vm.referenceWindowY = maxY
        }

        // 2. Quick Action Window
        if (vm.quickActionWindowOpen && vm.quickActionWindowX > 0f) {
            val panelW = if (vm.quickActionWindowWidth > 0f) vm.quickActionWindowWidth else with(density) { 200.dp.toPx() }
            val panelH = if (vm.quickActionWindowHeight > 0f) vm.quickActionWindowHeight else with(density) { 160.dp.toPx() }
            val maxX = (screenW - panelW).coerceAtLeast(0f)
            val maxY = (screenH - panelH).coerceAtLeast(0f)
            var changed = false
            if (vm.quickActionWindowX > maxX) {
                vm.quickActionWindowX = maxX
                changed = true
            }
            if (vm.quickActionWindowY > maxY) {
                vm.quickActionWindowY = maxY
                changed = true
            }
            if (changed) vm.persistQuickActionsState()
        }

        // 3. Quick Brush Window
        if (vm.quickBrushWindowOpen && vm.quickBrushWindowX > 0f) {
            val panelW = if (vm.quickBrushWindowWidth > 0f) vm.quickBrushWindowWidth else with(density) { 240.dp.toPx() }
            val panelH = if (vm.quickBrushWindowHeight > 0f) vm.quickBrushWindowHeight else with(density) { 54.dp.toPx() }
            val maxX = (screenW - panelW).coerceAtLeast(0f)
            val maxY = (screenH - panelH).coerceAtLeast(0f)
            var changed = false
            if (vm.quickBrushWindowX > maxX) {
                vm.quickBrushWindowX = maxX
                changed = true
            }
            if (vm.quickBrushWindowY > maxY) {
                vm.quickBrushWindowY = maxY
                changed = true
            }
            if (changed) vm.persistQuickBrushState()
        }

        // 4. Quick Color Window
        if (vm.quickColorWindowOpen && vm.quickColorWindowX > 0f) {
            val panelW = if (vm.quickColorWindowWidth > 0f) vm.quickColorWindowWidth else with(density) { 212.dp.toPx() }
            val panelH = if (vm.quickColorWindowHeight > 0f) vm.quickColorWindowHeight else with(density) { 272.dp.toPx() }
            val maxX = (screenW - panelW).coerceAtLeast(0f)
            val maxY = (screenH - panelH).coerceAtLeast(0f)
            var changed = false
            if (vm.quickColorWindowX > maxX) {
                vm.quickColorWindowX = maxX
                changed = true
            }
            if (vm.quickColorWindowY > maxY) {
                vm.quickColorWindowY = maxY
                changed = true
            }
            if (changed) vm.persistQuickColorState()
        }

        // 5. Quick Layer Window
        if (vm.quickLayerWindowOpen && vm.quickLayerWindowX > 0f) {
            val panelW = if (vm.quickLayerWindowWidth > 0f) vm.quickLayerWindowWidth else with(density) { 156.dp.toPx() }
            val panelH = if (vm.quickLayerWindowHeight > 0f) vm.quickLayerWindowHeight else with(density) { 290.dp.toPx() }
            val maxX = (screenW - panelW).coerceAtLeast(0f)
            val maxY = (screenH - panelH).coerceAtLeast(0f)
            var changed = false
            if (vm.quickLayerWindowX > maxX) {
                vm.quickLayerWindowX = maxX
                changed = true
            }
            if (vm.quickLayerWindowY > maxY) {
                vm.quickLayerWindowY = maxY
                changed = true
            }
            if (changed) vm.persistQuickLayerState()
        }
    }

    LaunchedEffect(vm.referenceWindowOpen) {
        if (vm.referenceWindowOpen && !vm.referenceWindowWasOpen) {
            val panelW = with(density) { vm.referenceWindowWidth.dp.toPx() }
            val panelH = with(density) { vm.referenceWindowHeight.dp.toPx() }
            vm.referenceWindowX = ((screenW - panelW) / 2f).coerceAtLeast(0f)
            vm.referenceWindowY = ((screenH - panelH) / 2f).coerceAtLeast(0f)
        }
        vm.referenceWindowWasOpen = vm.referenceWindowOpen
    }

    LaunchedEffect(vm.quickActionWindowOpen) {
        if (vm.quickActionWindowOpen && !vm.quickActionWindowWasOpen) {
            val panelW = if (vm.quickActionWindowWidth > 0f) vm.quickActionWindowWidth else with(density) { 200.dp.toPx() }
            val panelH = if (vm.quickActionWindowHeight > 0f) vm.quickActionWindowHeight else with(density) { 160.dp.toPx() }
            vm.quickActionWindowX = ((screenW - panelW) / 2f).coerceAtLeast(0f)
            vm.quickActionWindowY = ((screenH - panelH) / 2f).coerceAtLeast(0f)
            vm.persistQuickActionsState()
        }
        vm.quickActionWindowWasOpen = vm.quickActionWindowOpen
    }

    LaunchedEffect(vm.quickBrushWindowOpen) {
        if (vm.quickBrushWindowOpen && !vm.quickBrushWindowWasOpen) {
            val panelW = if (vm.quickBrushWindowWidth > 0f) vm.quickBrushWindowWidth else with(density) { 240.dp.toPx() }
            val panelH = if (vm.quickBrushWindowHeight > 0f) vm.quickBrushWindowHeight else with(density) { 54.dp.toPx() }
            vm.quickBrushWindowX = ((screenW - panelW) / 2f).coerceAtLeast(0f)
            vm.quickBrushWindowY = ((screenH - panelH) / 2f).coerceAtLeast(0f)
            vm.persistQuickBrushState()
        }
        vm.quickBrushWindowWasOpen = vm.quickBrushWindowOpen
    }

    LaunchedEffect(vm.quickColorWindowOpen) {
        if (vm.quickColorWindowOpen && !vm.quickColorWindowWasOpen) {
            val panelW = if (vm.quickColorWindowWidth > 0f) vm.quickColorWindowWidth else with(density) { 212.dp.toPx() }
            val panelH = if (vm.quickColorWindowHeight > 0f) vm.quickColorWindowHeight else with(density) { 272.dp.toPx() }
            vm.quickColorWindowX = ((screenW - panelW) / 2f).coerceAtLeast(0f)
            vm.quickColorWindowY = ((screenH - panelH) / 2f).coerceAtLeast(0f)
            vm.persistQuickColorState()
        }
        vm.quickColorWindowWasOpen = vm.quickColorWindowOpen
    }

    LaunchedEffect(vm.quickLayerWindowOpen) {
        if (vm.quickLayerWindowOpen && !vm.quickLayerWindowWasOpen) {
            val panelW = if (vm.quickLayerWindowWidth > 0f) vm.quickLayerWindowWidth else with(density) { 156.dp.toPx() }
            val panelH = if (vm.quickLayerWindowHeight > 0f) vm.quickLayerWindowHeight else with(density) { 290.dp.toPx() }
            vm.quickLayerWindowX = ((screenW - panelW) / 2f).coerceAtLeast(0f)
            vm.quickLayerWindowY = ((screenH - panelH) / 2f).coerceAtLeast(0f)
            vm.persistQuickLayerState()
        }
        vm.quickLayerWindowWasOpen = vm.quickLayerWindowOpen
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
            onOpenLayerDetails = onOpenLayerDetails,
            hazeState = hazeState,
            opacity = vm.popupPanelOpacity,
        )
    }
}
