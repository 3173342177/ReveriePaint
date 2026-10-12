/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.reverie.paint.ui.theme.Morandi
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
 * 悬浮模式可视化拖拽排布与边距调整对话框
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
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.panel)
                    .glassBorder(RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.panel_dock_dialog_title),
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.panel_dock_dialog_tip),
                            color = colors.subText,
                            fontSize = 12.sp,
                        )
                    }

                    // Reset button
                    ReButton(
                        text = stringResource(R.string.panel_dock_reset),
                        primary = false,
                        onClick = {
                            topPos = PanelPosition(PanelEdge.TOP, PanelAlignment.CENTER)
                            toolPos = PanelPosition(PanelEdge.LEFT, PanelAlignment.START)
                            sliderPos = PanelPosition(PanelEdge.LEFT, PanelAlignment.END)
                            marginDp = 12f
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Interactive Tablet Mockup Canvas
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.panelHi.copy(alpha = 0.6f))
                        .border(1.5.dp, colors.border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    val allSlots = remember {
                        val list = mutableListOf<PanelPosition>()
                        PanelEdge.entries.forEach { edge ->
                            PanelAlignment.entries.forEach { align ->
                                list.add(PanelPosition(edge, align))
                            }
                        }
                        list
                    }

                    // Render 12 Drop Slots (3 per edge)
                    allSlots.forEach { slot ->
                        val isSlotOccupied = slot == topPos || slot == toolPos || slot == sliderPos
                        val isHighlighted = draggingPanel != DragTargetPanel.NONE &&
                            dragGlobalPos?.let { pos ->
                                slotBoundsMap[slot]?.contains(pos) == true
                            } == true

                        val slotModifier = resolveSlotAlignmentModifier(slot.edge, slot.alignment)
                        val slotShape = RoundedCornerShape(10.dp)

                        Box(
                            modifier = slotModifier
                                .onGloballyPositioned { coords ->
                                    slotBoundsMap = slotBoundsMap + (slot to coords.boundsInRoot())
                                }
                                .width(if (slot.edge.isHorizontal) 92.dp else 46.dp)
                                .height(if (slot.edge.isHorizontal) 36.dp else 72.dp)
                                .clip(slotShape)
                                .background(
                                    when {
                                        isHighlighted -> colors.accent.copy(alpha = 0.35f)
                                        isSlotOccupied -> colors.accent.copy(alpha = 0.12f)
                                        else -> colors.panel.copy(alpha = 0.4f)
                                    }
                                )
                                .border(
                                    width = if (isHighlighted) 2.dp else 1.dp,
                                    color = when {
                                        isHighlighted -> colors.accent
                                        isSlotOccupied -> colors.accent.copy(alpha = 0.5f)
                                        else -> colors.border.copy(alpha = 0.5f)
                                    },
                                    shape = slotShape,
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
                                    color = colors.subText.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    // Render the 3 Draggable Panel Capsules
                    // 1. TopBar Capsule
                    DraggablePanelCapsule(
                        title = stringResource(R.string.panel_dock_topbar),
                        icon = R.drawable.ic_canvas_tab,
                        pos = topPos,
                        isDragging = draggingPanel == DragTargetPanel.TOPBAR,
                        onDragStart = { draggingPanel = DragTargetPanel.TOPBAR },
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

                    // 2. ToolRail Capsule
                    DraggablePanelCapsule(
                        title = stringResource(R.string.panel_dock_toolrail),
                        icon = R.drawable.ic_brush,
                        pos = toolPos,
                        isDragging = draggingPanel == DragTargetPanel.TOOLRAIL,
                        onDragStart = { draggingPanel = DragTargetPanel.TOOLRAIL },
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

                    // 3. SliderRail Capsule
                    DraggablePanelCapsule(
                        title = stringResource(R.string.panel_dock_sliderrail),
                        icon = R.drawable.ic_eye,
                        pos = sliderPos,
                        isDragging = draggingPanel == DragTargetPanel.SLIDERRAIL,
                        onDragStart = { draggingPanel = DragTargetPanel.SLIDERRAIL },
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

                Spacer(Modifier.height(16.dp))

                // Margin Slider (8dp ~ 48dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.panelHi.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.panel_dock_margin_title),
                            color = colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${marginDp.roundToInt()} dp",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.panel_dock_margin_desc),
                        color = colors.subText,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    ReSlider(
                        value = ((marginDp - 8f) / (48f - 8f)).coerceIn(0f, 1f),
                        onValue = { frac ->
                            marginDp = 8f + frac * (48f - 8f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Footer Actions
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
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.DraggablePanelCapsule(
    title: String,
    icon: Int,
    pos: PanelPosition,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = Theme.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var currentGlobalBounds by remember { mutableStateOf<Rect?>(null) }

    val slotModifier = resolveSlotAlignmentModifier(pos.edge, pos.alignment)
    val isH = pos.edge.isHorizontal

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
            .zIndex(if (isDragging) 100f else 10f)
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
            .width(if (isH) 92.dp else 46.dp)
            .height(if (isH) 36.dp else 72.dp)
            .shadow(if (isDragging) 12.dp else 4.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(colors.accent.copy(alpha = if (isDragging) 0.95f else 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isH) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title.substringBefore(" "),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title.substringBefore(" "),
                    color = Color.White,
                    fontSize = 10.sp,
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
                PanelAlignment.START -> Modifier.padding(start = 12.dp, top = 8.dp)
                PanelAlignment.CENTER -> Modifier.padding(top = 8.dp)
                PanelAlignment.END -> Modifier.padding(end = 12.dp, top = 8.dp)
            }
        }
        PanelEdge.BOTTOM -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(start = 12.dp, bottom = 8.dp)
                PanelAlignment.CENTER -> Modifier.padding(bottom = 8.dp)
                PanelAlignment.END -> Modifier.padding(end = 12.dp, bottom = 8.dp)
            }
        }
        PanelEdge.LEFT -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(start = 8.dp, top = 12.dp)
                PanelAlignment.CENTER -> Modifier.padding(start = 8.dp)
                PanelAlignment.END -> Modifier.padding(start = 8.dp, bottom = 12.dp)
            }
        }
        PanelEdge.RIGHT -> {
            when (alignment) {
                PanelAlignment.START -> Modifier.padding(end = 8.dp, top = 12.dp)
                PanelAlignment.CENTER -> Modifier.padding(end = 8.dp)
                PanelAlignment.END -> Modifier.padding(end = 8.dp, bottom = 12.dp)
            }
        }
    }

    return Modifier.align(align).then(offsetModifier)
}
