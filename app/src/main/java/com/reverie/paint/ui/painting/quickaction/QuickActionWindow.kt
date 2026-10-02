/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting.quickaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reverie.paint.R
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.model.QuickAction
import com.reverie.paint.model.QuickActionLayoutMode
import com.reverie.paint.ui.components.pressScale
import com.reverie.paint.ui.theme.Morandi
import com.reverie.paint.ui.theme.Motion
import com.reverie.paint.ui.theme.systemHoverIcon
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlin.math.roundToInt

/**
 * 悬浮快捷操作小窗 (QuickActionWindow)
 * 支持：
 * 1. 自适应胶囊 (单行/单列) 与自适应圆角矩形 (多列网格)
 * 2. 自由拖动、贴边防出界
 * 3. 顶部微触把手一键折叠收缩为半透明悬浮球/微胶囊
 * 4. 高斯毛玻璃背景与透明度
 * 5. 右上角齿轮即时进入动作自定义/排序/模式选择对话框
 */
@Composable
fun QuickActionWindow(
    vm: PaintViewModel,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    opacity: Float = 0.94f,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var showEditDialog by remember { mutableStateOf(false) }

    val config = vm.quickActionsConfig
    val isCollapsed = vm.quickActionCollapsed
    val layoutMode = config.layoutMode
    val activeActions = config.actions

    // 动态圆角：单排为极限胶囊 (CircleShape 或大圆角)，多排为 20dp 圆角矩形，折叠时为小胶囊/圆形
    val windowShape = remember(isCollapsed, layoutMode) {
        if (isCollapsed) {
            RoundedCornerShape(22.dp)
        } else when (layoutMode) {
            QuickActionLayoutMode.ROW, QuickActionLayoutMode.COLUMN -> RoundedCornerShape(24.dp)
            QuickActionLayoutMode.GRID_2, QuickActionLayoutMode.GRID_3 -> RoundedCornerShape(20.dp)
        }
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    vm.quickActionWindowX.roundToInt(),
                    vm.quickActionWindowY.roundToInt(),
                )
            }
            .shadow(12.dp, windowShape)
            .systemHoverIcon(context)
            .clip(windowShape)
            .then(
                if (vm.blurBackground && hazeState != null) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = com.reverie.paint.ui.theme.Glass.popupStyle(if (opacity >= 0.99f) 0.92f else opacity),
                    )
                } else {
                    Modifier.background(Morandi.panel.copy(alpha = opacity))
                }
            )
            .border(1.dp, Morandi.border.copy(alpha = 0.7f), windowShape)
            .animateContentSize(Motion.enterSpring())
    ) {
        if (isCollapsed) {
            // ---- 折叠微缩态 (流线型悬浮小球/胶囊) ----
            Row(
                modifier = Modifier
                    .wrapContentSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            vm.quickActionWindowX += dragAmount.x
                            vm.quickActionWindowY += dragAmount.y
                            vm.persistQuickActionsState()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 展开图标
                val expandSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Morandi.accent.copy(alpha = 0.18f))
                        .pressScale(expandSource, pressedScale = 0.88f)
                        .clickable(
                            interactionSource = expandSource,
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.quickActionCollapsed = false
                            vm.persistQuickActionsState()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shortcut),
                        contentDescription = stringResource(R.string.quick_action_window_title),
                        tint = Morandi.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable {
                            onClose()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_x),
                        contentDescription = "Close",
                        tint = Morandi.subText,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            // ---- 展开完整态 ----
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. 顶部控制栏 (拖拽把手 + 折叠 + 设置 + 关闭)
                Row(
                    modifier = Modifier
                        .then(
                            when (layoutMode) {
                                QuickActionLayoutMode.ROW -> Modifier.widthIn(min = 120.dp)
                                QuickActionLayoutMode.COLUMN -> Modifier.width(44.dp)
                                QuickActionLayoutMode.GRID_2 -> Modifier.width(96.dp)
                                QuickActionLayoutMode.GRID_3 -> Modifier.width(144.dp)
                            }
                        )
                        .height(24.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                vm.quickActionWindowX += dragAmount.x
                                vm.quickActionWindowY += dragAmount.y
                                vm.persistQuickActionsState()
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (layoutMode == QuickActionLayoutMode.COLUMN) {
                        // 单列窄胶囊模式下的极简拖动手柄
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clickable {
                                    vm.quickActionCollapsed = true
                                    vm.persistQuickActionsState()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Morandi.subText.copy(alpha = 0.45f))
                            )
                        }
                    } else {
                        // 横向或网格模式下的微型工具头
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // 折叠按钮
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        vm.quickActionCollapsed = true
                                        vm.persistQuickActionsState()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Morandi.subText)
                                )
                            }

                            // 标题
                            Text(
                                text = stringResource(R.string.quick_action_window_title),
                                color = Morandi.subText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // 编辑齿轮
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { showEditDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = "Edit",
                                    tint = Morandi.subText,
                                    modifier = Modifier.size(13.dp),
                                )
                            }

                            // 关闭
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onClose() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_x),
                                    contentDescription = "Close",
                                    tint = Morandi.subText,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }

                if (layoutMode == QuickActionLayoutMode.COLUMN) {
                    Spacer(Modifier.height(4.dp))
                } else {
                    Spacer(Modifier.height(6.dp))
                }

                // 2. 动作按钮渲染布局
                when (layoutMode) {
                    QuickActionLayoutMode.ROW -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            activeActions.forEach { action ->
                                QuickActionButton(
                                    vm = vm,
                                    action = action,
                                    showLabel = config.showLabels,
                                    onClick = { vm.executeQuickAction(action) },
                                )
                            }
                        }
                    }
                    QuickActionLayoutMode.COLUMN -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            activeActions.forEach { action ->
                                QuickActionButton(
                                    vm = vm,
                                    action = action,
                                    showLabel = config.showLabels,
                                    onClick = { vm.executeQuickAction(action) },
                                )
                            }

                            // 单列底部配置齿轮
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showEditDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = "Edit",
                                    tint = Morandi.subText.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    QuickActionLayoutMode.GRID_2 -> {
                        val chunks = activeActions.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chunks.forEach { rowActions ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rowActions.forEach { action ->
                                        QuickActionButton(
                                            vm = vm,
                                            action = action,
                                            showLabel = config.showLabels,
                                            onClick = { vm.executeQuickAction(action) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    QuickActionLayoutMode.GRID_3 -> {
                        val chunks = activeActions.chunked(3)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chunks.forEach { rowActions ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rowActions.forEach { action ->
                                        QuickActionButton(
                                            vm = vm,
                                            action = action,
                                            showLabel = config.showLabels,
                                            onClick = { vm.executeQuickAction(action) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 自定义快捷操作编辑对话框 ----
    if (showEditDialog) {
        QuickActionsEditDialog(
            vm = vm,
            onDismiss = {
                showEditDialog = false
                vm.persistQuickActionsState()
            },
        )
    }
}

/**
 * 独立的快捷功能按钮
 */
@Composable
private fun QuickActionButton(
    vm: PaintViewModel,
    action: QuickAction,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }

    // 判断该动作当前是否处于高亮/激活态
    val isActivated = when (action) {
        QuickAction.LOCK_VIEW -> vm.isViewTransformLocked
        QuickAction.TOGGLE_ERASER -> vm.currentToolId == "eraser"
        QuickAction.ALPHA_LOCK -> vm.isCurrentLayerAlphaLocked()
        else -> false
    }

    val bg = if (isActivated) Morandi.accent else Morandi.panelHi.copy(alpha = 0.65f)
    val tint = if (isActivated) Color.White else Morandi.text

    if (showLabel) {
        // 图标 + 文字标签
        Column(
            modifier = Modifier
                .width(52.dp)
                .pressScale(source, pressedScale = 0.90f)
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .clickable(interactionSource = source, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = stringResource(action.titleRes),
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(action.titleRes),
                color = if (isActivated) Color.White else Morandi.subText,
                fontSize = 9.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        // 纯高质感图标胶囊
        Box(
            modifier = Modifier
                .size(38.dp)
                .pressScale(source, pressedScale = 0.90f)
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .clickable(interactionSource = source, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = stringResource(action.titleRes),
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 快捷操作自定义配置弹窗
 */
@Composable
private fun QuickActionsEditDialog(
    vm: PaintViewModel,
    onDismiss: () -> Unit,
) {
    var selectedActions by remember { mutableStateOf(vm.quickActionsConfig.actions.toSet()) }
    var layoutMode by remember { mutableStateOf(vm.quickActionsConfig.layoutMode) }
    var showLabels by remember { mutableStateOf(vm.quickActionsConfig.showLabels) }

    Dialog(
        onDismissRequest = {
            vm.quickActionsConfig = vm.quickActionsConfig.copy(
                actions = QuickAction.entries.filter { selectedActions.contains(it) },
                layoutMode = layoutMode,
                showLabels = showLabels,
            )
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable {
                    vm.quickActionsConfig = vm.quickActionsConfig.copy(
                        actions = QuickAction.entries.filter { selectedActions.contains(it) },
                        layoutMode = layoutMode,
                        showLabels = showLabels,
                    )
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Morandi.panel)
                    .border(1.dp, Morandi.border, RoundedCornerShape(20.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.quick_action_edit),
                            color = Morandi.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.quick_action_edit_desc),
                            color = Morandi.subText,
                            fontSize = 12.sp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Morandi.panelHi)
                            .clickable {
                                vm.quickActionsConfig = vm.quickActionsConfig.copy(
                                    actions = QuickAction.entries.filter { selectedActions.contains(it) },
                                    layoutMode = layoutMode,
                                    showLabels = showLabels,
                                )
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_x),
                            contentDescription = "Close",
                            tint = Morandi.text,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 排布样式选择器
                Text(
                    text = "排布样式",
                    color = Morandi.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Morandi.panelHi)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QuickActionLayoutMode.entries.forEach { mode ->
                        val isSelected = layoutMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Morandi.accent else Color.Transparent)
                                .clickable { layoutMode = mode },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(mode.labelRes),
                                color = if (isSelected) Color.White else Morandi.subText,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 显示文字标签切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Morandi.panelHi)
                        .clickable { showLabels = !showLabels }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "显示按钮文本标签",
                        color = Morandi.text,
                        fontSize = 13.sp,
                    )
                    com.reverie.paint.ui.components.ReSwitch(
                        checked = showLabels,
                        onChecked = { showLabels = it },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 可选动作网格勾选列表
                Text(
                    text = "快捷操作列表 (${selectedActions.size}/${QuickAction.entries.size})",
                    color = Morandi.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(QuickAction.entries.size) { i ->
                        val action = QuickAction.entries[i]
                        val isChecked = selectedActions.contains(action)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isChecked) Morandi.accent.copy(alpha = 0.12f) else Morandi.panelHi.copy(alpha = 0.5f))
                                .clickable {
                                    selectedActions = if (isChecked) {
                                        if (selectedActions.size > 1) selectedActions - action else selectedActions
                                    } else {
                                        selectedActions + action
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChecked) Morandi.accent else Morandi.panelHi),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(action.iconRes),
                                        contentDescription = null,
                                        tint = if (isChecked) Color.White else Morandi.text,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(action.titleRes),
                                    color = Morandi.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                                )
                            }

                            if (isChecked) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = "Selected",
                                    tint = Morandi.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 底部操作区 (恢复默认 + 完成)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.quick_action_reset_default),
                        color = Morandi.subText,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                selectedActions = QuickAction.DEFAULT_ACTIONS.toSet()
                                layoutMode = QuickActionLayoutMode.COLUMN
                                showLabels = false
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Morandi.accent)
                            .clickable {
                                vm.quickActionsConfig = vm.quickActionsConfig.copy(
                                    actions = QuickAction.entries.filter { selectedActions.contains(it) },
                                    layoutMode = layoutMode,
                                    showLabels = showLabels,
                                )
                                onDismiss()
                            }
                            .padding(horizontal = 22.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.quick_action_done),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
