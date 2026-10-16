package com.example.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.audio.VoiceAlertManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EEWNotificationEntity
import com.example.network.NetworkClient
import com.example.network.toEEWAlert
import com.example.network.toEarthquakeItem
import com.example.notification.KyoshinNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * WorkManager Foreground Service Worker that listens for incoming
 * Emergency Earthquake Warnings (EEW) and earthquake reports, triggering
 * high-priority notifications and audio voice alerts even when the app
 * is in the background.
 */
class EEWBackgroundWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "EEWBackgroundWorker"
    private val voiceAlertManager by lazy { VoiceAlertManager(applicationContext) }
    private val database by lazy { AppDatabase.getInstance(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Promote this worker to a Foreground Service
            try {
                setForeground(createForegroundInfo("緊急地震速報・地震通知を常時監視中（音声警告待機）"))
            } catch (e: Exception) {
                Log.w(tag, "Foreground service setup in worker fallback: ${e.message}")
            }

            val prefs = applicationContext.getSharedPreferences("eew_worker_prefs", Context.MODE_PRIVATE)

            val startTime = System.currentTimeMillis()
            while (!isStopped) {
                try {
                    checkAndNotifyIncoming(prefs)
                } catch (e: Exception) {
                    Log.e(tag, "Error during earthquake poll in worker: ${e.message}")
                }

                // Poll every 5 seconds for rapid earthquake detection
                delay(5000)

                // Run for up to 10 minutes per execution window before finishing successfully
                if (System.currentTimeMillis() - startTime > 10 * 60 * 1000) {
                    break
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "EEWBackgroundWorker failure", e)
            Result.retry()
        }
    }

    private suspend fun checkAndNotifyIncoming(prefs: android.content.SharedPreferences) {
        // Fetch recent reports and EEW alerts
        val historyList = try {
            NetworkClient.p2pService.getEarthquakeHistory(codes = "551,556", limit = 10)
        } catch (e: Exception) {
            emptyList()
        }

        // 1. Check for incoming Emergency Earthquake Warnings (Code 556)
        val activeEEW = historyList.firstNotNullOfOrNull { it.toEEWAlert() }
        if (activeEEW != null) {
            val lastNotifiedEEW = prefs.getString("last_notified_eew_id", null)
            val alertKey = "${activeEEW.hypocenter}_${activeEEW.magnitude}_${activeEEW.maxShindo}"
            if (alertKey != lastNotifiedEEW) {
                prefs.edit().putString("last_notified_eew_id", alertKey).apply()

                // High priority heads-up notification
                KyoshinNotificationManager.notifyEEW(applicationContext, activeEEW)

                // Audio Voice Alert in background
                voiceAlertManager.announceDetailedEEW(activeEEW)

                // Persist to Room database
                try {
                    database.eewNotificationDao().insertLog(
                        EEWNotificationEntity(
                            alertId = activeEEW.id,
                            reportNum = "${activeEEW.reportNum}",
                            originTime = "${activeEEW.originTimeMillis}",
                            hypocenter = activeEEW.hypocenter,
                            magnitude = activeEEW.magnitude,
                            depthKm = activeEEW.depthKm,
                            maxShindo = activeEEW.maxShindo,
                            isWarning = activeEEW.isWarning,
                            isDrill = false,
                            latitude = activeEEW.lat,
                            longitude = activeEEW.lon
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to persist EEW to db", e)
                }
            }
        }

        // 2. Check for incoming Earthquake Reports (Code 551)
        val latestEarthquake = historyList.firstNotNullOfOrNull { it.toEarthquakeItem() }
        if (latestEarthquake != null) {
            val lastNotifiedId = prefs.getString("last_notified_eq_id", null)
            if (latestEarthquake.id != lastNotifiedId) {
                prefs.edit().putString("last_notified_eq_id", latestEarthquake.id).apply()

                // Notify if significant (Shindo 3+ or Tsunami alert)
                if (latestEarthquake.maxScale >= "3" || latestEarthquake.tsunamiInfo != "津波の心配なし") {
                    KyoshinNotificationManager.notifyEarthquakeReport(applicationContext, latestEarthquake)
                    voiceAlertManager.announceDetailedEarthquakeReport(latestEarthquake)

                    try {
                        database.eewNotificationDao().insertLog(
                            EEWNotificationEntity(
                                alertId = latestEarthquake.id,
                                reportNum = "1",
                                originTime = latestEarthquake.time,
                                hypocenter = latestEarthquake.hypocenter,
                                magnitude = latestEarthquake.magnitude.toFloat(),
                                depthKm = latestEarthquake.depthKm,
                                maxShindo = latestEarthquake.maxScale,
                                isWarning = latestEarthquake.maxScale in listOf("5弱", "5強", "6弱", "6強", "7"),
                                isDrill = false,
                                latitude = latestEarthquake.lat,
                                longitude = latestEarthquake.lon
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to persist earthquake report", e)
                    }
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("緊急地震速報・地震通知を監視中（音声警告待機）")
    }

    private fun createForegroundInfo(content: String): ForegroundInfo {
        KyoshinNotificationManager.initNotificationChannels(applicationContext)
        val channelId = KyoshinNotificationManager.CHANNEL_SERVICE_ID
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("強震モニタ: 監視サービス")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 9002
    }
}
