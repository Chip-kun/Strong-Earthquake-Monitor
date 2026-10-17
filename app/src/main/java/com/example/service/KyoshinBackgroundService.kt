package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.VoiceAlertManager
import com.example.data.local.AppDatabase
import com.example.data.local.repository.EEWNotificationRepository
import com.example.network.NetworkClient
import com.example.network.toEEWAlert
import com.example.network.toEarthquakeItem
import com.example.notification.KyoshinNotificationManager
import kotlinx.coroutines.*

class KyoshinBackgroundService : Service() {

    private val tag = "KyoshinBgService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isMonitoring = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var eewRepository: EEWNotificationRepository
    private lateinit var voiceAlertManager: VoiceAlertManager
    private var bgWebSocketClient: com.example.network.KyoshinWebSocketClient? = null

    override fun onCreate() {
        super.onCreate()
        KyoshinNotificationManager.initNotificationChannels(this)

        val dao = AppDatabase.getInstance(applicationContext).eewNotificationDao()
        eewRepository = EEWNotificationRepository(dao)
        voiceAlertManager = VoiceAlertManager(applicationContext)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KyoshinMonitor:BgMonitorWakeLock")

        startForegroundNotification()
        startMonitoringLoop()
        startWebSocketListener()
        Log.i(tag, "KyoshinBackgroundService created and running")
    }

    private fun startWebSocketListener() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ws = com.example.network.KyoshinWebSocketClient(serviceScope)
        bgWebSocketClient = ws

        ws.onEEWReceived = { alert ->
            val lastEewId = prefs.getString(KEY_LAST_EEW_ID, null)
            if (alert.id != lastEewId) {
                prefs.edit().putString(KEY_LAST_EEW_ID, alert.id).apply()
                Log.i(tag, "WS PUSH: EEW Alert: ${alert.hypocenter} M${alert.magnitude}")
                wakeLock?.acquire(15000L)
                serviceScope.launch {
                    eewRepository.saveEEWAlert(alert)
                }
                KyoshinNotificationManager.notifyEEW(applicationContext, alert)
                voiceAlertManager.announceDetailedEEW(alert)
            }
        }

        ws.onEarthquakeReceived = { eq ->
            val lastEqId = prefs.getString(KEY_LAST_EQ_ID, null)
            if (eq.id != lastEqId) {
                prefs.edit().putString(KEY_LAST_EQ_ID, eq.id).apply()
                Log.i(tag, "WS PUSH: Earthquake: ${eq.hypocenter} maxScale=${eq.maxScale}")
                wakeLock?.acquire(12000L)
                serviceScope.launch {
                    eewRepository.saveEarthquakeItem(eq)
                }
                KyoshinNotificationManager.notifyEarthquakeReport(applicationContext, eq)
                voiceAlertManager.announceDetailedEarthquakeReport(eq)
            }
        }

