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

    fun getValueForDisplayMode(mode: DisplayMode): Float {
        return when (mode) {
            DisplayMode.SHINDO -> realtimeShindo
            DisplayMode.PGA -> pga
            DisplayMode.PGV -> pgv
            DisplayMode.PGD -> pgd
        }
    }

    fun getColorForDisplayMode(mode: DisplayMode): Color {
        val valForMode = getValueForDisplayMode(mode)
        val equivalentShindo = when (mode) {
            DisplayMode.SHINDO -> realtimeShindo
            DisplayMode.PGA -> when {
                valForMode < 0.8f -> 0f
                valForMode < 2.5f -> 1f
                valForMode < 8.0f -> 2f
                valForMode < 25.0f -> 3f
                valForMode < 50.0f -> 4f
                valForMode < 100.0f -> 4.5f
                valForMode < 250.0f -> 5.0f
                valForMode < 400.0f -> 5.5f
                valForMode < 600.0f -> 6.0f
                else -> 7.0f
            }
            DisplayMode.PGV -> when {
                valForMode < 0.2f -> 0f
                valForMode < 0.7f -> 1f
                valForMode < 2.0f -> 2f
                valForMode < 5.0f -> 3f
                valForMode < 15.0f -> 4f
                valForMode < 30.0f -> 5f
                valForMode < 50.0f -> 5.5f
                valForMode < 80.0f -> 6.0f
                else -> 7.0f
            }
            DisplayMode.PGD -> when {
                valForMode < 0.1f -> 0f
                valForMode < 0.5f -> 1f
                valForMode < 2.0f -> 2f
                valForMode < 5.0f -> 3f
                valForMode < 15.0f -> 4f
                valForMode < 30.0f -> 5f
                else -> 6.0f
            }
        }
        return getShindoColor(equivalentShindo)
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
    val affectedPrefectures: List<String> = emptyList(),
    val detailedAreas: List<String> = emptyList(), // 市町村・細分化地域（例: 大阪府北部、伊豆大島、能登、中越など）
    val tsunamiWarning: String = "津波の心配なし" // "大津波警報", "津波警報", "津波注意報", "津波の心配なし"
)

data class EarthquakeItem(
    val id: String,
    val time: String,
    val hypocenter: String,
    val maxScale: String, // "1", "2", "3", "4", "5-", "5+", "6-", "6+", "7"
    val magnitude: Double,
    val depthKm: Int,
    val tsunamiInfo: String, // "津波の心配なし", "津波注意報", "津波警報", "大津波警報"
    val lat: Double,
    val lon: Double,
    val intensityPoints: List<PrefectureIntensity> = emptyList(),
    val detailedRegions: List<String> = emptyList() // 詳しい震度観測市区町村・地域
)

data class PrefectureIntensity(
    val prefecture: String,
    val scale: String,
    val cities: List<String> = emptyList() // 観測市区町村名
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

object GeoProjection {
    const val MIN_LAT = 23.5
    const val MAX_LAT = 46.0
    const val MIN_LON = 122.5
    const val MAX_LON = 146.5

    // Conformal aspect ratio (width / height)
    // (MAX_LON - MIN_LON) * cos(35 deg) / (MAX_LAT - MIN_LAT)
    // 24.0 * 0.819152 / 22.5 = 0.87376
    const val MAP_ASPECT_RATIO = 0.87376f

    fun toNormX(lon: Double): Float {
        return ((lon - MIN_LON) / (MAX_LON - MIN_LON)).toFloat().coerceIn(0.01f, 0.99f)
    }

    fun toNormY(lat: Double): Float {
        return ((MAX_LAT - lat) / (MAX_LAT - MIN_LAT)).toFloat().coerceIn(0.01f, 0.99f)
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

data class PrefectureMarker(
    val name: String,
    val shortName: String,
    val lat: Double,
    val lon: Double,
    val mapX: Float = GeoProjection.toNormX(lon),
    val mapY: Float = GeoProjection.toNormY(lat)
)

val JAPAN_PREFECTURES: List<PrefectureMarker> = listOf(
    PrefectureMarker("北海道", "北海道", 43.550, 142.600),
    PrefectureMarker("青森県", "青森", 40.824, 140.740),
    PrefectureMarker("岩手県", "岩手", 39.550, 141.300),
    PrefectureMarker("宮城県", "宮城", 38.350, 140.871),
    PrefectureMarker("秋田県", "秋田", 39.718, 140.350),
    PrefectureMarker("山形県", "山形", 38.350, 140.250),
    PrefectureMarker("福島県", "福島", 37.450, 140.250),
    PrefectureMarker("茨城県", "茨城", 36.341, 140.446),
    PrefectureMarker("栃木県", "栃木", 36.650, 139.883),
    PrefectureMarker("群馬県", "群馬", 36.450, 138.950),
    PrefectureMarker("埼玉県", "埼玉", 35.950, 139.400),
    PrefectureMarker("千葉県", "千葉", 35.500, 140.200),
    PrefectureMarker("東京都", "東京", 35.689, 139.550),
    PrefectureMarker("神奈川県", "神奈川", 35.400, 139.450),
    PrefectureMarker("新潟県", "新潟", 37.500, 138.920),
    PrefectureMarker("富山県", "富山", 36.650, 137.211),
    PrefectureMarker("石川県", "石川", 36.750, 136.750),
    PrefectureMarker("福井県", "福井", 35.950, 136.221),
    PrefectureMarker("山梨県", "山梨", 35.664, 138.568),
    PrefectureMarker("長野県", "長野", 36.350, 138.100),
    PrefectureMarker("岐阜県", "岐阜", 35.391, 136.722),
    PrefectureMarker("静岡県", "静岡", 34.976, 138.383),
    PrefectureMarker("愛知県", "愛知", 35.180, 136.906),
    PrefectureMarker("三重県", "三重", 34.730, 136.508),
    PrefectureMarker("滋賀県", "滋賀", 35.004, 135.868),
    PrefectureMarker("京都府", "京都", 35.021, 135.755),
    PrefectureMarker("大阪府", "大阪", 34.686, 135.520),
    PrefectureMarker("兵庫県", "兵庫", 34.691, 135.183),
    PrefectureMarker("奈良県", "奈良", 34.685, 135.832),
    PrefectureMarker("和歌山県", "和歌山", 34.226, 135.167),
    PrefectureMarker("鳥取県", "鳥取", 35.503, 134.238),
    PrefectureMarker("島根県", "島根", 35.472, 133.050),
    PrefectureMarker("岡山県", "岡山", 34.661, 133.935),
    PrefectureMarker("広島県", "広島", 34.396, 132.459),
    PrefectureMarker("山口県", "山口", 34.186, 131.470),
    PrefectureMarker("徳島県", "徳島", 34.065, 134.559),
    PrefectureMarker("香川県", "香川", 34.340, 134.043),
    PrefectureMarker("愛媛県", "愛媛", 33.841, 132.766),
    PrefectureMarker("高知県", "高知", 33.559, 133.531),
    PrefectureMarker("福岡県", "福岡", 33.606, 130.418),
    PrefectureMarker("佐賀県", "佐賀", 33.249, 130.298),
    PrefectureMarker("長崎県", "長崎", 32.744, 129.873),
    PrefectureMarker("熊本県", "熊本", 32.789, 130.741),
    PrefectureMarker("大分県", "大分", 33.238, 131.612),
    PrefectureMarker("宮崎県", "宮崎", 31.911, 131.423),
    PrefectureMarker("鹿児島県", "鹿児島", 31.560, 130.558),
    PrefectureMarker("沖縄県", "沖縄", 26.212, 127.680)
)

