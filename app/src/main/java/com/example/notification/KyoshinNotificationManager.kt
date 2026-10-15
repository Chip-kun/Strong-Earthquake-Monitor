package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.EEWAlert
import com.example.model.EarthquakeItem

object KyoshinNotificationManager {

    const val CHANNEL_EEW_ID = "channel_eew_critical"
    const val CHANNEL_EARTHQUAKE_ID = "channel_earthquake_reports"
    const val CHANNEL_SEISMOMETER_ID = "channel_seismometer_shake"
    const val CHANNEL_SERVICE_ID = "channel_service_monitoring"

    const val NOTIF_ID_SERVICE = 1000
    private const val NOTIF_ID_EEW = 1001
    private const val NOTIF_ID_SHAKE = 1002
    private const val NOTIF_ID_EQ_BASE = 2000

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Channel 0: Background Monitoring Foreground Service (Silent)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "常駐バックグラウンド監視サービス",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "アプリ終了後も地震・緊急地震速報を常時監視するための常駐通知"
                enableVibration(false)
                setShowBadge(false)
            }

            // Channel 1: Critical EEW (Emergency)
            val eewChannel = NotificationChannel(
                CHANNEL_EEW_ID,
                "緊急地震速報 (EEW)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "気象庁発表の緊急地震速報を受信した際の最高優先度通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Channel 2: Real Earthquake Info (JMA / P2P reports)
            val eqChannel = NotificationChannel(
                CHANNEL_EARTHQUAKE_ID,
                "地震情報（震度速報・震源情報）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "各地の震度および各地震の震源・津波情報の通知"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Channel 3: My Seismometer Shake Detection
            val shakeChannel = NotificationChannel(
                CHANNEL_SEISMOMETER_ID,
                "マイ地震計（端末揺れ検知）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "端末内蔵の加速度センサーによる急激な揺れ検知通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, eewChannel, eqChannel, shakeChannel)
            )
        }
    }

    /**
     * Post notification for Emergency Earthquake Warning (EEW)
     */
    fun notifyEEW(context: Context, alert: EEWAlert) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_EEW,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val title = if (alert.isWarning) "【緊急地震速報（警報）】強い揺れに警戒！" else "【緊急地震速報（予報）】"
            val body = "${alert.hypocenter} M${String.format("%.1f", alert.magnitude)} 最大震度${alert.maxShindo} (深さ${alert.depthKm}km)"

            val bigTextBuilder = StringBuilder()
            bigTextBuilder.append(body).append("\n")
            if (alert.affectedPrefectures.isNotEmpty()) {
                bigTextBuilder.append("警戒都道府県: ").append(alert.affectedPrefectures.joinToString("、")).append("\n")
            }
            if (alert.detailedAreas.isNotEmpty()) {
                bigTextBuilder.append("詳しい警戒地域: ").append(alert.detailedAreas.joinToString("、")).append("\n")
            }
            if (alert.tsunamiWarning.isNotEmpty() && alert.tsunamiWarning != "津波の心配なし") {
                bigTextBuilder.append("⚠️ ").append(alert.tsunamiWarning).append("（海岸から直ちに離れてください）\n")
            }
            bigTextBuilder.append("身の安全を確保してください。")

            val notification = NotificationCompat.Builder(context, CHANNEL_EEW_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBuilder.toString()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(0xFFEF4444.toInt()) // Red
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIF_ID_EEW, notification)
        } catch (e: SecurityException) {
            // Notifications permission might be denied
        } catch (e: Exception) {
            // General exception safety
        }
    }

    /**
     * Post notification when device seismometer detects strong shaking
     */
    fun notifyDeviceShake(context: Context, gal: Float, shindoText: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_SHAKE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val title = "【マイ地震計】強い揺れを検知しました"
            val body = "瞬時加速度: ${String.format("%.1f", gal)} gal / 推定震度: $shindoText"

            val notification = NotificationCompat.Builder(context, CHANNEL_SEISMOMETER_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$body\n内蔵加速度センサーが急激な揺れを感知しました。転倒物等にご注意ください。")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(0xFFF59E0B.toInt()) // Amber
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIF_ID_SHAKE, notification)
        } catch (e: SecurityException) {
            // Permission check fallback
        } catch (e: Exception) {
            // Safety
        }
    }

    /**
     * Post notification for newly confirmed Earthquake report
     */
    fun notifyEarthquakeReport(context: Context, item: EarthquakeItem) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val notifId = NOTIF_ID_EQ_BASE + (item.id.hashCode() and 0x7FFF)
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val title = "地震情報: ${item.hypocenter} 最大震度${item.maxScale}"
            val body = "${item.time}頃 M${item.magnitude} 深さ約${item.depthKm}km"

            val bigTextBuilder = StringBuilder()
            bigTextBuilder.append(body).append("\n")
            if (item.tsunamiInfo.isNotEmpty()) {
                val prefix = if (item.tsunamiInfo.contains("警報") || item.tsunamiInfo.contains("注意報")) "⚠️ " else "ℹ️ "
                bigTextBuilder.append(prefix).append(item.tsunamiInfo).append("\n")
            }

            // Detailed cities and prefectures
            val topIntensityPoints = item.intensityPoints.filter { it.scale == item.maxScale }
            if (topIntensityPoints.isNotEmpty()) {
                val prefs = topIntensityPoints.map { it.prefecture }.distinct().joinToString("、")
                bigTextBuilder.append("最大震度観測: ").append(prefs).append("\n")
                val cities = topIntensityPoints.flatMap { it.cities }.distinct()
                if (cities.isNotEmpty()) {
                    bigTextBuilder.append("詳しい市町村: ").append(cities.take(8).joinToString("、")).append("\n")
                }
            } else if (item.detailedRegions.isNotEmpty()) {
                bigTextBuilder.append("詳しい観測地域: ").append(item.detailedRegions.take(6).joinToString("、")).append("\n")
            }

            val isTsunami = item.tsunamiInfo.contains("警報") || item.tsunamiInfo.contains("注意報")

            val notification = NotificationCompat.Builder(context, CHANNEL_EARTHQUAKE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("$body (${item.tsunamiInfo})")
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBuilder.toString().trimEnd()))
                .setPriority(if (isTsunami) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(if (isTsunami) 0xFFEF4444.toInt() else 0xFF38BDF8.toInt())
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            // Permission denied
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}
