package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class DisplayMode(val label: String, val unit: String) {
    SHINDO("震度", ""),
    PGA("最大加速度", "gal"),
    PGV("最大速度", "cm/s"),
    PGD("変位", "mm")
}

enum class DepthMode(val label: String) {
    SURFACE("地表"),
    BOREHOLE("地中")
}

data class ObservationStation(
    val id: String,
    val name: String,
    val prefecture: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val mapX: Float, // Normalized 0f..1f relative to Japan bounding box
    val mapY: Float, // Normalized 0f..1f
    var realtimeShindo: Float = 0f,
    var pga: Float = 0.05f,
    var pgv: Float = 0.01f,
    var pgd: Float = 0.001f,
    var isBorehole: Boolean = false
) {
    fun getIntensityColor(): Color {
        return getShindoColor(realtimeShindo)
    }

    fun getFormattedValue(mode: DisplayMode): String {
        return when (mode) {
            DisplayMode.SHINDO -> String.format("%.1f", realtimeShindo.coerceAtLeast(0f))
            DisplayMode.PGA -> String.format("%.2f gal", pga)
            DisplayMode.PGV -> String.format("%.2f cm/s", pgv)
            DisplayMode.PGD -> String.format("%.3f mm", pgd)
        }
    }
}

data class EEWAlert(
    val id: String,
    val reportNum: Int,
    val isFinal: Boolean,
    val isWarning: Boolean, // 警報 or 予報
    val hypocenter: String,
    val lat: Double,
    val lon: Double,
    val depthKm: Int,
    val magnitude: Float,
    val maxShindo: String,
    val originTimeMillis: Long,
    var pWaveRadiusKm: Float = 0f,
    var sWaveRadiusKm: Float = 0f,
    var countdownSeconds: Int = 15,
    val isActive: Boolean = true,
    val isDrill: Boolean = false,
    val affectedPrefectures: List<String> = emptyList()
)

data class EarthquakeItem(
    val id: String,
    val time: String,
    val hypocenter: String,
    val maxScale: String, // "1", "2", "3", "4", "5-", "5+", "6-", "6+", "7"
    val magnitude: Double,
    val depthKm: Int,
    val tsunamiInfo: String, // "津波の心配なし", "津波注意報", "津波警報"
    val lat: Double,
    val lon: Double,
    val intensityPoints: List<PrefectureIntensity> = emptyList()
)

data class PrefectureIntensity(
    val prefecture: String,
    val scale: String
)

data class SeismographSample(
    val timestamp: Long,
    val ew: Float, // East-West acceleration
    val ns: Float, // North-South
    val ud: Float  // Up-Down
)

fun getShindoColor(shindo: Float): Color {
    return when {
        shindo < 0.5f -> Shindo0Color
        shindo < 1.5f -> Shindo1Color
        shindo < 2.5f -> Shindo2Color
        shindo < 3.5f -> Shindo3Color
        shindo < 4.5f -> Shindo4Color
        shindo < 5.0f -> Shindo5LowColor
        shindo < 5.5f -> Shindo5HighColor
        shindo < 6.0f -> Shindo6LowColor
        shindo < 6.5f -> Shindo6HighColor
        else -> Shindo7Color
    }
}

fun parseShindoScaleToText(scale: Int): String {
    return when (scale) {
        10 -> "1"
        20 -> "2"
        30 -> "3"
        40 -> "4"
        45 -> "5弱"
        46 -> "5弱"
        50 -> "5強"
        55 -> "6弱"
        60 -> "6強"
        70 -> "7"
        else -> if (scale >= 70) "7" else if (scale > 0) "${scale / 10}" else "0"
    }
}

fun getShindoColorByText(text: String): Color {
    return when (text) {
        "1" -> Shindo1Color
        "2" -> Shindo2Color
        "3" -> Shindo3Color
        "4" -> Shindo4Color
        "5弱", "5-" -> Shindo5LowColor
        "5強", "5+" -> Shindo5HighColor
        "6弱", "6-" -> Shindo6LowColor
        "6強", "6+" -> Shindo6HighColor
        "7" -> Shindo7Color
        else -> Shindo0Color
    }
}
