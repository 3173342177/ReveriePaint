/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.painting.quickoklch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reverie.paint.R
import com.reverie.paint.core.PaintViewModel
import com.reverie.paint.core.updateBrushColor
import com.reverie.paint.model.OklchColor
import com.reverie.paint.ui.components.DragPillHandle
import com.reverie.paint.ui.components.PanelCloseButton
import com.reverie.paint.ui.components.pressScale
import com.reverie.paint.ui.theme.Glass
import com.reverie.paint.ui.theme.Morandi
import com.reverie.paint.ui.theme.Motion
import com.reverie.paint.ui.theme.glassBorder
import com.reverie.paint.ui.theme.systemHoverIcon
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 紧凑纯粹型 OKLCH 悬浮调色小窗:
 * - 顶部: 拖拽手柄、"OKLCH" 标题、前景色/背景色互换预览、Hex 色值、折叠/关闭按钮
 * - 上半部: 当前色相下的 2D L-C (感知亮度-彩度) 色域切片平面 (动态展示 sRGB 边界与色域裁切)
 * - 下半部: L (Lightness)、C (Chroma)、H (Hue) 三联精密渐变滑块与数值调节
 * - 纯粹致密布局, 零垃圾回收内存分配, 144Hz 满帧交互
 */
@Composable
fun QuickOklchWindow(
    vm: PaintViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    opacity: Float = 0.96f,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val defaultW = with(density) { 244.dp.toPx() }
    val defaultH = with(density) { 330.dp.toPx() }

    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val isCollapsed = vm.quickOklchCollapsed
    val windowShape = remember(isCollapsed) {
        if (isCollapsed) CircleShape else RoundedCornerShape(16.dp)
    }

    var isInteracting by remember { mutableStateOf(false) }
    var currentOklch by remember { mutableStateOf(OklchColor.fromHex(vm.brushColor)) }
    var lastSelfHex by remember { mutableStateOf("") }

    // 与 vm.brushColor 双向同步 (非交互态下由外界取色管驱动更新)
    LaunchedEffect(vm.brushColor) {
        if (!isInteracting && !vm.brushColor.equals(lastSelfHex, ignoreCase = true)) {
            currentOklch = OklchColor.fromHex(vm.brushColor)
            lastSelfHex = ""
        }
    }

    val updateColor = { newColor: OklchColor ->
        currentOklch = newColor
        val hex = newColor.toHex(clip = true)
        lastSelfHex = hex
        vm.updateBrushColor(hex)
    }

    val parsedCurrentColor = remember(vm.brushColor) {
        try {
            Color(android.graphics.Color.parseColor(vm.brushColor))
        } catch (_: Exception) {
            Color.Black
        }
    }

    val parsedSecondaryColor = remember(vm.brushSecondaryColor) {
        try {
            Color(android.graphics.Color.parseColor(vm.brushSecondaryColor))
        } catch (_: Exception) {
            Color.White
        }
    }

    Box(
        modifier = modifier
            .offset {
                val x = if (vm.quickOklchWindowX >= 0f) {
                    vm.quickOklchWindowX.roundToInt()
                } else {
                    val curW = if (windowSize.width > 0) windowSize.width.toFloat() else defaultW
                    ((screenWidthPx - curW) / 2f).coerceAtLeast(0f).roundToInt()
                }
                val y = if (vm.quickOklchWindowY >= 0f) {
                    vm.quickOklchWindowY.roundToInt()
                } else {
                    val curH = if (windowSize.height > 0) windowSize.height.toFloat() else defaultH
                    ((screenHeightPx - curH) / 2f).coerceAtLeast(0f).roundToInt()
                }
                IntOffset(x, y)
            }
    ) {
        Box(
            modifier = Modifier
                .onSizeChanged { size ->
                    windowSize = size
                    vm.quickOklchWindowWidth = size.width.toFloat()
                    vm.quickOklchWindowHeight = size.height.toFloat()
                }
                .shadow(16.dp, windowShape, spotColor = Color.Black.copy(alpha = 0.5f))
                .systemHoverIcon(context)
                .clip(windowShape)
                .then(
                    if (vm.blurBackground && hazeState != null) {
                        Modifier.hazeChild(
                            state = hazeState,
                            style = Glass.popupStyle(if (opacity >= 0.99f) 0.90f else opacity),
                        )
                    } else {
                        Modifier.background(Morandi.panel.copy(alpha = opacity))
                    }
                )
                .glassBorder(windowShape)
                .animateContentSize(Motion.enterSpring())
        ) {
            if (isCollapsed) {
                // ---- 折叠微缩态：高质感悬浮取色胶囊 ----
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    val curW = if (windowSize.width > 0) windowSize.width.toFloat() else defaultW
                                    val curH = if (windowSize.height > 0) windowSize.height.toFloat() else defaultH
                                    if (vm.quickOklchWindowX < 0f) {
                                        vm.quickOklchWindowX = ((screenWidthPx - curW) / 2f).coerceAtLeast(0f)
                                    }
                                    if (vm.quickOklchWindowY < 0f) {
                                        vm.quickOklchWindowY = ((screenHeightPx - curH) / 2f).coerceAtLeast(0f)
                                    }
                                },
                                onDragEnd = { vm.persistQuickOklchState() },
                                onDragCancel = { vm.persistQuickOklchState() },
                            ) { change, dragAmount ->
                                change.consume()
                                vm.quickOklchWindowX += dragAmount.x
                                vm.quickOklchWindowY += dragAmount.y
                            }
                        }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.quickOklchCollapsed = false
                            vm.persistQuickOklchState()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(parsedCurrentColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    )
                }
            } else {
                // ---- 展开态：紧凑纯粹 OKLCH 调色面板 ----
                Column(
                    modifier = Modifier
                        .width(244.dp)
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 1. 顶部操作栏
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DragPillHandle(
                            onDrag = { dragAmount ->
                                val curW = if (windowSize.width > 0) windowSize.width.toFloat() else defaultW
                                val curH = if (windowSize.height > 0) windowSize.height.toFloat() else defaultH
                                if (vm.quickOklchWindowX < 0f) {
                                    vm.quickOklchWindowX = ((screenWidthPx - curW) / 2f).coerceAtLeast(0f)
                                }
                                if (vm.quickOklchWindowY < 0f) {
                                    vm.quickOklchWindowY = ((screenHeightPx - curH) / 2f).coerceAtLeast(0f)
                                }
                                vm.quickOklchWindowX += dragAmount.x
                                vm.quickOklchWindowY += dragAmount.y
                                vm.persistQuickOklchState()
                            },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 标题 + 徽标
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.quick_oklch_window_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Morandi.text,
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Morandi.accent.copy(alpha = 0.22f))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Perceptual",
                                        color = Morandi.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            // 右侧: 前背景双色重叠预览 + 折叠 + 关闭
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                // 前背景色交换预览
                                val swapSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp, 22.dp)
                                        .pressScale(swapSource)
                                        .clickable(
                                            interactionSource = swapSource,
                                            indication = null,
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val targetHex = vm.brushSecondaryColor
                                            currentOklch = OklchColor.fromHex(targetHex)
                                            vm.swapColors()
                                        },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(17.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(parsedSecondaryColor)
                                            .border(1.dp, Morandi.border, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(17.dp)
                                            .align(Alignment.TopStart)
                                            .clip(CircleShape)
                                            .background(parsedCurrentColor)
                                            .border(1.2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                    )
                                }

                                // 最小化折叠按钮
                                val collapseSource = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .pressScale(collapseSource)
                                        .clip(CircleShape)
                                        .background(Morandi.panelHi.copy(alpha = 0.5f))
                                        .clickable(interactionSource = collapseSource, indication = null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            vm.quickOklchCollapsed = true
                                            vm.persistQuickOklchState()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(9.dp)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Morandi.subText)
                                    )
                                }

                                PanelCloseButton(onClose = onClose)
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // 2. 核心 2D L-C 切片平面
                    OklchLCSlicePlane(
                        oklch = currentOklch,
                        onColorChange = { newColor ->
                            updateColor(newColor)
                        },
                        onInteractionStart = { isInteracting = true },
                        onInteractionEnd = { isInteracting = false },
                    )

                    Spacer(Modifier.height(8.dp))

                    // 3. L / C / H 三联精密滑块
                    OklchSlidersGroup(
                        oklch = currentOklch,
                        onColorChange = { newColor ->
                            updateColor(newColor)
                        },
                        onInteractionStart = { isInteracting = true },
                        onInteractionEnd = { isInteracting = false },
                    )

                    Spacer(Modifier.height(6.dp))

                    // 4. 底部 Hex 预览与一键复制
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Morandi.panelHi.copy(alpha = 0.45f))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val hexText = currentOklch.toHex(clip = true)
                                cm.setPrimaryClip(ClipData.newPlainText("hex", hexText))
                                Toast.makeText(context, "$hexText Copied", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentOklch.toHex(clip = true),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Morandi.text,
                        )
                        val inGamut = currentOklch.isInGamut()
                        Text(
                            text = if (inGamut) "sRGB OK" else stringResource(R.string.oklch_out_of_gamut),
                            fontSize = 9.sp,
                            color = if (inGamut) Morandi.subText else Morandi.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2D L-C 切片平面:
 * 纵轴: Lightness L [1.0 (顶部白色) ~ 0.0 (底部黑色)]
 * 横轴: Chroma C [0.0 (左侧灰色) ~ 0.37 (右侧极限彩度)]
 * 色相: 固定为当前传入的 H (色相变动时重绘底图 Bitmap，L/C 变动只移动准星，零性能开销)
 */
@Composable
private fun OklchLCSlicePlane(
    oklch: OklchColor,
    onColorChange: (OklchColor) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bmpWidth = 114
    val bmpHeight = 75
    val maxChroma = OklchColor.MAX_CHROMA

    // 离屏渲染 Bitmap 缓存 (零每帧分配)
    val bitmap = remember { Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888) }
    val pixelBuffer = remember { IntArray(bmpWidth * bmpHeight) }
    var lastHueForBmp by remember { mutableFloatStateOf(-999f) }

    // 仅在 Hue 变化时重新生成切片像素 (114x75 尺寸极小，仅 8550 像素，耗时 < 1ms)
    LaunchedEffect(oklch.h) {
        if (abs(oklch.h - lastHueForBmp) >= 0.5f) {
            val h = oklch.h
            for (y in 0 until bmpHeight) {
                val l = 1f - (y.toFloat() / (bmpHeight - 1).toFloat())
                val rowOffset = y * bmpWidth
                for (x in 0 until bmpWidth) {
                    val c = (x.toFloat() / (bmpWidth - 1).toFloat()) * maxChroma
                    if (OklchColor.isInGamut(l, c, h)) {
                        pixelBuffer[rowOffset + x] = OklchColor.toRgbInt(l, c, h, clip = false)
                    } else {
                        // 超色域区域: 呈现暗色半透明网格视觉，直观展示当前色相的 sRGB 边界轮廓
                        val isChecker = ((x / 3) + (y / 3)) % 2 == 0
                        val checkerColor = if (isChecker) 0xFF2A2C30.toInt() else 0xFF202226.toInt()
                        pixelBuffer[rowOffset + x] = checkerColor
                    }
                }
            }
            bitmap.setPixels(pixelBuffer, 0, bmpWidth, 0, 0, bmpWidth, bmpHeight)
            lastHueForBmp = h
        }
    }

    val planeShape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(planeShape)
            .border(1.dp, Morandi.border, planeShape)
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(oklch.h) {
                    val handleTouch = { pos: Offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w > 0f && h > 0f) {
                            val newL = (1f - (pos.y / h)).coerceIn(0f, 1f)
                            val rawC = (pos.x / w * maxChroma).coerceIn(0f, maxChroma)
                            val maxCInGamut = OklchColor.findMaxChromaInGamut(newL, oklch.h)
                            val finalC = rawC.coerceAtMost(maxCInGamut)
                            onColorChange(OklchColor(newL, finalC, oklch.h))
                        }
                    }

                    detectTapGestures(
                        onPress = { offset ->
                            onInteractionStart()
                            handleTouch(offset)
                            tryAwaitRelease()
                            onInteractionEnd()
                        }
                    )
                }
                .pointerInput(oklch.h) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onInteractionStart()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                val newL = (1f - (offset.y / h)).coerceIn(0f, 1f)
                                val rawC = (offset.x / w * maxChroma).coerceIn(0f, maxChroma)
                                val maxCInGamut = OklchColor.findMaxChromaInGamut(newL, oklch.h)
                                val finalC = rawC.coerceAtMost(maxCInGamut)
                                onColorChange(OklchColor(newL, finalC, oklch.h))
                            }
                        },
                        onDragEnd = { onInteractionEnd() },
                        onDragCancel = { onInteractionEnd() },
                    ) { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w > 0f && h > 0f) {
                            val newL = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                            val rawC = (change.position.x / w * maxChroma).coerceIn(0f, maxChroma)
                            val maxCInGamut = OklchColor.findMaxChromaInGamut(newL, oklch.h)
                            val finalC = rawC.coerceAtMost(maxCInGamut)
                            onColorChange(OklchColor(newL, finalC, oklch.h))
                        }
                    }
                }
        ) {
            // 绘制离屏切片 Bitmap
            drawImage(
                image = bitmap.asImageBitmap(),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )

            // 绘制当前选色准星 (白环 + 黑边描边)
            val cursorX = (oklch.c / maxChroma) * size.width
            val cursorY = (1f - oklch.l) * size.height

            drawCircle(
                color = Color.Black.copy(alpha = 0.8f),
                radius = 8.dp.toPx(),
                center = Offset(cursorX, cursorY),
                style = Stroke(width = 2.5.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(cursorX, cursorY),
                style = Stroke(width = 1.8.dp.toPx()),
            )
            drawCircle(
                color = Color(oklch.toRgbInt(clip = true)),
                radius = 4.5.dp.toPx(),
                center = Offset(cursorX, cursorY),
            )
        }
    }
}

