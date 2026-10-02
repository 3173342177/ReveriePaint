/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting.quickbrush

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reverie.paint.R
import com.reverie.paint.core.BrushPresetInfo
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.ui.components.ReTextButton
import com.reverie.paint.ui.painting.brush.rememberPresetThumb
import com.reverie.paint.ui.theme.Theme

@Composable
fun QuickBrushManageDialog(
    vm: PaintViewModel,
    onDismiss: () -> Unit,
) {
    val colors = Theme.current
    val favoritePresets = vm.getOrderedFavoriteBrushes()
    var selectedGroup by remember { mutableStateOf("全部") }

    val allGroups = remember(vm.brushPresets) {
        val groups = vm.brushPresets.map { it.group.ifBlank { "未分组" } }.distinct().toMutableList()
        groups.remove("全部")
        groups.remove("常用")
        groups.remove("最近")
        listOf("全部") + groups
    }

    val libraryPresets = remember(selectedGroup, vm.brushPresets) {
        if (selectedGroup == "全部") {
            vm.brushPresets
        } else {
            vm.brushPresets.filter { it.group == selectedGroup || (selectedGroup == "未分组" && it.group.isBlank()) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.panel)
                .border(1.dp, colors.panelHi, RoundedCornerShape(24.dp))
                .padding(22.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.quick_brush_manage_title),
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.quick_brush_manage_desc),
                            color = colors.subText,
                            fontSize = 12.sp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.panelHi)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = colors.icon,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 排列方向切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.panelHi)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val isHoriz = vm.quickBrushOrientation == "horizontal"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isHoriz) colors.accent.copy(alpha = 0.22f) else colors.panelHi.copy(alpha = 0f))
                            .clickable {
                                vm.quickBrushOrientation = "horizontal"
                                vm.persistQuickBrushState()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewStream,
                                contentDescription = null,
                                tint = if (isHoriz) colors.accent else colors.subText,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.quick_brush_orientation_horizontal),
                                color = if (isHoriz) colors.accent else colors.subText,
                                fontSize = 13.sp,
                                fontWeight = if (isHoriz) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (!isHoriz) colors.accent.copy(alpha = 0.22f) else colors.panelHi.copy(alpha = 0f))
                            .clickable {
                                vm.quickBrushOrientation = "vertical"
                                vm.persistQuickBrushState()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewColumn,
                                contentDescription = null,
                                tint = if (!isHoriz) colors.accent else colors.subText,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.quick_brush_orientation_vertical),
                                color = if (!isHoriz) colors.accent else colors.subText,
                                fontSize = 13.sp,
                                fontWeight = if (!isHoriz) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 核心内容区 (上下两段滑动)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Section 1: 已选常用笔刷
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${stringResource(R.string.quick_brush_favorites_list)} (${favoritePresets.size})",
                                    color = colors.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            if (favoritePresets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.panelHi)
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.quick_brush_empty),
                                        color = colors.subText,
                                        fontSize = 13.sp,
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    favoritePresets.forEachIndexed { index, preset ->
                                        FavoriteBrushRow(
                                            preset = preset,
                                            index = index,
                                            total = favoritePresets.size,
                                            onMoveUp = {
                                                if (index > 0) {
                                                    val curOrder = favoritePresets.map { it.name }.toMutableList()
                                                    val temp = curOrder[index]
                                                    curOrder[index] = curOrder[index - 1]
                                                    curOrder[index - 1] = temp
                                                    vm.quickBrushOrder = curOrder
                                                    vm.persistQuickBrushState()
                                                }
                                            },
                                            onMoveDown = {
                                                if (index < favoritePresets.size - 1) {
                                                    val curOrder = favoritePresets.map { it.name }.toMutableList()
                                                    val temp = curOrder[index]
                                                    curOrder[index] = curOrder[index + 1]
                                                    curOrder[index + 1] = temp
                                                    vm.quickBrushOrder = curOrder
                                                    vm.persistQuickBrushState()
                                                }
                                            },
                                            onRemove = {
                                                vm.toggleFavoriteBrush(preset.name)
                                                vm.quickBrushOrder = vm.quickBrushOrder.filter { it != preset.name }
                                                vm.persistQuickBrushState()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: 笔刷库 (加星标)
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.quick_brush_library_list),
                                color = colors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))

                            // 分组胶囊标签
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(allGroups) { group ->
                                    val isSelected = group == selectedGroup
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.accent.copy(alpha = 0.22f) else colors.panelHi)
                                            .clickable { selectedGroup = group }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = group,
                                            color = if (isSelected) colors.accent else colors.subText,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // 笔刷库条目列表
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                libraryPresets.forEach { preset ->
                                    val isFav = vm.isFavoriteBrush(preset.name)
                                    LibraryBrushRow(
                                        preset = preset,
                                        isFavorite = isFav,
                                        onToggleFavorite = {
                                            vm.toggleFavoriteBrush(preset.name)
                                            if (!isFav) {
                                                if (preset.name !in vm.quickBrushOrder) {
                                                    vm.quickBrushOrder = vm.quickBrushOrder + preset.name
                                                }
                                            } else {
                                                vm.quickBrushOrder = vm.quickBrushOrder.filter { it != preset.name }
                                            }
                                            vm.persistQuickBrushState()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                ReTextButton(
                    text = "完成",
                    onClick = onDismiss,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FavoriteBrushRow(
    preset: BrushPresetInfo,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = Theme.current
    val thumbBmp = rememberPresetThumb(preset.name, preset.thumbBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panelHi)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbBmp != null) {
                Image(
                    bitmap = thumbBmp.asImageBitmap(),
                    contentDescription = preset.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preset.group.isNotBlank()) {
                Text(
                    text = preset.group,
                    color = colors.subText,
                    fontSize = 11.sp,
                )
            }
        }

        // 上移/下移
        if (total > 1) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = index > 0) { onMoveUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Move Up",
                    tint = if (index > 0) colors.subText else colors.subText.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = index < total - 1) { onMoveDown() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = "Move Down",
                    tint = if (index < total - 1) colors.subText else colors.subText.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // 移除
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Remove",
                tint = colors.subText,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun LibraryBrushRow(
    preset: BrushPresetInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val colors = Theme.current
    val thumbBmp = rememberPresetThumb(preset.name, preset.thumbBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panelHi.copy(alpha = 0.65f))
            .clickable { onToggleFavorite() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbBmp != null) {
                Image(
                    bitmap = thumbBmp.asImageBitmap(),
                    contentDescription = preset.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preset.group.isNotBlank()) {
                Text(
                    text = preset.group,
                    color = colors.subText,
                    fontSize = 11.sp,
                )
            }
        }

        // 星标开关
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isFavorite) colors.accent.copy(alpha = 0.18f) else colors.panel)
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (isFavorite) "Starred" else "Unstarred",
                tint = if (isFavorite) colors.accent else colors.subText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
