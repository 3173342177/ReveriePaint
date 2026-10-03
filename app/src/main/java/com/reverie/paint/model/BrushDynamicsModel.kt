/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import com.reverie.paint.R
import kotlin.math.max
import kotlin.math.min

/**
 * 曲线控制点，范围严格归一化于 [0.0, 1.0]
 */
data class CurvePoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x in 0f..1f && y in 0f..1f) {
            "CurvePoint coordinates must be within [0, 1], got x=$x, y=$y"
        }
    }

    companion object {
        fun of(x: Float, y: Float): CurvePoint {
            return CurvePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }
}

/**
 * 常用曲线预设模板
 */
enum class CurvePreset(val titleRes: Int) {
    LINEAR(R.string.brush_curve_preset_linear),
    SOFT(R.string.brush_curve_preset_soft),
    HARD(R.string.brush_curve_preset_hard),
    S_CURVE(R.string.brush_curve_preset_scurve),
    PEAK(R.string.brush_curve_preset_peak),
    STEPS(R.string.brush_curve_preset_steps);

    fun createPoints(): List<CurvePoint> = when (this) {
        LINEAR -> listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        SOFT -> listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.5f),
            CurvePoint(0.75f, 0.9f),
            CurvePoint(1f, 1f),
        )
        HARD -> listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.1f),
            CurvePoint(0.75f, 0.5f),
            CurvePoint(1f, 1f),
        )
        S_CURVE -> listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.1f),
            CurvePoint(0.75f, 0.9f),
            CurvePoint(1f, 1f),
        )
        PEAK -> listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.5f, 1f),
            CurvePoint(1f, 0f),
        )
        STEPS -> listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.48f, 0f),
            CurvePoint(0.52f, 1f),
            CurvePoint(1f, 1f),
        )
    }
}

/**
 * Krita 笔刷核心动态传感器全集
 */
enum class BrushSensor(
    val id: String,
    val titleRes: Int,
    val iconRes: Int,
) {
    PRESSURE("pressure", R.string.brush_sensor_pressure, R.drawable.ic_hand),
    SPEED("speed", R.string.brush_sensor_speed, R.drawable.ic_line),
    DRAWING_ANGLE("drawingangle", R.string.brush_sensor_drawingangle, R.drawable.ic_rotate_cw),
    TILT_ELEVATION("tilt-elevation", R.string.brush_sensor_tilt_elevation, R.drawable.ic_pencil),
    TILT_DIRECTION("tilt-direction", R.string.brush_sensor_tilt_direction, R.drawable.ic_rotate_ccw),
    TILT_X("tilt-x", R.string.brush_sensor_tilt_x, R.drawable.ic_flip_h),
    TILT_Y("tilt-y", R.string.brush_sensor_tilt_y, R.drawable.ic_flip_v),
    ROTATION("rotation", R.string.brush_sensor_rotation, R.drawable.ic_refresh),
    TANGENTIAL_PRESSURE("tangentialpressure", R.string.brush_sensor_tangential_pressure, R.drawable.ic_sliders),
    FADE("fade", R.string.brush_sensor_fade, R.drawable.ic_droplet),
    DISTANCE("distance", R.string.brush_sensor_distance, R.drawable.ic_repeat_loop),
    TIME("time", R.string.brush_sensor_time, R.drawable.ic_clock),
    FUZZY("fuzzy", R.string.brush_sensor_fuzzy, R.drawable.ic_grid);

    companion object {
        fun fromId(id: String): BrushSensor {
            val normalized = id.trim().lowercase()
            return entries.firstOrNull { it.id == normalized } ?: PRESSURE
        }
    }
}

/**
 * 单项参数的动态响应配置
 */
data class DynamicOptionConfig(
    val optionKey: String,
    val enabled: Boolean = false,
    val sensorId: String = BrushSensor.PRESSURE.id,
    val points: List<CurvePoint> = CurvePreset.LINEAR.createPoints(),
    val strength: Float = 1.0f,
) {
    /**
     * 将控制点序列化为 Krita 标准格式 (e.g. "0,0;0.5,0.7;1,1;")
     */
    fun toKritaCurveString(): String {
        if (points.isEmpty()) return "0,0;1,1;"
        val sorted = points.sortedBy { it.x }
        val sb = StringBuilder()
        for (pt in sorted) {
            sb.append(String.format(java.util.Locale.US, "%.3f,%.3f;", pt.x, pt.y))
        }
        return sb.toString()
    }

    /**
     * 单调三次样条插值评估给定 x 输入 (0.0..1.0) 下的 y 输出 (0.0..1.0)
     */
    fun evaluate(x: Float): Float {
        val clampedX = x.coerceIn(0f, 1f)
        if (points.isEmpty()) return clampedX
        val sorted = points.sortedBy { it.x }
        if (clampedX <= sorted.first().x) return sorted.first().y
        if (clampedX >= sorted.last().x) return sorted.last().y

        // 查找包含 clampedX 的线段区间
        for (i in 0 until sorted.size - 1) {
            val p0 = sorted[i]
            val p1 = sorted[i + 1]
            if (clampedX in p0.x..p1.x) {
                val dx = p1.x - p0.x
                if (dx <= 0.0001f) return p0.y
                val t = (clampedX - p0.x) / dx
                // 使用 Hermite / Smoothstep 产生光滑平滑过渡
                val smoothT = t * t * (3f - 2f * t)
                return (p0.y + (p1.y - p0.y) * smoothT).coerceIn(0f, 1f)
            }
        }
        return clampedX
    }

    companion object {
        fun defaultFor(optionKey: String): DynamicOptionConfig {
            return DynamicOptionConfig(
                optionKey = optionKey,
                enabled = false,
                sensorId = BrushSensor.PRESSURE.id,
                points = CurvePreset.LINEAR.createPoints(),
                strength = 1.0f,
            )
        }

        /**
         * 从 Krita 格式反序列化点列表
         */
        fun parseKritaCurve(curveStr: String): List<CurvePoint> {
            val trimmed = curveStr.trim()
            if (trimmed.isBlank()) return CurvePreset.LINEAR.createPoints()
            val tokens = trimmed.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            val parsed = mutableListOf<CurvePoint>()
            for (t in tokens) {
                val parts = t.split(',')
                if (parts.size == 2) {
                    val x = parts[0].toFloatOrNull()
                    val y = parts[1].toFloatOrNull()
                    if (x != null && y != null) {
                        parsed.add(CurvePoint.of(x, y))
                    }
                }
            }
            if (parsed.size < 2) return CurvePreset.LINEAR.createPoints()
            return parsed.sortedBy { it.x }
        }
    }
}