/**
 * L / C / H 三联精密滑块组件组
 */
@Composable
private fun OklchSlidersGroup(
    oklch: OklchColor,
    onColorChange: (OklchColor) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 1. L (Lightness 0% ~ 100%)
        val lColors = remember(oklch.c, oklch.h) {
            listOf(
                Color(OklchColor.toRgbInt(0f, oklch.c, oklch.h)),
                Color(OklchColor.toRgbInt(0.25f, oklch.c, oklch.h)),
                Color(OklchColor.toRgbInt(0.5f, oklch.c, oklch.h)),
                Color(OklchColor.toRgbInt(0.75f, oklch.c, oklch.h)),
                Color(OklchColor.toRgbInt(1f, oklch.c, oklch.h)),
            )
        }
        OklchSingleSlider(
            label = "L",
            displayValue = "${(oklch.l * 100f).roundToInt()}%",
            progress = oklch.l,
            trackBrush = Brush.horizontalGradient(lColors),
            onValueChange = { frac ->
                val newL = frac.coerceIn(0f, 1f)
                val maxC = OklchColor.findMaxChromaInGamut(newL, oklch.h)
                onColorChange(OklchColor(newL, oklch.c.coerceAtMost(maxC), oklch.h))
            },
            onInteractionStart = onInteractionStart,
            onInteractionEnd = onInteractionEnd,
        )

        // 2. C (Chroma 0.00 ~ 0.37)
        val maxC = OklchColor.MAX_CHROMA
        val cColors = remember(oklch.l, oklch.h) {
            listOf(
                Color(OklchColor.toRgbInt(oklch.l, 0f, oklch.h)),
                Color(OklchColor.toRgbInt(oklch.l, maxC * 0.33f, oklch.h)),
                Color(OklchColor.toRgbInt(oklch.l, maxC * 0.66f, oklch.h)),
                Color(OklchColor.toRgbInt(oklch.l, maxC, oklch.h)),
            )
        }
        OklchSingleSlider(
            label = "C",
            displayValue = "%.2f".format(oklch.c),
            progress = (oklch.c / maxC).coerceIn(0f, 1f),
            trackBrush = Brush.horizontalGradient(cColors),
            onValueChange = { frac ->
                val rawC = (frac * maxC).coerceIn(0f, maxC)
                val maxCInGamut = OklchColor.findMaxChromaInGamut(oklch.l, oklch.h)
                onColorChange(OklchColor(oklch.l, rawC.coerceAtMost(maxCInGamut), oklch.h))
            },
            onInteractionStart = onInteractionStart,
            onInteractionEnd = onInteractionEnd,
        )

        // 3. H (Hue 0° ~ 360°)
        val hColors = remember(oklch.l, oklch.c) {
            (0..12).map { i ->
                val deg = (i * 30f) % 360f
                Color(OklchColor.toRgbInt(oklch.l.coerceIn(0.2f, 0.85f), oklch.c.coerceAtLeast(0.12f), deg))
            }
        }
        OklchSingleSlider(
            label = "H",
            displayValue = "${oklch.h.roundToInt()}°",
            progress = (oklch.h / 360f).coerceIn(0f, 1f),
            trackBrush = Brush.horizontalGradient(hColors),
            onValueChange = { frac ->
                val newH = (frac * 360f) % 360f
                val maxCInGamut = OklchColor.findMaxChromaInGamut(oklch.l, newH)
                onColorChange(OklchColor(oklch.l, oklch.c.coerceAtMost(maxCInGamut), newH))
            },
            onInteractionStart = onInteractionStart,
            onInteractionEnd = onInteractionEnd,
        )
    }
}

/**
 * 单条精简滑动轨道组件
 */
@Composable
private fun OklchSingleSlider(
    label: String,
    displayValue: String,
    progress: Float,
    trackBrush: Brush,
    onValueChange: (Float) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Morandi.text,
            modifier = Modifier.width(13.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, Morandi.border, RoundedCornerShape(9.dp))
                .background(trackBrush)
                .pointerInput(Unit) {
                    val updateFraction = { x: Float ->
                        val frac = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(frac)
                    }
                    detectTapGestures(
                        onPress = { offset ->
                            onInteractionStart()
                            updateFraction(offset.x)
                            tryAwaitRelease()
                            onInteractionEnd()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onInteractionStart()
                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onValueChange(frac)
                        },
                        onDragEnd = { onInteractionEnd() },
                        onDragCancel = { onInteractionEnd() },
                    ) { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(frac)
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val thumbX = progress.coerceIn(0f, 1f) * size.width
                drawCircle(
                    color = Color.Black.copy(alpha = 0.6f),
                    radius = 8.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        Text(
            text = displayValue,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Morandi.subText,
            modifier = Modifier.width(32.dp),
        )
    }
}