        ws.connect()
    }

    private fun startForegroundNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            KyoshinNotificationManager.NOTIF_ID_SERVICE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, KyoshinNotificationManager.CHANNEL_SERVICE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("強震モニター 常駐監視中")
            .setContentText("24時間リアルタイムで地震速報・緊急地震速報を監視しています")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            ServiceCompat.startForeground(
                this,
                KyoshinNotificationManager.NOTIF_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(KyoshinNotificationManager.NOTIF_ID_SERVICE, notification)
        }
    }

    private fun startMonitoringLoop() {
        if (isMonitoring) return
        isMonitoring = true

        serviceScope.launch {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Initial check to prime last known IDs if first run
            try {
                val initialList = NetworkClient.p2pService.getEarthquakeHistory(codes = "551,556", limit = 5)
                val initialEq = initialList.firstNotNullOfOrNull { it.toEarthquakeItem() }
                if (initialEq != null && !prefs.contains(KEY_LAST_EQ_ID)) {
                    prefs.edit().putString(KEY_LAST_EQ_ID, initialEq.id).apply()
                }
            } catch (e: Exception) {
                Log.w(tag, "Initial history fetch failed: ${e.message}")
            }

            while (isActive) {
                try {
                    checkAndNotifyUpdates(prefs)
                } catch (e: Exception) {
                    Log.w(tag, "Error during background poll: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkAndNotifyUpdates(prefs: android.content.SharedPreferences) {
        val list = NetworkClient.p2pService.getEarthquakeHistory(codes = "551,556", limit = 10)
        if (list.isEmpty()) return

        // 1. Check for Emergency Earthquake Warning (EEW - Code 556)
        val eewResponses = list.filter { it.code == 556 }
        for (resp in eewResponses) {
            val eewAlert = resp.toEEWAlert() ?: continue
            val lastEewId = prefs.getString(KEY_LAST_EEW_ID, null)

            if (eewAlert.id != lastEewId) {
                prefs.edit().putString(KEY_LAST_EEW_ID, eewAlert.id).apply()
                Log.i(tag, "NEW EEW ALERT in background: ${eewAlert.hypocenter} M${eewAlert.magnitude}")

                // Acquire brief wakelock for alert processing
                wakeLock?.acquire(15000L)

                // Save to Room Database
                eewRepository.saveEEWAlert(eewAlert)

                // High Priority Heads-up Notification
                KyoshinNotificationManager.notifyEEW(applicationContext, eewAlert)

                // Voice announcement
                voiceAlertManager.announceDetailedEEW(eewAlert)
            }
        }

        // 2. Check for newly confirmed Earthquake report (Code 551)
        val eqItems = list.mapNotNull { it.toEarthquakeItem() }
        val latestEq = eqItems.firstOrNull()

        if (latestEq != null) {
            val lastEqId = prefs.getString(KEY_LAST_EQ_ID, null)
            if (latestEq.id != lastEqId) {
                prefs.edit().putString(KEY_LAST_EQ_ID, latestEq.id).apply()
                Log.i(tag, "NEW EARTHQUAKE in background: ${latestEq.hypocenter} maxScale=${latestEq.maxScale}")

                // Save to Room database for history map review
                eewRepository.saveEarthquakeItem(latestEq)

                // Acquire wakelock to ensure notification and voice are completed
                wakeLock?.acquire(12000L)

                // Trigger notification if not purely negligible micro-tremor
                KyoshinNotificationManager.notifyEarthquakeReport(applicationContext, latestEq)

                // Voice announcement
                voiceAlertManager.announceDetailedEarthquakeReport(latestEq)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TEST_ALERT) {
            serviceScope.launch {
                delay(3000) // 3-second delay so user can close or lock screen
                val demoItem = com.example.model.EarthquakeItem(
                    id = "test_bg_${System.currentTimeMillis()}",
                    time = "たった今",
                    hypocenter = "バックグラウンド通知テスト（東京湾）",
                    maxScale = "4",
                    magnitude = 5.2,
                    depthKm = 30,
                    tsunamiInfo = "津波の心配なし",
                    lat = 35.5,
                    lon = 139.8,
                    intensityPoints = listOf(
                        com.example.model.PrefectureIntensity("東京都", "4", listOf("千代田区", "港区", "品川区")),
                        com.example.model.PrefectureIntensity("神奈川県", "3", listOf("横浜市中区", "川崎市川崎区"))
                    ),
                    detailedRegions = listOf("東京湾", "東京都23区", "神奈川県東部")
                )
                wakeLock?.acquire(10000L)
                eewRepository.saveEarthquakeItem(demoItem)
                KyoshinNotificationManager.notifyEarthquakeReport(applicationContext, demoItem)
                voiceAlertManager.announceDetailedEarthquakeReport(demoItem)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        bgWebSocketClient?.disconnect()
        bgWebSocketClient = null
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        Log.i(tag, "KyoshinBackgroundService destroyed")
    }

    companion object {
        const val PREFS_NAME = "kyoshin_bg_monitor_prefs"
        const val KEY_MONITORING_ENABLED = "bg_monitoring_enabled"
        const val KEY_LAST_EQ_ID = "last_notified_eq_id"
        const val KEY_LAST_EEW_ID = "last_notified_eew_id"

        private const val POLL_INTERVAL_MS = 6000L
        private const val ACTION_STOP = "com.example.service.STOP_MONITOR"
        private const val ACTION_TEST_ALERT = "com.example.service.TEST_ALERT"

        fun start(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MONITORING_ENABLED, true).apply()

            val intent = Intent(context, KyoshinBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MONITORING_ENABLED, false).apply()

            val intent = Intent(context, KyoshinBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MONITORING_ENABLED, true) // Enabled by default
        }

        fun triggerTestNotification(context: Context) {
            val intent = Intent(context, KyoshinBackgroundService::class.java).apply {
                action = ACTION_TEST_ALERT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
