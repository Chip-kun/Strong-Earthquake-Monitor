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
        return dao.insertLog(entity)
    }

    suspend fun insertCustomLog(entity: EEWNotificationEntity): Long {
        return dao.insertLog(entity)
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
