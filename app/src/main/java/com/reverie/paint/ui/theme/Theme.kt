/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.reverie.paint.model.BrushPreset
import com.reverie.paint.model.CanvasPreset

/**
 * All UI colors as a data class so a theme system (light / dark / accent /
 * user-defined) can swap in different instances later. Every composable
 * must read colors from [Theme.current] - never hardcode a color.
 *
 * Components reference tokens by semantic name (bg / panel / accent /
 * onAccent / scrim ...) so a new theme only needs a different instance.
 */
data class AppColors(
    val bg: Color, // app / page background
    val panel: Color, // panels, rails, top bar
    val panelHi: Color, // raised surfaces, sliders track
    val accent: Color, // selection, primary actions
    val accentHi: Color, // brighter accent (borders, pressed)
    val onAccent: Color, // text/icon on accent (usually white)
    val text: Color, // primary text
    val subText: Color, // secondary text, labels
    val canvasBg: Color, // workspace behind the document
    val border: Color, // hairline separators
    val icon: Color, // idle icons
    val scrim: Color, // full-screen backdrop over the canvas
    val gridLine: Color, // canvas grid
    val canvasShadow: Color, // drop shadow under the document
    val error: Color = Color(0xFFD46A6A), // destructive / error actions
)

/** Morandi Dark palette - refined neutral studio workspace, elevated dark graphite panels, specular hairline borders, elegant Morandi misty blue accent. */
val MorandiDarkColors =
    AppColors(
        bg = Color(0xFF141416), // Procreate deep graphite background
        panel = Color(0xFF1E1E22), // Sleek dark graphite panel (iPadOS dark chrome)
        panelHi = Color(0xFF2A2A2E), // Raised surfaces, cards, slider tracks
        accent = Color(0xFF5A6E8A), // Morandi signature misty blue
        accentHi = Color(0xFF78889F),
        onAccent = Color(0xFFFFFFFF),
        text = Color(0xFFF5F5F7), // Crisp readable off-white text
        subText = Color(0xFF8E8E93), // Apple secondary label gray
        canvasBg = Color(0xFF28282C), // Neutral studio workspace backdrop behind document
        border = Color.Transparent, // Borderless studio aesthetic (relies on elevation & tonal steps)
        icon = Color(0xFF98989D), // Apple neutral silver icon
        scrim = Color(0x80000000),
        gridLine = Color(0x1FFFFFFF),
        canvasShadow = Color(0x80000000),
        error = Color(0xFFD46A6A),
    )

/** Default iOS Light palette - soft Morandi warm studio workspace with balanced contrast. */
val MorandiLightColors =
    AppColors(
        bg = Color(0xFFF2F2F7), // iOS Grouped Background: refined light gray
        panel = Color(0xFFF7F7F9), // Soft warm off-white panels (eliminates harsh pure white glare)
        panelHi = Color(0xFFEAEAEE), // Refined raised surface gray
        accent = Color(0xFF5A6E8A), // Morandi signature misty blue
        accentHi = Color(0xFF4A5C75),
        onAccent = Color(0xFFFFFFFF),
        text = Color(0xFF242426), // Deep warm graphite text (replaces harsh #000000)
        subText = Color(0xFF8A8A8E), // Morandi balanced secondary label gray
        canvasBg = Color(0xFFE5E5EA), // Clean neutral workspace backdrop
        border = Color.Transparent, // Borderless studio aesthetic
        icon = Color(0xFF636366), // Balanced medium graphite icon (eliminates excessive contrast)
        scrim = Color(0x3D000000),
        gridLine = Color(0x1F000000),
        canvasShadow = Color(0x1F000000),
        error = Color(0xFFD04444),
    )

/** Alias for backward compatibility */
val MorandiColors = MorandiDarkColors
val IosDarkColors = MorandiDarkColors
val IosLightColors = MorandiLightColors

fun blendColors(base: Color, tint: Color, tintWeight: Float): Color {
    val r = base.red * (1f - tintWeight) + tint.red * tintWeight
    val g = base.green * (1f - tintWeight) + tint.green * tintWeight
    val b = base.blue * (1f - tintWeight) + tint.blue * tintWeight
    return Color(red = r, green = g, blue = b, alpha = base.alpha)
}

