package com.example.data.local.repository

import com.example.data.local.dao.EEWNotificationDao
import com.example.data.local.entity.EEWNotificationEntity
import com.example.model.EEWAlert
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EEWNotificationRepository(
    private val dao: EEWNotificationDao
) {
    val allLogs: Flow<List<EEWNotificationEntity>> = dao.getAllLogs()

    suspend fun saveEEWAlert(alert: EEWAlert): Long {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        }
        val originStr = if (alert.originTimeMillis > 0) {
            dateFormat.format(Date(alert.originTimeMillis))
        } else {
            dateFormat.format(Date())
        }

        val entity = EEWNotificationEntity(
            alertId = alert.id.ifEmpty { "EEW-${System.currentTimeMillis()}" },
            reportNum = if (alert.reportNum > 0) "第${alert.reportNum}報" else "速報",
            originTime = originStr,
            hypocenter = alert.hypocenter,
            magnitude = alert.magnitude,
            depthKm = alert.depthKm,
            maxShindo = alert.maxShindo,
            isWarning = alert.isWarning,
            isDrill = alert.isDrill,
            affectedAreas = alert.affectedPrefectures.joinToString(", "),
            latitude = alert.lat,
            longitude = alert.lon,
            receivedAt = System.currentTimeMillis()
        )
        val validEntity = if (entity.latitude == 0.0 || entity.longitude == 0.0) {
            val coords = resolveCoordinates(entity.hypocenter)
            entity.copy(latitude = coords.first, longitude = coords.second)
        } else {
            entity
        }
        return dao.insertLog(validEntity)
    }

    suspend fun insertCustomLog(entity: EEWNotificationEntity): Long {
        val validEntity = if (entity.latitude == 0.0 || entity.longitude == 0.0) {
            val coords = resolveCoordinates(entity.hypocenter)
            entity.copy(latitude = coords.first, longitude = coords.second)
        } else {
            entity
        }
        return dao.insertLog(validEntity)
    }

    suspend fun saveEarthquakeItem(item: com.example.model.EarthquakeItem): Long {
        val existing = dao.getLogByAlertId(item.id)
        if (existing != null) return existing.id

        val (lat, lon) = if (item.lat != 0.0 && item.lon != 0.0) {
            Pair(item.lat, item.lon)
        } else {
            resolveCoordinates(item.hypocenter)
        }

        val entity = EEWNotificationEntity(
            alertId = item.id,
            reportNum = if (item.tsunamiInfo.isNotEmpty() && item.tsunamiInfo != "津波の心配なし") item.tsunamiInfo else "震度速報",
            originTime = item.time,
            hypocenter = item.hypocenter,
            magnitude = item.magnitude.toFloat(),
            depthKm = item.depthKm,
            maxShindo = item.maxScale,
            isWarning = item.maxScale in listOf("5弱", "5強", "6弱", "6強", "7") || item.tsunamiInfo.contains("警報"),
            isDrill = false,
            affectedAreas = item.intensityPoints.joinToString(" / ") { "${it.prefecture}(震度${it.scale})" },
            latitude = lat,
            longitude = lon,
            receivedAt = System.currentTimeMillis()
        )
        return dao.insertLog(entity)
    }

    suspend fun importP2PEarthquakes(items: List<com.example.model.EarthquakeItem>): Int {
        var count = 0
        for (item in items) {
            val existing = dao.getLogByAlertId(item.id)
            if (existing == null) {
                saveEarthquakeItem(item)
                count++
            }
        }
        return count
    }

    fun resolveCoordinates(hypocenter: String): Pair<Double, Double> {
        val h = hypocenter.trim()
        return when {
            h.contains("能登") || h.contains("石川") -> Pair(37.50, 137.20)
            h.contains("福島") -> Pair(37.70, 141.60)
            h.contains("日向灘") || h.contains("宮崎") -> Pair(31.80, 131.70)
            h.contains("相模トラフ") || h.contains("相模湾") -> Pair(35.10, 139.30)
            h.contains("東京湾") || h.contains("首都直下") || h.contains("東京") -> Pair(35.65, 139.85)
            h.contains("千葉") || h.contains("房総") -> Pair(35.40, 140.50)
            h.contains("三陸") || h.contains("岩手") -> Pair(39.00, 142.50)
            h.contains("宮城") || h.contains("仙台") -> Pair(38.30, 142.10)
            h.contains("茨城") -> Pair(36.35, 140.45)
            h.contains("静岡") || h.contains("駿河湾") -> Pair(34.80, 138.50)
            h.contains("南海トラフ") || h.contains("潮岬") -> Pair(33.20, 135.80)
            h.contains("熊本") -> Pair(32.78, 130.74)
            h.contains("鹿児島") || h.contains("大隅") -> Pair(31.50, 130.60)
            h.contains("北海道") || h.contains("胆振") || h.contains("十勝") -> Pair(42.70, 142.00)
            h.contains("釧路") || h.contains("根室") -> Pair(43.00, 144.50)
            h.contains("青森") || h.contains("津軽") -> Pair(40.80, 140.70)
            h.contains("秋田") -> Pair(39.70, 140.10)
            h.contains("山形") || h.contains("庄内") -> Pair(38.80, 139.80)
            h.contains("新潟") || h.contains("佐渡") -> Pair(37.50, 138.90)
            h.contains("富山") -> Pair(36.70, 137.20)
            h.contains("福井") -> Pair(36.00, 136.20)
            h.contains("長野") -> Pair(36.35, 138.10)
            h.contains("岐阜") || h.contains("飛騨") -> Pair(35.80, 137.00)
            h.contains("愛知") || h.contains("三河") -> Pair(35.00, 137.00)
            h.contains("三重") || h.contains("伊勢湾") -> Pair(34.50, 136.50)
            h.contains("和歌山") || h.contains("紀伊") -> Pair(34.00, 135.20)
            h.contains("京都") -> Pair(35.05, 135.75)
            h.contains("大阪") -> Pair(34.69, 135.50)
            h.contains("兵庫") || h.contains("淡路") -> Pair(34.70, 135.00)
            h.contains("鳥取") -> Pair(35.40, 133.80)
            h.contains("島根") -> Pair(35.20, 132.80)
            h.contains("岡山") -> Pair(34.70, 133.90)
            h.contains("広島") -> Pair(34.40, 132.50)
            h.contains("山口") -> Pair(34.20, 131.50)
            h.contains("徳島") -> Pair(33.90, 134.50)
            h.contains("香川") -> Pair(34.30, 134.00)
            h.contains("愛媛") || h.contains("伊予") -> Pair(33.80, 132.80)
            h.contains("高知") || h.contains("土佐") -> Pair(33.50, 133.50)
            h.contains("福岡") -> Pair(33.60, 130.40)
            h.contains("大分") -> Pair(33.20, 131.60)
            h.contains("長崎") -> Pair(32.75, 129.85)
            h.contains("沖縄") || h.contains("宮古") || h.contains("八重山") -> Pair(26.20, 127.70)
            else -> Pair(36.00, 138.00) // Default central Japan
        }
    }

    suspend fun deleteLog(entity: EEWNotificationEntity) {
        dao.deleteLog(entity)
    }

    suspend fun clearAll() {
        dao.clearAllLogs()
    }

    suspend fun seedInitialHistoryIfEmpty() {
        val count = dao.countStatic()
        if (count == 0) {
            val now = System.currentTimeMillis()
            val initialSeeds = listOf(
                EEWNotificationEntity(
                    alertId = "EEW-20240101-01",
                    reportNum = "第7報(最終報)",
                    originTime = "2024/01/01 16:10:09",
                    hypocenter = "能登半島沖",
                    magnitude = 7.6f,
                    depthKm = 16,
                    maxShindo = "7",
                    isWarning = true,
                    isDrill = false,
                    affectedAreas = "石川県, 富山県, 新潟県, 福井県",
                    latitude = 37.5,
                    longitude = 137.2,
                    receivedAt = now - 3600000 * 48
                ),
                EEWNotificationEntity(
                    alertId = "EEW-20220316-01",
                    reportNum = "第5報",
                    originTime = "2022/03/16 23:36:33",
                    hypocenter = "福島県沖",
                    magnitude = 7.4f,
                    depthKm = 57,
                    maxShindo = "6強",
                    isWarning = true,
                    isDrill = false,
                    affectedAreas = "宮城県, 福島県, 岩手県, 山形県",
                    latitude = 37.7,
                    longitude = 141.6,
                    receivedAt = now - 3600000 * 24
                ),
                EEWNotificationEntity(
                    alertId = "EEW-20240808-01",
                    reportNum = "第3報",
                    originTime = "2024/08/08 16:42:55",
                    hypocenter = "日向灘",
                    magnitude = 7.1f,
                    depthKm = 30,
                    maxShindo = "6弱",
                    isWarning = true,
                    isDrill = false,
                    affectedAreas = "宮崎県, 鹿児島県, 高知県, 大分県",
                    latitude = 31.8,
                    longitude = 131.7,
                    receivedAt = now - 3600000 * 12
                ),
                EEWNotificationEntity(
                    alertId = "EEW-DRILL-01",
                    reportNum = "訓練第1報",
                    originTime = "2026/10/12 09:00:00",
                    hypocenter = "相模トラフ",
                    magnitude = 7.3f,
                    depthKm = 30,
                    maxShindo = "6強",
                    isWarning = true,
                    isDrill = true,
                    affectedAreas = "神奈川県, 東京都, 千葉県, 静岡県",
                    latitude = 35.1,
                    longitude = 139.3,
                    receivedAt = now - 3600000 * 2
                )
            )
            initialSeeds.forEach { dao.insertLog(it) }
        }
    }
}
