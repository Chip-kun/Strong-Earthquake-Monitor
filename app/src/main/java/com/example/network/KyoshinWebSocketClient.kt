package com.example.network

import android.util.Log
import com.example.model.EEWAlert
import com.example.model.EarthquakeItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class WebSocketStatus(val label: String) {
    DISCONNECTED("切断"),
    CONNECTING("接続中"),
    CONNECTED("接続完了"),
    RECONNECTING("再接続中"),
    ERROR("エラー")
}

data class WebSocketStats(
    val status: WebSocketStatus = WebSocketStatus.DISCONNECTED,
    val url: String = KyoshinWebSocketClient.DEFAULT_WS_URL,
    val messagesReceivedCount: Long = 0,
    val lastMessageTimeMillis: Long = 0,
    val lastEventSummary: String = "未接続",
    val reconnectAttempts: Int = 0,
    val errorMessage: String? = null
)

/**
 * 強震モニター・緊急地震速報・P2P地震情報のリアルタイム配信データを受信するためのWebSocketクライアント。
 * 常時接続、切断時の自動再接続（指数バックオフ）、JSONパース、EEW・地震情報コールバックを完備。
 */
class KyoshinWebSocketClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private var wsUrl: String = DEFAULT_WS_URL
) {
    companion object {
        const val TAG = "KyoshinWebSocket"
        const val DEFAULT_WS_URL = "wss://api.p2pquake.net/v2/ws"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive socket
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var isManuallyClosed = false

    private val _stats = MutableStateFlow(WebSocketStats(url = wsUrl))
    val stats: StateFlow<WebSocketStats> = _stats.asStateFlow()

    // Event listeners
    var onEEWReceived: ((EEWAlert) -> Unit)? = null
    var onEarthquakeReceived: ((EarthquakeItem) -> Unit)? = null
    var onRawMessageReceived: ((String) -> Unit)? = null

    fun connect() {
        if (_stats.value.status == WebSocketStatus.CONNECTED || _stats.value.status == WebSocketStatus.CONNECTING) {
            Log.d(TAG, "Already connected or connecting to WebSocket")
            return
        }

        isManuallyClosed = false
        reconnectJob?.cancel()

        _stats.value = _stats.value.copy(
            status = WebSocketStatus.CONNECTING,
            errorMessage = null,
            lastEventSummary = "接続中..."
        )

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to $wsUrl")
                _stats.value = _stats.value.copy(
                    status = WebSocketStatus.CONNECTED,
                    reconnectAttempts = 0,
                    errorMessage = null,
                    lastEventSummary = "リアルタイム配信 待機中"
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                _stats.value = _stats.value.copy(
                    status = WebSocketStatus.DISCONNECTED,
                    lastEventSummary = "切断 ($reason)"
                )
                if (!isManuallyClosed) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket error: ${t.message}", t)
                _stats.value = _stats.value.copy(
                    status = WebSocketStatus.ERROR,
                    errorMessage = t.message,
                    lastEventSummary = "接続エラー: ${t.message ?: "通信失敗"}"
                )
                if (!isManuallyClosed) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        isManuallyClosed = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        _stats.value = _stats.value.copy(
            status = WebSocketStatus.DISCONNECTED,
            lastEventSummary = "手動切断"
        )
    }

    fun reconnect() {
        disconnect()
        isManuallyClosed = false
        connect()
    }

    fun updateUrl(newUrl: String) {
        if (wsUrl != newUrl) {
            wsUrl = newUrl
            _stats.value = _stats.value.copy(url = newUrl)
            reconnect()
        }
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed) return
        reconnectJob?.cancel()

        val nextAttempts = _stats.value.reconnectAttempts + 1
        val delaySec = min(30, 2 * (1 shl min(nextAttempts - 1, 4)))

        _stats.value = _stats.value.copy(
            status = WebSocketStatus.RECONNECTING,
            reconnectAttempts = nextAttempts,
            lastEventSummary = "${delaySec}秒後に自動再接続します (#$nextAttempts)"
        )

        reconnectJob = scope.launch {
            delay(delaySec * 1000L)
            if (!isManuallyClosed) {
                Log.i(TAG, "Attempting reconnect (#$nextAttempts)...")
                connect()
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        val now = System.currentTimeMillis()
        val currentCount = _stats.value.messagesReceivedCount + 1

        onRawMessageReceived?.invoke(text)

        try {
            val json = JSONObject(text)
            val code = json.optInt("code", 0)

            when (code) {
                556 -> {
                    // 緊急地震速報 (EEW)
                    val eewAlert = parseEEWFromJson(json)
                    val summary = if (eewAlert.isWarning) {
                        "⚠️【警報】${eewAlert.hypocenter} M${eewAlert.magnitude} 最大震度${eewAlert.maxShindo}"
                    } else {
                        "⚡【予報】${eewAlert.hypocenter} M${eewAlert.magnitude} 最大震度${eewAlert.maxShindo}"
                    }
                    _stats.value = _stats.value.copy(
                        messagesReceivedCount = currentCount,
                        lastMessageTimeMillis = now,
                        lastEventSummary = summary
                    )
                    onEEWReceived?.invoke(eewAlert)
                }
                551 -> {
                    // 地震情報 (震源・震度情報)
                    val eqItem = parseEarthquakeFromJson(json)
                    val summary = "🌐【地震情報】${eqItem.hypocenter} M${eqItem.magnitude} 最大震度${eqItem.maxScale}"
                    _stats.value = _stats.value.copy(
                        messagesReceivedCount = currentCount,
                        lastMessageTimeMillis = now,
                        lastEventSummary = summary
                    )
                    onEarthquakeReceived?.invoke(eqItem)
                }
                552 -> {
                    // 津波予報
                    val summary = "🌊【津波情報】津波予報が更新されました"
                    _stats.value = _stats.value.copy(
                        messagesReceivedCount = currentCount,
                        lastMessageTimeMillis = now,
                        lastEventSummary = summary
                    )
                }
                554 -> {
                    // 緊急地震速報（発表検出）
                    _stats.value = _stats.value.copy(
                        messagesReceivedCount = currentCount,
                        lastMessageTimeMillis = now,
                        lastEventSummary = "⚡ EEW発表検出を受信"
                    )
                }
                555 -> {
                    // 各地域ピア地震感知情報
                    _stats.value = _stats.value.copy(
                        messagesReceivedCount = currentCount,
                        lastMessageTimeMillis = now,
                        lastEventSummary = "📡 地域ピア感知情報受信"
                    )
                }
                else -> {
                    _stats.value = _stats.value.copy(
                        messagesReceivedCount = currentCount,
                        lastMessageTimeMillis = now,
                        lastEventSummary = "データ受信 (code: $code)"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WebSocket JSON: ${e.message}", e)
            _stats.value = _stats.value.copy(
                messagesReceivedCount = currentCount,
                lastMessageTimeMillis = now,
                lastEventSummary = "パース未対応メッセージ受信"
            )
        }
    }

    private fun parseEEWFromJson(json: JSONObject): EEWAlert {
        val id = json.optString("id", "eew_${System.currentTimeMillis()}")
        val eq = json.optJSONObject("earthquake")
        val hypo = eq?.optJSONObject("hypocenter")
        val hypoName = hypo?.optString("name", "緊急地震速報") ?: "緊急地震速報"
        val mag = hypo?.optDouble("magnitude", 5.0)?.toFloat() ?: 5.0f
        val depth = hypo?.optInt("depth", 10) ?: 10
        val rawScale = eq?.optInt("maxScale", 40) ?: 40
        val scaleText = com.example.model.parseShindoScaleToText(rawScale)
        val isWarn = rawScale >= 45 || mag >= 6.0f

        val lat = hypo?.optDouble("latitude", 35.68) ?: 35.68
        val lon = hypo?.optDouble("longitude", 139.76) ?: 139.76

        // Parse areas if present
        val areas = mutableListOf<String>()
        val areasJson = json.optJSONArray("areas")
        if (areasJson != null) {
            for (i in 0 until areasJson.length()) {
                val areaObj = areasJson.optJSONObject(i)
                val areaName = areaObj?.optString("name")
                if (!areaName.isNullOrBlank()) {
                    areas.add(areaName)
                }
            }
        }

        return EEWAlert(
            id = id,
            reportNum = 1,
            isFinal = false,
            isWarning = isWarn,
            hypocenter = hypoName,
            lat = lat,
            lon = lon,
            depthKm = depth,
            magnitude = mag,
            maxShindo = scaleText,
            originTimeMillis = System.currentTimeMillis(),
            sWaveRadiusKm = 80f,
            countdownSeconds = 15,
            isActive = true,
            isDrill = false,
            tsunamiWarning = "津波の心配なし",
            affectedPrefectures = areas,
            detailedAreas = areas
        )
    }

    private fun parseEarthquakeFromJson(json: JSONObject): EarthquakeItem {
        val id = json.optString("id", "${System.currentTimeMillis()}")
        val timeStr = json.optString("time", "現在")
        val eq = json.optJSONObject("earthquake")
        val hypo = eq?.optJSONObject("hypocenter")
        val hypoName = hypo?.optString("name", "不明") ?: "不明"
        val mag = hypo?.optDouble("magnitude", 0.0) ?: 0.0
        val depth = hypo?.optInt("depth", 0) ?: 0
        val rawScale = eq?.optInt("maxScale", 10) ?: 10
        val scaleText = com.example.model.parseShindoScaleToText(rawScale)
        val tsunami = eq?.optString("domesticTsunami", "None")

        val tsunamiDesc = when (tsunami) {
            "Warning" -> "大津波警報・津波警報発表中"
            "Watch" -> "津波注意報発表中"
            "Checking" -> "津波の有無を調査中"
            else -> "津波の心配なし"
        }

        val lat = hypo?.optDouble("latitude", 35.0) ?: 35.0
        val lon = hypo?.optDouble("longitude", 138.0) ?: 138.0

        // Parse observation points
        val pointsJson = json.optJSONArray("points")
        val pointsList = mutableListOf<com.example.model.PrefectureIntensity>()
        val detailedRegions = mutableListOf<String>()

        if (pointsJson != null) {
            val prefMap = mutableMapOf<String, MutableList<Pair<String, Int>>>()
            for (i in 0 until pointsJson.length()) {
                val pt = pointsJson.optJSONObject(i) ?: continue
                val pref = pt.optString("pref", "")
                val addr = pt.optString("addr", "")
                val scale = pt.optInt("scale", 0)
                if (pref.isNotBlank()) {
                    prefMap.getOrPut(pref) { mutableListOf() }.add(addr to scale)
                }
                if (scale >= rawScale - 10 && addr.isNotBlank()) {
                    detailedRegions.add(addr)
                }
            }

            prefMap.forEach { (pref, cities) ->
                val maxPtScale = cities.maxOfOrNull { it.second } ?: 0
                pointsList.add(
                    com.example.model.PrefectureIntensity(
                        prefecture = pref,
                        scale = com.example.model.parseShindoScaleToText(maxPtScale),
                        cities = cities.map { it.first }.filter { it.isNotBlank() }.distinct().take(6)
                    )
                )
            }
        }

        return EarthquakeItem(
            id = id,
            time = eq?.optString("time", timeStr) ?: timeStr,
            hypocenter = hypoName,
            maxScale = scaleText,
            magnitude = mag,
            depthKm = depth,
            tsunamiInfo = tsunamiDesc,
            lat = lat,
            lon = lon,
            intensityPoints = pointsList,
            detailedRegions = detailedRegions.distinct().take(8)
        )
    }
}
