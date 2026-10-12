/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.reverie.paint.R
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.model.PanelAlignment
import com.reverie.paint.model.PanelEdge
import com.reverie.paint.model.PanelPosition
import com.reverie.paint.ui.components.ReButton
import com.reverie.paint.ui.components.ReSlider
import com.reverie.paint.ui.theme.Theme
import com.reverie.paint.ui.theme.glassBorder
import kotlin.math.roundToInt

private enum class DragTargetPanel {
    NONE,
    TOPBAR,
    TOOLRAIL,
    SLIDERRAIL,
}

/**
 * 悬浮模式可视化排布与尺寸调整对话框 (平板专业分栏设计)
 */
@Composable
fun FloatingLayoutConfigDialog(
    vm: PaintViewModel,
    onDismiss: () -> Unit,
) {
    val colors = Theme.current
    var topPos by remember { mutableStateOf(vm.floatingUiConfig.topBarPosition) }
    var toolPos by remember { mutableStateOf(vm.floatingUiConfig.toolRailPosition) }
    var sliderPos by remember { mutableStateOf(vm.floatingUiConfig.sliderRailPosition) }
    var marginDp by remember { mutableFloatStateOf(vm.floatingUiConfig.marginDp.toFloat()) }
    var floatingScale by remember { mutableFloatStateOf(vm.floatingUiConfig.scale) }

    var selectedPanel by remember { mutableStateOf(DragTargetPanel.TOPBAR) }
    var draggingPanel by remember { mutableStateOf(DragTargetPanel.NONE) }
    var dragGlobalPos by remember { mutableStateOf<Offset?>(null) }
    var slotBoundsMap by remember { mutableStateOf(mapOf<PanelPosition, Rect>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 880.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.panel)
                    .glassBorder(RoundedCornerShape(24.dp))
                    .padding(24.dp),
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent)
                            )
                            Text(
                                text = stringResource(R.string.panel_dock_dialog_title),
                                color = colors.text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.panel_dock_drag_tip),
                            color = colors.subText,
                            fontSize = 12.sp,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReButton(
                            text = stringResource(R.string.panel_dock_reset),
                            primary = false,
                            onClick = {
                                topPos = PanelPosition(PanelEdge.TOP, PanelAlignment.CENTER)
                                toolPos = PanelPosition(PanelEdge.LEFT, PanelAlignment.START)
                                sliderPos = PanelPosition(PanelEdge.LEFT, PanelAlignment.END)
                                marginDp = 12f
                                floatingScale = 1.0f
                                selectedPanel = DragTargetPanel.TOPBAR
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 2. Main Content: Left Tablet Mockup vs Right Settings Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Left: Realistic Tablet Mockup Preview
                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.panelHi.copy(alpha = 0.35f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Tablet Device Shell
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            colors.canvasBg.copy(alpha = 0.85f),
                                            colors.panelHi.copy(alpha = 0.6f),
                                        )
                                    )
                                ),
                        ) {
                            // Center Canvas Sheet Placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.66f)
                                    .align(Alignment.Center)
                                    .shadow(4.dp, RoundedCornerShape(8.dp), spotColor = Color.Black.copy(alpha = 0.12f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.panel.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_canvas_tab),
                                        contentDescription = null,
                                        tint = colors.subText.copy(alpha = 0.35f),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.panel_tab_canvas),
                                        color = colors.subText.copy(alpha = 0.4f),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            // 12 Dock Slots & Draggable Capsules
                            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                val allSlots = remember {
                                    val list = mutableListOf<PanelPosition>()
                                    PanelEdge.entries.forEach { edge ->
                                        PanelAlignment.entries.forEach { align ->
                                            list.add(PanelPosition(edge, align))
                                        }
                                    }
                                    list
                                }

                                allSlots.forEach { slot ->
                                    val isSlotOccupied = slot == topPos || slot == toolPos || slot == sliderPos
                                    val isDropHovered = draggingPanel != DragTargetPanel.NONE &&
                                        dragGlobalPos?.let { pos ->
                                            slotBoundsMap[slot]?.contains(pos) == true
                                        } == true

                                    val slotModifier = resolveSlotAlignmentModifier(slot.edge, slot.alignment)
                                    val slotShape = RoundedCornerShape(8.dp)

                                    val slotBg by animateColorAsState(
                                        targetValue = when {
                                            isDropHovered -> colors.accent.copy(alpha = 0.4f)
                                            isSlotOccupied -> colors.accent.copy(alpha = 0.1f)
                                            else -> colors.panel.copy(alpha = 0.25f)
                                        },
                                        label = "slotBg"
                                    )
                                    val slotBorderColor by animateColorAsState(
                                        targetValue = when {
                                            isDropHovered -> colors.accent
                                            isSlotOccupied -> colors.accent.copy(alpha = 0.3f)
                                            else -> colors.border.copy(alpha = 0.35f)
                                        },
                                        label = "slotBorder"
                                    )

                                    Box(
                                        modifier = slotModifier
                                            .onGloballyPositioned { coords ->
                                                slotBoundsMap = slotBoundsMap + (slot to coords.boundsInRoot())
                                            }
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    // Tap to quickly place selected panel
                                                    when (selectedPanel) {
                                                        DragTargetPanel.TOPBAR -> topPos = slot
                                                        DragTargetPanel.TOOLRAIL -> toolPos = slot
                                                        DragTargetPanel.SLIDERRAIL -> sliderPos = slot
                                                        DragTargetPanel.NONE -> {}
                                                    }
                                                }
                                            )
                                            .width(if (slot.edge.isHorizontal) 84.dp else 38.dp)
                                            .height(if (slot.edge.isHorizontal) 30.dp else 64.dp)
                                            .clip(slotShape)
                                            .background(slotBg)
                                            .then(
                                                if (isDropHovered) {
                                                    Modifier.border(1.5.dp, colors.accent, slotShape)
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!isSlotOccupied) {
                                            Text(
                                                text = when (slot.alignment) {
                                                    PanelAlignment.START -> if (slot.edge.isHorizontal) "左" else "上"
                                                    PanelAlignment.CENTER -> "中"
                                                    PanelAlignment.END -> if (slot.edge.isHorizontal) "右" else "下"
                                                },
                                                color = if (isDropHovered) colors.accent else colors.subText.copy(alpha = 0.45f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }

                                // 3 Draggable Capsules
                                DraggablePanelCapsule(
                                    title = stringResource(R.string.panel_dock_topbar),
                                    icon = R.drawable.ic_canvas_tab,
                                    pos = topPos,
                                    isSelected = selectedPanel == DragTargetPanel.TOPBAR,
                                    isDragging = draggingPanel == DragTargetPanel.TOPBAR,
                                    onSelect = { selectedPanel = DragTargetPanel.TOPBAR },
                                    onDragStart = {
                                        selectedPanel = DragTargetPanel.TOPBAR
                                        draggingPanel = DragTargetPanel.TOPBAR
                                    },
                                    onDrag = { global -> dragGlobalPos = global },
                                    onDragEnd = {
                                        dragGlobalPos?.let { pos ->
                                            slotBoundsMap.entries.firstOrNull { it.value.contains(pos) }?.key?.let { targetSlot ->
                                                topPos = targetSlot
                                            }
                                        }
                                        draggingPanel = DragTargetPanel.NONE
                                        dragGlobalPos = null
                                    },
                                )

                                DraggablePanelCapsule(
                                    title = stringResource(R.string.panel_dock_toolrail),
                                    icon = R.drawable.ic_brush,
                                    pos = toolPos,
                                    isSelected = selectedPanel == DragTargetPanel.TOOLRAIL,
                                    isDragging = draggingPanel == DragTargetPanel.TOOLRAIL,
                                    onSelect = { selectedPanel = DragTargetPanel.TOOLRAIL },
                                    onDragStart = {
                                        selectedPanel = DragTargetPanel.TOOLRAIL
                                        draggingPanel = DragTargetPanel.TOOLRAIL
                                    },
                                    onDrag = { global -> dragGlobalPos = global },
                                    onDragEnd = {
                                        dragGlobalPos?.let { pos ->
                                            slotBoundsMap.entries.firstOrNull { it.value.contains(pos) }?.key?.let { targetSlot ->
                                                toolPos = targetSlot
                                            }
                                        }
                                        draggingPanel = DragTargetPanel.NONE
                                        dragGlobalPos = null
                                    },
                                )

                                DraggablePanelCapsule(
                                    title = stringResource(R.string.panel_dock_sliderrail),
                                    icon = R.drawable.ic_eye,
                                    pos = sliderPos,
                                    isSelected = selectedPanel == DragTargetPanel.SLIDERRAIL,
                                    isDragging = draggingPanel == DragTargetPanel.SLIDERRAIL,
                                    onSelect = { selectedPanel = DragTargetPanel.SLIDERRAIL },
                                    onDragStart = {
                                        selectedPanel = DragTargetPanel.SLIDERRAIL
                                        draggingPanel = DragTargetPanel.SLIDERRAIL
                                    },
                                    onDrag = { global -> dragGlobalPos = global },
                                    onDragEnd = {
                                        dragGlobalPos?.let { pos ->
                                            slotBoundsMap.entries.firstOrNull { it.value.contains(pos) }?.key?.let { targetSlot ->
                                                sliderPos = targetSlot
                                            }
                                        }
                                        draggingPanel = DragTargetPanel.NONE
                                        dragGlobalPos = null
                                    },
                                )
                            }
                        }
                    }

                    // Right: Presets & Controls Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Quick Presets
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.panelHi.copy(alpha = 0.5f))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.panel_dock_preset_title),
                                color = colors.text,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PresetChip(
                                    title = stringResource(R.string.panel_dock_preset_default),
                                    onClick = {
                                        topPos = PanelPosition(PanelEdge.TOP, PanelAlignment.CENTER)
                                        toolPos = PanelPosition(PanelEdge.LEFT, PanelAlignment.START)
                                        sliderPos = PanelPosition(PanelEdge.LEFT, PanelAlignment.END)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                PresetChip(
                                    title = stringResource(R.string.panel_dock_preset_righthand),
                                    onClick = {
                                        topPos = PanelPosition(PanelEdge.TOP, PanelAlignment.CENTER)
                                        toolPos = PanelPosition(PanelEdge.RIGHT, PanelAlignment.START)
                                        sliderPos = PanelPosition(PanelEdge.RIGHT, PanelAlignment.END)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            PresetChip(
                                title = stringResource(R.string.panel_dock_preset_bottom),
                                onClick = {
                                    topPos = PanelPosition(PanelEdge.TOP, PanelAlignment.CENTER)
                                    toolPos = PanelPosition(PanelEdge.BOTTOM, PanelAlignment.START)
                                    sliderPos = PanelPosition(PanelEdge.BOTTOM, PanelAlignment.END)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Margin Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.panelHi.copy(alpha = 0.5f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.panel_dock_margin_title),
                                    color = colors.text,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${marginDp.roundToInt()} dp",
                                    color = colors.accent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.panel_dock_margin_desc),
                                color = colors.subText,
                                fontSize = 10.5.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            ReSlider(
                                value = ((marginDp - 8f) / (48f - 8f)).coerceIn(0f, 1f),
                                onValue = { frac ->
                                    marginDp = 8f + frac * (48f - 8f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Scale Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.panelHi.copy(alpha = 0.5f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.panel_dock_scale_title),
                                    color = colors.text,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${(floatingScale * 100).roundToInt()}%",
                                    color = colors.accent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.panel_dock_scale_desc),
                                color = colors.subText,
                                fontSize = 10.5.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            ReSlider(
                                value = ((floatingScale - 0.70f) / (1.40f - 0.70f)).coerceIn(0f, 1f),
                                onValue = { frac ->
                                    floatingScale = 0.70f + frac * 0.70f
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 3. Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReButton(
                        text = stringResource(R.string.common_cancel),
                        primary = false,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(10.dp))
                    ReButton(
                        text = stringResource(R.string.common_confirm),
                        primary = true,
                        onClick = {
                            vm.updateFloatingUiPositions(
                                topBarPosition = topPos,
                                toolRailPosition = toolPos,
                                sliderRailPosition = sliderPos,
                            )
                            vm.updateFloatingUiMargin(marginDp.roundToInt())
                            vm.updateFloatingUiScale(floatingScale)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Theme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.panel.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.DraggablePanelCapsule(
    title: String,
    icon: Int,
    pos: PanelPosition,
    isSelected: Boolean,
    isDragging: Boolean,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = Theme.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var currentGlobalBounds by remember { mutableStateOf<Rect?>(null) }

    val slotModifier = resolveSlotAlignmentModifier(pos.edge, pos.alignment)
    val isH = pos.edge.isHorizontal

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else if (isSelected) 6.dp else 2.dp,
        label = "capsuleElevation"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "capsuleScale"
    )

    Box(
        modifier = slotModifier
            .onGloballyPositioned { coords ->
                currentGlobalBounds = coords.boundsInRoot()
            }
            .offset {
                if (isDragging) {
                    IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                } else {
                    IntOffset.Zero
                }
            }
            .zIndex(if (isDragging) 100f else if (isSelected) 20f else 10f)
            .pointerInput(pos) {
                detectDragGestures(
                    onDragStart = {
                        dragOffset = Offset.Zero
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        currentGlobalBounds?.let { b ->
                            onDrag(Offset(b.center.x + dragOffset.x, b.center.y + dragOffset.y))
                        }
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        onDragEnd()
                    },
                )
            }
            .clickable(onClick = onSelect)
            .width(if (isH) 84.dp else 38.dp)
            .height(if (isH) 30.dp else 64.dp)
            .shadow(elevation, RoundedCornerShape(8.dp), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isSelected || isDragging) {
                        listOf(colors.accent, colors.accent.copy(alpha = 0.88f))
                    } else {
                        listOf(colors.panelHi, colors.panel)
                    }
                )
            )
            .then(
                if (isSelected || isDragging) {
                    Modifier.border(1.2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                } else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = if (isSelected || isDragging) Color.White else colors.text
        val iconColor = if (isSelected || isDragging) Color.White else colors.accent

        if (isH) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = title.substringBefore(" "),
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = title.substringBefore(" "),
                    color = textColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.resolveSlotAlignmentModifier(
    edge: PanelEdge,
    alignment: PanelAlignment,
): Modifier {
    val align = when (edge) {
        PanelEdge.TOP -> {
            when (alignment) {
                PanelAlignment.START -> Alignment.TopStart
                PanelAlignment.CENTER -> Alignment.TopCenter
                PanelAlignment.END -> Alignment.TopEnd
            }
        }
        PanelEdge.BOTTOM -> {
            when (alignment) {
                PanelAlignment.START -> Alignment.BottomStart
                PanelAlignment.CENTER -> Alignment.BottomCenter
                PanelAlignment.END -> Alignment.BottomEnd
            }
        }
        PanelEdge.LEFT -> {
            when (alignment) {
                PanelAlignment.START -> Alignment.TopStart
                PanelAlignment.CENTER -> Alignment.CenterStart
                PanelAlignment.END -> Alignment.BottomStart
            }
        }
        PanelEdge.RIGHT -> {
            when (alignment) {
                PanelAlignment.START -> Alignment.TopEnd
                PanelAlignment.CENTER -> Alignment.CenterEnd
                PanelAlignment.END -> Alignment.BottomEnd
            }
        }
    }

    val offsetModifier = when (edge) {
        PanelEdge.TOP -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(start = 10.dp, top = 6.dp)
                PanelAlignment.CENTER -> Modifier.padding(top = 6.dp)
                PanelAlignment.END -> Modifier.padding(end = 10.dp, top = 6.dp)
            }
        }
        PanelEdge.BOTTOM -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(start = 10.dp, bottom = 6.dp)
                PanelAlignment.CENTER -> Modifier.padding(bottom = 6.dp)
                PanelAlignment.END -> Modifier.padding(end = 10.dp, bottom = 6.dp)
            }
        }
        PanelEdge.LEFT -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(start = 6.dp, top = 10.dp)
                PanelAlignment.CENTER -> Modifier.padding(start = 6.dp)
                PanelAlignment.END -> Modifier.padding(start = 6.dp, bottom = 10.dp)
            }
        }
        PanelEdge.RIGHT -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(end = 6.dp, top = 10.dp)
                PanelAlignment.CENTER -> Modifier.padding(end = 6.dp)
                PanelAlignment.END -> Modifier.padding(end = 6.dp, bottom = 10.dp)
            }
        }
    }

    return Modifier.align(align).then(offsetModifier)
}
