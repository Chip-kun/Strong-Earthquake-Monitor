package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.model.DepthMode
import com.example.model.EEWAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class KmoniImageType(val code: String, val label: String) {
    SHINDO("jma", "リアルタイム震度"),
    PGA("acmap", "最大加速度 (PGA)"),
    PGV("vcmap", "最大速度 (PGV)"),
    PGD("dcmap", "最大変位 (PGD)")
}

enum class KmoniFetchStatus(val label: String) {
    IDLE("待機中"),
    FETCHING("画像取得中"),
    SUCCESS("正常取得"),
    DELAY_ADJUST("遅延調整中"),
    ERROR("取得失敗")
}

data class KmoniFetchStats(
    val status: KmoniFetchStatus = KmoniFetchStatus.IDLE,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val lastLatencyMs: Long = 0,
    val currentDelayOffsetSec: Int = 2,
    val lastSuccessTimestamp: String = "",
    val latestImageUrl: String = "",
    val errorMessage: String? = null
)

/**
 * 防災科学技術研究所 (NIED) の強震モニタAPIからリアルタイム画像を定期取得（1秒周期等）するロジック。
 *
 * 【対応機能】
 * - 地表 (s) / 地中 (b) の観測点画像の自動切り替え
 * - リアルタイム震度 (jma)、最大加速度 (acmap)、最大速度 (vcmap)、最大変位 (dcmap) の取得
 * - JSTタイムスタンプ生成およびサーバー遅延（1〜3秒）の動的補正
 * - OkHttpによる高速HTTPフェッチ、Bitmapデコード、キャッシュ
 * - 防災科研EEW JSONエンドポイント（/webservice/hypo/eew/）の同時チェック
 */