/** Build theme colors where changing accent only affects the primary accent, keeping backgrounds/panels pure neutral with contrast levels */
fun buildThemeColors(isDark: Boolean, accent: Color, contrast: String = "STANDARD"): AppColors {
    val fallbackBase = if (isDark) {
        when (contrast) {
            "SOFT" -> MorandiDarkColors.copy(
                panel = Color(0xFF222226),
                panelHi = Color(0xFF2D2D32),
                text = Color(0xFFE2E2E6),
                subText = Color(0xFF828286),
                icon = Color(0xFF88888D),
            )
            "HIGH" -> MorandiDarkColors.copy(
                panel = Color(0xFF161618),
                panelHi = Color(0xFF242428),
                text = Color(0xFFFFFFFF),
                subText = Color(0xFF9E9EA4),
                icon = Color(0xFFAFAFB5),
            )
            else -> MorandiDarkColors
        }
    } else {
        when (contrast) {
            "SOFT" -> MorandiLightColors.copy(
                panel = Color(0xFFF4F4F6),
                panelHi = Color(0xFFE5E5E9),
                text = Color(0xFF333336),
                subText = Color(0xFF949498),
                icon = Color(0xFF757579),
            )
            "HIGH" -> MorandiLightColors.copy(
                panel = Color(0xFFFFFFFF),
                panelHi = Color(0xFFE2E2E7),
                text = Color(0xFF111113),
                subText = Color(0xFF707075),
                icon = Color(0xFF48484C),
            )
            else -> MorandiLightColors
        }
    }

    val accentHi = if (isDark) {
        blendColors(accent, Color.White, 0.18f)
    } else {
        blendColors(accent, Color.Black, 0.12f)
    }
    return fallbackBase.copy(
        accent = accent,
        accentHi = accentHi,
    )
}

/** Build Monet dynamic color theme for Android 12+ (Material You) without altering neutral backgrounds */
fun getMonetColors(
    context: Context,
    isDark: Boolean = true,
    fallbackAccent: Color = Color(0xFF5A6E8A),
    contrast: String = "STANDARD",
): AppColors {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return try {
            val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            buildThemeColors(isDark = isDark, accent = scheme.primary, contrast = contrast)
        } catch (_: Throwable) {
            buildThemeColors(isDark = isDark, accent = fallbackAccent, contrast = contrast)
        }
    }
    return buildThemeColors(isDark = isDark, accent = fallbackAccent, contrast = contrast)
}

/**
 * Active theme. UI code reads [Theme.current]; a theme system can swap
 * this (light/dark/accent presets or user-defined colors) at runtime.
 */
object Theme {
    var current by mutableStateOf(MorandiColors)
}

/** Backwards-compatible alias so existing call sites can migrate gradually. */
val Morandi: AppColors
    get() = Theme.current

val BrushPresets =
    listOf(
        BrushPreset("钢笔", 4.0),
        BrushPreset("铅笔", 2.0),
        BrushPreset("马克笔", 18.0),
        BrushPreset("毛笔", 12.0),
        BrushPreset("喷枪", 30.0),
        BrushPreset("圆头", 40.0),
    )

val CanvasPresets =
    listOf(
        CanvasPreset("手机", 1080, 1920),
        CanvasPreset("方形", 2000, 2000),
        CanvasPreset("横屏", 1920, 1080),
        CanvasPreset("A4", 2480, 3508),
        CanvasPreset("壁纸", 1440, 3120),
        CanvasPreset("2K", 2560, 1440),
    )

val ColorSwatches =
    listOf(
        "#262A30",
        "#7C8F9E",
        "#8D9E8F",
        "#C9ADA7",
        "#F2F0EA",
        "#B4552D",
        "#5A6E8A",
        "#9A8F7B",
        "#FFFFFF",
        "#000000",
    )

/** Parse a "#rrggbb" hex string into a Compose Color. */
fun parseColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

/**
 * 显式强制使用系统标准箭头指针（避免受 View 级透明指针影响）
 */
fun systemDefaultPointerIcon(context: Context): PointerIcon {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PointerIcon(
            android.view.PointerIcon.getSystemIcon(context, android.view.PointerIcon.TYPE_DEFAULT)
        )
    } else {
        PointerIcon.Default
    }
}

/**
 * 显式使用 Android 官方系统 TYPE_NULL 隐藏光标（避免使用自定义 Bitmap 导致的硬件切换闪烁）
 */
fun systemNullPointerIcon(context: Context): PointerIcon {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PointerIcon(
            android.view.PointerIcon.getSystemIcon(context, android.view.PointerIcon.TYPE_NULL)
        )
    } else {
        PointerIcon.Default
    }
}

fun Modifier.systemHoverIcon(context: Context): Modifier =
    this.pointerHoverIcon(systemDefaultPointerIcon(context), overrideDescendants = true)



