/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OKLCH 颜色数据模型及高精度色彩空间双向转换。
 *
 * 参数定义:
 * - [l]: 感知明度 (Lightness), 范围 [0.0, 1.0]
 * - [c]: 彩度 (Chroma), 范围 [0.0, MAX_CHROMA] (sRGB 极限彩度约 0.37)
 * - [h]: 色相角 (Hue), 范围 [0.0, 360.0)
 */
data class OklchColor(
    val l: Float,
    val c: Float,
    val h: Float,
) {
    companion object {
        const val MAX_CHROMA = 0.37f
        private const val DEG2RAD = (PI / 180.0).toFloat()
        private const val RAD2DEG = (180.0 / PI).toFloat()
        private const val EPSILON = 0.0005f

        /**
         * sRGB [0..255] 整数转为 OKLCH 颜色
         */
        fun fromRgb(r: Int, g: Int, b: Int): OklchColor {
            val rLin = srgbToLinear(r / 255f)
            val gLin = srgbToLinear(g / 255f)
            val bLin = srgbToLinear(b / 255f)

            // Linear sRGB -> LMS
            val lVal = 0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin
            val mVal = 0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin
            val sVal = 0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin

            val lPrime = cbrt(lVal)
            val mPrime = cbrt(mVal)
            val sPrime = cbrt(sVal)

            // LMS -> OKLab
            val okL = 0.2104542553f * lPrime + 0.7936177850f * mPrime - 0.0040720468f * sPrime
            val okA = 1.9779984951f * lPrime - 2.4285922050f * mPrime + 0.4505937099f * sPrime
            val okB = 0.0259040371f * lPrime + 0.7827717662f * mPrime - 0.8086757660f * sPrime

            // OKLab -> OKLCH
            val chroma = sqrt(okA * okA + okB * okB)
            var hue = atan2(okB, okA) * RAD2DEG
            if (hue < 0f) hue += 360f

            return OklchColor(
                l = okL.coerceIn(0f, 1f),
                c = chroma.coerceAtLeast(0f),
                h = if (chroma < 0.0001f) 0f else hue % 360f,
            )
        }

        fun fromRgbInt(rgbInt: Int): OklchColor {
            val r = (rgbInt shr 16) and 0xFF
            val g = (rgbInt shr 8) and 0xFF
            val b = rgbInt and 0xFF
            return fromRgb(r, g, b)
        }

        fun fromHex(hex: String): OklchColor {
            val clean = hex.removePrefix("#").trim()
            val colorInt = clean.toLongOrNull(16)?.toInt() ?: 0
            return fromRgbInt(colorInt)
        }

        /**
         * 判定当前给定的 (L, C, H) 是否落在 sRGB 色域内部
         */
        fun isInGamut(l: Float, c: Float, h: Float): Boolean {
            if (l <= 0f || l >= 1f) return c <= 0.001f
            val (r, g, b) = oklchToLinearSrgb(l, c, h)
            return r in -EPSILON..(1f + EPSILON) &&
                g in -EPSILON..(1f + EPSILON) &&
                b in -EPSILON..(1f + EPSILON)
        }

        /**
         * 二分查找给定 (L, H) 下在 sRGB 色域内的最大彩度 (C_max)
         */
        fun findMaxChromaInGamut(l: Float, h: Float): Float {
            if (l <= 0.001f || l >= 0.999f) return 0f
            var low = 0f
            var high = MAX_CHROMA
            for (i in 0 until 14) {
                val mid = (low + high) * 0.5f
                if (isInGamut(l, mid, h)) {
                    low = mid
                } else {
                    high = mid
                }
            }
            return low
        }

        /**
         * 色域裁切 (Gamut Clip):
         * 保持 L 与 H 不变，若超色域则将 C 限制在 sRGB 最大边界内
         */
        fun clipToGamut(l: Float, c: Float, h: Float): OklchColor {
            val maxC = findMaxChromaInGamut(l, h)
            return OklchColor(l, min(c, maxC), h)
        }

        /**
         * OKLCH -> Linear sRGB (未做 clamp)
         */
        private fun oklchToLinearSrgb(l: Float, c: Float, h: Float): Triple<Float, Float, Float> {
            val hRad = h * DEG2RAD
            val a = c * cos(hRad)
            val b = c * sin(hRad)

            // OKLab -> LMS
            val lPrime = l + 0.3963377774f * a + 0.2158037573f * b
            val mPrime = l - 0.1055613458f * a - 0.0638541728f * b
            val sPrime = l - 0.0894841775f * a - 1.2914855480f * b

            val lVal = lPrime * lPrime * lPrime
            val mVal = mPrime * mPrime * mPrime
            val sVal = sPrime * sPrime * sPrime

            // LMS -> Linear sRGB
            val rLin = +4.0767416621f * lVal - 3.3077115913f * mVal + 0.2309699292f * sVal
            val gLin = -1.2684380046f * lVal + 2.6097574011f * mVal - 0.3413193965f * sVal
            val bLin = -0.0041960863f * lVal - 0.7034186147f * mVal + 1.7076147010f * sVal

            return Triple(rLin, gLin, bLin)
        }

        /**
         * OKLCH -> sRGB [0..255] 整数 (0xAARRGGBB)
         * @param clip 若为 true, 超色域时先执行 Gamut Clip，避免色彩扭曲
         */
        fun toRgbInt(l: Float, c: Float, h: Float, clip: Boolean = true): Int {
            val targetC = if (clip && !isInGamut(l, c, h)) {
                findMaxChromaInGamut(l, h)
            } else {
                c
            }

            val (rLin, gLin, bLin) = oklchToLinearSrgb(l, targetC, h)

            val r = (linearToSrgb(rLin).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val g = (linearToSrgb(gLin).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val b = (linearToSrgb(bLin).coerceIn(0f, 1f) * 255f + 0.5f).toInt()

            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun toHex(l: Float, c: Float, h: Float, clip: Boolean = true): String {
            val rgb = toRgbInt(l, c, h, clip)
            return "#%06X".format(rgb and 0xFFFFFF)
        }

        private fun srgbToLinear(c: Float): Float {
            return if (c <= 0.04045f) {
                c / 12.92f
            } else {
                ((c + 0.055f) / 1.055f).pow(2.4f)
            }
        }

        private fun linearToSrgb(c: Float): Float {
            return if (c <= 0.0031308f) {
                12.92f * c
            } else {
                1.055f * c.pow(1f / 2.4f) - 0.055f
            }
        }

        private fun cbrt(v: Float): Float {
            return if (v >= 0f) {
                v.pow(1f / 3f)
            } else {
                -(-v).pow(1f / 3f)
            }
        }
    }

    fun toRgbInt(clip: Boolean = true): Int = toRgbInt(l, c, h, clip)

    fun toHex(clip: Boolean = true): String = toHex(l, c, h, clip)

    fun isInGamut(): Boolean = isInGamut(l, c, h)

    fun clipToGamut(): OklchColor = clipToGamut(l, c, h)
}