class KmoniImageFetcher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val TAG = "KmoniImageFetcher"
        const val BASE_URL = "http://www.kmoni.bosai.go.jp"
        const val DEFAULT_INTERVAL_MS = 1000L
        const val MIN_DELAY_OFFSET_SEC = 1
        const val MAX_DELAY_OFFSET_SEC = 5
    }

    private var pollingJob: Job? = null
    private var isRunning = false
    private var pollIntervalMs = DEFAULT_INTERVAL_MS
    private var delayOffsetSeconds = 2 // Typical NIED image server lag is 1-2 seconds

    private val _depthMode = MutableStateFlow(DepthMode.SURFACE)
    val depthMode: StateFlow<DepthMode> = _depthMode.asStateFlow()

    private val _imageType = MutableStateFlow(KmoniImageType.SHINDO)
    val imageType: StateFlow<KmoniImageType> = _imageType.asStateFlow()

    private val _latestBitmap = MutableStateFlow<Bitmap?>(null)
    val latestBitmap: StateFlow<Bitmap?> = _latestBitmap.asStateFlow()

    private val _latestTimestamp = MutableStateFlow("")
    val latestTimestamp: StateFlow<String> = _latestTimestamp.asStateFlow()

    private val _fetchStats = MutableStateFlow(KmoniFetchStats())
    val fetchStats: StateFlow<KmoniFetchStats> = _fetchStats.asStateFlow()

    // Listener for EEW alerts detected from NIED JSON API
    var onNiedEEWDetected: ((EEWAlert) -> Unit)? = null

    private val jstDateFormat = SimpleDateFormat("yyyyMMdd", Locale.JAPAN).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tokyo")
    }
    private val jstTimeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.JAPAN).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tokyo")
    }
    private val displayDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tokyo")
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        startPollingLoop()
        Log.i(TAG, "KmoniImageFetcher started with interval ${pollIntervalMs}ms")
    }

    fun stop() {
        isRunning = false
        pollingJob?.cancel()
        _fetchStats.value = _fetchStats.value.copy(status = KmoniFetchStatus.IDLE)
        Log.i(TAG, "KmoniImageFetcher stopped")
    }

    fun setDepthMode(depth: DepthMode) {
        if (_depthMode.value != depth) {
            _depthMode.value = depth
            triggerImmediateFetch()
        }
    }

    fun setImageType(type: KmoniImageType) {
        if (_imageType.value != type) {
            _imageType.value = type
            triggerImmediateFetch()
        }
    }

    fun setInterval(intervalMs: Long) {
        pollIntervalMs = max(500L, intervalMs)
        if (isRunning) {
            startPollingLoop()
        }
    }

    fun triggerImmediateFetch() {
        scope.launch {
            fetchLatestImage()
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && isRunning) {
                fetchLatestImage()
                delay(pollIntervalMs)
            }
        }
    }

    /**
     * Builds the target URL for NIED Kyoshin Monitor image
     * Example: http://www.kmoni.bosai.go.jp/data/map_img/RealtimeImg/jma_s/20261017/20261017123456.jma_s.gif
     */
    fun buildImageUrl(date: Date, type: KmoniImageType, depth: DepthMode): String {
        val ymd = jstDateFormat.format(date)
        val ymdHms = jstTimeFormat.format(date)
        val depthSuffix = if (depth == DepthMode.BOREHOLE) "b" else "s"
        val typeCode = type.code
        val fileName = "${typeCode}_${depthSuffix}"

        return "$BASE_URL/data/map_img/RealtimeImg/$fileName/$ymd/$ymdHms.$fileName.gif"
    }

    /**
     * Builds the target URL for NIED EEW estimation / PS-wave overlay
     */
    fun buildEEWOverlayUrl(date: Date, isPSWave: Boolean = false): String {
        val ymd = jstDateFormat.format(date)
        val ymdHms = jstTimeFormat.format(date)
        val folder = if (isPSWave) "PSWaveImg/eew" else "EstShindoImg/eew"
        return "$BASE_URL/data/map_img/$folder/$ymd/$ymdHms.eew.gif"
    }

    /**
     * Builds the target URL for NIED EEW JSON service
     */
    fun buildEEWJsonUrl(date: Date): String {
        val ymdHms = jstTimeFormat.format(date)
        return "$BASE_URL/webservice/hypo/eew/$ymdHms.json"
    }

    private suspend fun fetchLatestImage() {
        val startTime = System.currentTimeMillis()
        val currentStats = _fetchStats.value
        _fetchStats.value = currentStats.copy(status = KmoniFetchStatus.FETCHING)

        // Try primary timestamp (now - delayOffsetSeconds)
        val targetMillis = startTime - (delayOffsetSeconds * 1000L)
        val targetDate = Date(targetMillis)
        val targetUrl = buildImageUrl(targetDate, _imageType.value, _depthMode.value)

        var resultBitmap: Bitmap? = null
        var successfulUrl = targetUrl
        var successfulDate = targetDate

        try {
            resultBitmap = downloadBitmap(targetUrl)

            if (resultBitmap == null) {
                // If 404/not available yet, try fallback 1 second earlier
                val fallbackMillis = targetMillis - 1000L
                val fallbackDate = Date(fallbackMillis)
                val fallbackUrl = buildImageUrl(fallbackDate, _imageType.value, _depthMode.value)

                resultBitmap = downloadBitmap(fallbackUrl)
                if (resultBitmap != null) {
                    successfulUrl = fallbackUrl
                    successfulDate = fallbackDate
                    // Slightly increase delay offset to stabilize stream
                    delayOffsetSeconds = min(MAX_DELAY_OFFSET_SEC, delayOffsetSeconds + 1)
                }
            } else {
                // Succeeded directly. If delayOffset was high, gradually optimize
                if (delayOffsetSeconds > MIN_DELAY_OFFSET_SEC && currentStats.successCount % 10L == 0L) {
                    delayOffsetSeconds = max(MIN_DELAY_OFFSET_SEC, delayOffsetSeconds - 1)
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (resultBitmap != null) {
                _latestBitmap.value = resultBitmap
                val formattedTime = displayDateFormat.format(successfulDate)
                _latestTimestamp.value = formattedTime

                _fetchStats.value = currentStats.copy(
                    status = KmoniFetchStatus.SUCCESS,
                    successCount = currentStats.successCount + 1,
                    lastLatencyMs = elapsed,
                    currentDelayOffsetSec = delayOffsetSeconds,
                    lastSuccessTimestamp = formattedTime,
                    latestImageUrl = successfulUrl,
                    errorMessage = null
                )

                // Periodically check EEW status JSON from NIED
                if (currentStats.successCount % 3L == 0L) {
                    checkNiedEEW(successfulDate)
                }
            } else {
                _fetchStats.value = currentStats.copy(
                    status = KmoniFetchStatus.DELAY_ADJUST,
                    failureCount = currentStats.failureCount + 1,
                    lastLatencyMs = elapsed,
                    currentDelayOffsetSec = delayOffsetSeconds,
                    errorMessage = "画像準備中 (${delayOffsetSeconds}秒遅延調整)"
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.w(TAG, "Error fetching Kmoni image: ${e.message}")
            _fetchStats.value = currentStats.copy(
                status = KmoniFetchStatus.ERROR,
                failureCount = currentStats.failureCount + 1,
                lastLatencyMs = elapsed,
                errorMessage = e.message ?: "通信エラー"
            )
        }
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; KyoshinMonitorApp/1.0)")
            .header("Referer", "http://www.kmoni.bosai.go.jp/")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Image download failed for $url: ${e.message}")
        }
        return@withContext null
    }

    private suspend fun checkNiedEEW(date: Date) = withContext(Dispatchers.IO) {
        val jsonUrl = buildEEWJsonUrl(date)
        val request = Request.Builder()
            .url(jsonUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; KyoshinMonitorApp/1.0)")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)
                    val result = json.optJSONObject("result")
                    val isEEW = result?.optBoolean("is_eew", false) ?: false

                    if (isEEW) {
                        val regionName = json.optString("region_name", "緊急地震速報")
                        val mag = json.optString("magunitude", "5.0").toFloatOrNull() ?: 5.0f
                        val maxCalc = json.optString("calcintensity", "4")
                        val lat = json.optString("latitude", "35.0").toDoubleOrNull() ?: 35.0
                        val lon = json.optString("longitude", "138.0").toDoubleOrNull() ?: 138.0
                        val depth = json.optString("depth", "10km").replace("km", "").trim().toIntOrNull() ?: 10
                        val isWarn = json.optBoolean("is_warn", false)

                        val alert = EEWAlert(
                            id = "nied_${jstTimeFormat.format(date)}",
                            reportNum = json.optString("report_num", "1").toIntOrNull() ?: 1,
                            isFinal = json.optBoolean("is_final", false),
                            isWarning = isWarn,
                            hypocenter = regionName,
                            lat = lat,
                            lon = lon,
                            depthKm = depth,
                            magnitude = mag,
                            maxShindo = maxCalc,
                            originTimeMillis = System.currentTimeMillis(),
                            countdownSeconds = 10,
                            isActive = true,
                            isDrill = false,
                            tsunamiWarning = "津波情報確認中"
                        )
                        Log.i(TAG, "NIED EEW detected: $alert")
                        onNiedEEWDetected?.invoke(alert)
                    }
                }
            }
        } catch (e: Exception) {
            // Non-critical, ignore
        }
    }
}
