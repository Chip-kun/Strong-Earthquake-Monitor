package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.network.NetworkClient
import com.example.network.toEarthquakeItem
import com.example.notification.KyoshinNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EEWBackgroundWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.p2pService.getEarthquakeHistory(limit = 5)
            val items = response.mapNotNull { it.toEarthquakeItem() }
            val latest = items.firstOrNull()

            if (latest != null) {
                val prefs = applicationContext.getSharedPreferences("eew_worker_prefs", Context.MODE_PRIVATE)
                val lastNotifiedId = prefs.getString("last_notified_eq_id", null)

                if (latest.id != lastNotifiedId) {
                    prefs.edit().putString("last_notified_eq_id", latest.id).apply()

                    if (latest.maxScale >= "3" || latest.tsunamiInfo != "津波の心配なし") {
                        KyoshinNotificationManager.notifyEarthquakeReport(applicationContext, latest)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
