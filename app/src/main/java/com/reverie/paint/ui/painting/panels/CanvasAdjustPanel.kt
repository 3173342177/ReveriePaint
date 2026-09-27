/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reverie.paint.R
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.model.AnchorPosition
import com.reverie.paint.model.AspectRatioPreset
import com.reverie.paint.model.CanvasAdjustMode
import com.reverie.paint.model.ResampleFilter
import com.reverie.paint.ui.components.ReIconButton
import com.reverie.paint.ui.components.ReTextButton
import com.reverie.paint.ui.theme.Glass
import com.reverie.paint.ui.theme.Morandi
import com.reverie.paint.ui.theme.glassBorder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlin.math.roundToInt

@Composable
fun CanvasAdjustPanel(
    vm: PaintViewModel,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val state = vm.canvasAdjustState
    val focusManager = LocalFocusManager.current
    val panelShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .widthIn(max = 560.dp)
            .shadow(20.dp, panelShape, spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(panelShape)
            .then(
                if (vm.blurBackground && hazeState != null) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = Glass.popupStyle(vm.popupPanelOpacity),
                    )
                } else {
                    Modifier.background(Morandi.panel.copy(alpha = vm.popupPanelOpacity))
                }
            )
            .glassBorder(panelShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 顶部模式切换分段器 (裁切与扩展 vs 图像缩放)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Morandi.panelHi)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isCrop = state.mode == CanvasAdjustMode.CROP_EXPAND
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isCrop) Morandi.accent else Color.Transparent)
                        .clickable {
                            if (!isCrop) {
                                vm.canvasAdjustState = state.copy(
                                    mode = CanvasAdjustMode.CROP_EXPAND,
                                    lockAspectRatio = false,
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.canvas_adjust_mode_crop),
                        color = if (isCrop) Color.White else Morandi.subText,
                        fontSize = 13.sp,
                        fontWeight = if (isCrop) FontWeight.Bold else FontWeight.Medium
                    )
                }

                val isScale = state.mode == CanvasAdjustMode.RESAMPLE_SCALE
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isScale) Morandi.accent else Color.Transparent)
                        .clickable {
                            if (!isScale) {
                                vm.canvasAdjustState = state.copy(
                                    mode = CanvasAdjustMode.RESAMPLE_SCALE,
                                    lockAspectRatio = true,
                                    cropX = 0,
                                    cropY = 0,
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.canvas_adjust_mode_scale),
                        color = if (isScale) Color.White else Morandi.subText,
                        fontSize = 13.sp,
                        fontWeight = if (isScale) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // 2. 尺寸数值输入与等比锁定行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Width input
                DimensionInputField(
                    label = "W",
                    value = state.targetWidth,
                    onValueCommitted = { newW ->
                        vm.canvasAdjustState = state.withTargetWidth(newW)
                    }
                )

                Spacer(Modifier.width(8.dp))

                // Ratio lock toggle button
                val isLocked = state.lockAspectRatio || state.selectedPreset != AspectRatioPreset.FREE
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isLocked) Morandi.accent.copy(alpha = 0.2f) else Morandi.panelHi)
                        .clickable {
                            val newLocked = !isLocked
                            vm.canvasAdjustState = state.copy(
                                lockAspectRatio = newLocked,
                                selectedPreset = if (!newLocked) AspectRatioPreset.FREE else state.selectedPreset
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open),
                        contentDescription = stringResource(if (isLocked) R.string.canvas_adjust_unlock_ratio else R.string.canvas_adjust_lock_ratio),
                        tint = if (isLocked) Morandi.accent else Morandi.subText,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Height input
                DimensionInputField(
                    label = "H",
                    value = state.targetHeight,
                    onValueCommitted = { newH ->
                        vm.canvasAdjustState = state.withTargetHeight(newH)
                    }
                )

                Spacer(Modifier.width(10.dp))

                // Reset button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Morandi.panelHi)
                        .clickable {
                            focusManager.clearFocus()
                            vm.canvasAdjustState = state.reset()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = stringResource(R.string.canvas_adjust_reset),
                        tint = Morandi.subText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3. 动态配置选项行 (根据模式切换：裁切模式显示锚点与常用比例；缩放模式显示倍率与插值算法)
            if (state.mode == CanvasAdjustMode.CROP_EXPAND) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 9 宫格锚点点阵
                    AnchorMatrixGrid(
                        selectedAnchor = state.anchor,
                        onAnchorSelected = { newAnchor ->
                            vm.canvasAdjustState = state.withAnchor(newAnchor)
                        }
                    )

                    // 比例预设水平滑动芯片
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AspectRatioPreset.entries.forEach { preset ->
                            val isSelected = state.selectedPreset == preset
                            ToolFloatChip(
                                label = stringResource(preset.labelRes),
                                selected = isSelected,
                                onClick = {
                                    vm.canvasAdjustState = state.withPreset(preset)
                                }
                            )
                        }
                    }
                }
            } else {
                // 图像缩放模式：常用倍率与插值算法切换
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 常用分辨率倍率快速芯片
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.5f to "0.5×", 0.75f to "0.75×", 1.5f to "1.5×", 2.0f to "2.0×", 3.0f to "3.0×").forEach { (mul, label) ->
                            val isCurrent = (state.origWidth * mul).roundToInt() == state.targetWidth &&
                                    (state.origHeight * mul).roundToInt() == state.targetHeight
                            ToolFloatChip(
                                label = label,
                                selected = isCurrent,
                                onClick = {
                                    val newW = (state.origWidth * mul).roundToInt().coerceIn(1, 16384)
                                    val newH = (state.origHeight * mul).roundToInt().coerceIn(1, 16384)
                                    vm.canvasAdjustState = state.copy(
                                        targetWidth = newW,
                                        targetHeight = newH,
                                    )
                                }
                            )
                        }
                    }

                    // 插值算法选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            ResampleFilter.BICUBIC to stringResource(R.string.canvas_resample_bicubic),
                            ResampleFilter.NEAREST to stringResource(R.string.canvas_resample_nearest),
                        ).forEach { (filter, label) ->
                            val isSel = state.resampleFilter == filter
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Morandi.accent.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) Morandi.accent else Morandi.border,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        vm.canvasAdjustState = state.copy(resampleFilter = filter)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Morandi.accent else Morandi.subText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // 4. 底部确定与取消操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 取消按钮
                ReTextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = {
                        focusManager.clearFocus()
                        vm.exitCanvasAdjustMode()
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = Morandi.panelHi,
                    contentColor = Morandi.text,
                    fontSize = 14.sp
                )

                // 应用按钮
                ReTextButton(
                    text = stringResource(R.string.canvas_adjust_apply),
                    onClick = {
                        focusManager.clearFocus()
                        vm.applyCanvasAdjustment()
                    },
                    modifier = Modifier.weight(1f),
                    containerColor = Morandi.accent,
                    contentColor = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 专为画布尺寸设计的紧凑数字输入控件
 */
@Composable
private fun DimensionInputField(
    label: String,
    value: Int,
    onValueCommitted: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .width(105.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Morandi.panelHi)
            .border(1.dp, Morandi.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Morandi.subText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
        BasicTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                if (filtered.length <= 5) {
                    text = filtered
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Morandi.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(Morandi.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val parsed = text.toIntOrNull() ?: value
                    onValueCommitted(parsed)
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "px",
            color = Morandi.subText.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}

/**
 * 3x3 经典九宫格扩展定位锚点选择器
 */
@Composable
private fun AnchorMatrixGrid(
    selectedAnchor: AnchorPosition,
    onAnchorSelected: (AnchorPosition) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Morandi.panelHi)
            .border(1.dp, Morandi.border, RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rows = listOf(
                listOf(AnchorPosition.TOP_LEFT, AnchorPosition.TOP_CENTER, AnchorPosition.TOP_RIGHT),
                listOf(AnchorPosition.CENTER_LEFT, AnchorPosition.CENTER, AnchorPosition.CENTER_RIGHT),
                listOf(AnchorPosition.BOTTOM_LEFT, AnchorPosition.BOTTOM_CENTER, AnchorPosition.BOTTOM_RIGHT),
            )
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (anchor in row) {
                        val isSelected = selectedAnchor == anchor
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) Morandi.accent else Morandi.subText.copy(alpha = 0.35f))
                                .clickable { onAnchorSelected(anchor) }
                        )
                    }
                }
            }
        }
    }
}
