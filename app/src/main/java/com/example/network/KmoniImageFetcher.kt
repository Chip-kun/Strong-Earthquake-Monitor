package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
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

enum class KmoniImageType(val code: String, val label: String) {
    SHINDO("jma", "リアルタイム震度"),
    PGA("acmap", "最大加速度 (PGA)"),
    PGV("vcmap", "最大速度 (PGV)"),
    PGD("dcmap", "最大変位 (PGD)")
}

enum class KmoniFetchStatus(val label: String) {
    IDLE("待機中"),
    SYNCING("時刻同期中"),
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
    val currentDelayOffsetSec: Int = 1,
    val lastSuccessTimestamp: String = "",
    val latestImageUrl: String = "",
    val errorMessage: String? = null
)

/**
 * 防災科学技術研究所 (NIED) の強震モニタAPIからリアルタイム画像を定期取得（1秒周期等）するロジック。
 *
 * 【重要仕様】
 * 1. エンドポイントのケースセンシティブ対応:
 *    /data/map_img/RealTimeImg/ （Tが大文字）
 * 2. サーバー時刻同期:
 *    /webservice/server/pros/latest.json よりサーバー側の最新有効秒（latest_time）を常時同期。
 * 3. ベースマップ合成:
 *    透明背景の観測点リアルタイムGIFを /data/map_img/CommonImg/base_map_b.gif（暗色日本地図）と
 *    Canvas上で重ね合わせて1枚の完全な強震モニタ画像としてレンダリング。
 * 4. 防災科研EEW JSONエンドポイント（/webservice/hypo/eew/）の自動チェック。
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
        const val LATEST_TIME_URL = "http://www.kmoni.bosai.go.jp/webservice/server/pros/latest.json"
        const val BASEMAP_URL = "http://www.kmoni.bosai.go.jp/data/map_img/CommonImg/base_map_b.gif"
        const val DEFAULT_INTERVAL_MS = 1000L
    }

    private var pollingJob: Job? = null
    private var isRunning = false
    private var pollIntervalMs = DEFAULT_INTERVAL_MS

    // Clock calibration between device and NIED server
    private var serverClockOffsetMillis: Long? = null
    private var lastServerSyncTimestamp = 0L

    // Cached dark base map of Japan
    private var cachedBaseMap: Bitmap? = null

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
    private val serverDisplayFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
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
            // Pre-load basemap once in background
            ensureBaseMapLoaded()

            while (isActive && isRunning) {
                fetchLatestImage()
                delay(pollIntervalMs)
            }
        }
    }

    /**
     * Builds the target URL for NIED Kyoshin Monitor image
     * Example: http://www.kmoni.bosai.go.jp/data/map_img/RealTimeImg/jma_s/20261017/20261017220143.jma_s.gif
     */
    fun buildImageUrl(date: Date, type: KmoniImageType, depth: DepthMode): String {
        val ymd = jstDateFormat.format(date)
        val ymdHms = jstTimeFormat.format(date)
        val depthSuffix = if (depth == DepthMode.BOREHOLE) "b" else "s"
        val typeCode = type.code
        val fileName = "${typeCode}_${depthSuffix}"

        // RealTimeImg with capital 'T'
        return "$BASE_URL/data/map_img/RealTimeImg/$fileName/$ymd/$ymdHms.$fileName.gif"
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

    private suspend fun ensureBaseMapLoaded() {
        if (cachedBaseMap != null) return
        try {
            cachedBaseMap = downloadBitmap(BASEMAP_URL)
            Log.d(TAG, "NIED dark basemap loaded successfully: ${cachedBaseMap?.width}x${cachedBaseMap?.height}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load base map: ${e.message}")
        }
    }

    private suspend fun syncServerClock(): Date? {
        val request = Request.Builder()
            .url(LATEST_TIME_URL)
            .header("User-Agent", "Mozilla/5.0 (Android; KyoshinMonitorApp/1.0)")
            .header("Referer", "http://www.kmoni.bosai.go.jp/")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use null
                        val json = JSONObject(body)
                        val latestTimeStr = json.optString("latest_time", "")
                        if (latestTimeStr.isNotBlank()) {
                            val serverDate = serverDisplayFormat.parse(latestTimeStr)
                            if (serverDate != null) {
                                serverClockOffsetMillis = serverDate.time - System.currentTimeMillis()
                                lastServerSyncTimestamp = System.currentTimeMillis()
                                Log.d(TAG, "Server time synced: $latestTimeStr (offset: ${serverClockOffsetMillis}ms)")
                                return@withContext serverDate
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync latest.json: ${e.message}")
            }
            return@withContext null
        }
    }

    private suspend fun fetchLatestImage() {
        val startTime = System.currentTimeMillis()
        val currentStats = _fetchStats.value

        // Check if we need to sync clock with latest.json
        val needsSync = serverClockOffsetMillis == null ||
                (startTime - lastServerSyncTimestamp > 12_000L) ||
                (currentStats.status == KmoniFetchStatus.ERROR)

        var targetDate: Date? = null
        if (needsSync) {
            _fetchStats.value = currentStats.copy(status = KmoniFetchStatus.SYNCING)
            targetDate = syncServerClock()
        }

        // If not synced just now, calculate target Date using calibrated server offset
        if (targetDate == null) {
            val offset = serverClockOffsetMillis ?: -1000L // Default ~1s server delay if uncalibrated
            targetDate = Date(startTime + offset)
        }

        _fetchStats.value = _fetchStats.value.copy(status = KmoniFetchStatus.FETCHING)

        val targetUrl = buildImageUrl(targetDate, _imageType.value, _depthMode.value)
        var resultBitmap: Bitmap? = null
        var successfulUrl = targetUrl
        var successfulDate = targetDate

        try {
            ensureBaseMapLoaded()

            resultBitmap = downloadBitmap(targetUrl)

            if (resultBitmap == null) {
                // If 404, fallback 1 second earlier
                val fallbackDate = Date(targetDate.time - 1000L)
                val fallbackUrl = buildImageUrl(fallbackDate, _imageType.value, _depthMode.value)

                resultBitmap = downloadBitmap(fallbackUrl)
                if (resultBitmap != null) {
                    successfulUrl = fallbackUrl
                    successfulDate = fallbackDate
                } else {
                    // Try re-syncing clock immediately next cycle
                    serverClockOffsetMillis = null
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (resultBitmap != null) {
                // Composite the transparent real-time layer onto the dark base map of Japan
                val finalCompositeBitmap = compositeOnBasemap(resultBitmap)

                _latestBitmap.value = finalCompositeBitmap
                val formattedTime = serverDisplayFormat.format(successfulDate)
                _latestTimestamp.value = formattedTime

                _fetchStats.value = currentStats.copy(
                    status = KmoniFetchStatus.SUCCESS,
                    successCount = currentStats.successCount + 1,
                    lastLatencyMs = elapsed,
                    currentDelayOffsetSec = 1,
                    lastSuccessTimestamp = formattedTime,
                    latestImageUrl = successfulUrl,
                    errorMessage = null
                )

                // Periodically check EEW status JSON from NIED
                if (currentStats.successCount % 4L == 0L) {
                    checkNiedEEW(successfulDate)
                }
            } else {
                _fetchStats.value = currentStats.copy(
                    status = KmoniFetchStatus.DELAY_ADJUST,
                    failureCount = currentStats.failureCount + 1,
                    lastLatencyMs = elapsed,
                    latestImageUrl = targetUrl,
                    errorMessage = "画像更新待機中 (時刻同期中...)"
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.w(TAG, "Error fetching Kmoni image: ${e.message}")
            _fetchStats.value = currentStats.copy(
                status = KmoniFetchStatus.ERROR,
                failureCount = currentStats.failureCount + 1,
                lastLatencyMs = elapsed,
                latestImageUrl = targetUrl,
                errorMessage = e.message ?: "通信エラー"
            )
        }
    }

    /**
     * Composites real-time station dots onto NIED dark basemap of Japan
     */
    private fun compositeOnBasemap(realtimeLayer: Bitmap): Bitmap {
        val base = cachedBaseMap
        if (base == null) {
            return realtimeLayer
        }

        val width = base.width
        val height = base.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        // 1. Draw dark basemap with coastline and prefectures
        canvas.drawBitmap(base, 0f, 0f, paint)

        // 2. Draw live observation station points layer
        canvas.drawBitmap(realtimeLayer, 0f, 0f, paint)

        return output
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
                } else {
                    Log.d(TAG, "HTTP ${response.code} for $url")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Download failed for $url: ${e.message}")
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
