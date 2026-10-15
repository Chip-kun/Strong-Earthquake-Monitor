package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EEWNotificationEntity
import com.example.data.local.repository.EEWNotificationRepository
import com.example.notification.KyoshinNotificationManager
import com.example.audio.VoiceAlertManager
import com.example.location.UserLocation
import com.example.location.UserLocationManager
import com.example.model.*
import com.example.repository.KyoshinRepository
import com.example.sensor.DeviceSeismometer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10

enum class AppTab(val title: String) { MONITOR("強震モニタ"), EARTHQUAKES("地震履歴"), SEISMOMETER("マイ地震計"), DRILL("緊急訓練"), GUIDE("防災・安否") }
enum class MapStyle(val label: String) { SATELLITE("リアル地形衛星"), RADAR("レーダー暗色"), TECTONIC("海溝・プレート") }
data class DisasterCheckItem(val id: String, val name: String, val category: String, val isChecked: Boolean = false)
data class DrillScenario(
    val title: String,
    val subtitle: String,
    val hypocenter: String,
    val magnitude: Float,
    val maxShindo: String,
    val depthKm: Int,
    val lat: Double,
    val lon: Double,
    val countdownSec: Int,
    val isWarning: Boolean,
    val affectedPrefectures: List<String> = emptyList(),
    val detailedAreas: List<String> = emptyList(),
    val tsunamiWarning: String = "津波の心配なし"
)

class KyoshinViewModel(application: Application) : AndroidViewModel(application) {

    val repository = KyoshinRepository(viewModelScope)
    val voiceAlertManager = VoiceAlertManager(application)
    val deviceSeismometer = DeviceSeismometer(application)
    val locationManager = UserLocationManager(application)

    // Room Database local storage for EEW logs
    val eewDatabaseRepository = EEWNotificationRepository(
        AppDatabase.getInstance(application).eewNotificationDao()
    )
    val eewLogs: StateFlow<List<EEWNotificationEntity>> = eewDatabaseRepository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedHistoryLog = MutableStateFlow<EEWNotificationEntity?>(null)
    val selectedHistoryLog: StateFlow<EEWNotificationEntity?> = _selectedHistoryLog.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("kyoshin_settings", Context.MODE_PRIVATE)

    private val _isSeismometerEnabled = MutableStateFlow(sharedPreferences.getBoolean("seismometer_enabled", true))
    val isSeismometerEnabled: StateFlow<Boolean> = _isSeismometerEnabled.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.MONITOR)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _currentTimeStr = MutableStateFlow("")
    val currentTimeStr: StateFlow<String> = _currentTimeStr.asStateFlow()

    private val _mapStyle = MutableStateFlow(MapStyle.SATELLITE)
    val mapStyle: StateFlow<MapStyle> = _mapStyle.asStateFlow()

    private val _showStationLabels = MutableStateFlow(true)
    val showStationLabels: StateFlow<Boolean> = _showStationLabels.asStateFlow()

    val userLocation = locationManager.userLocation

    val distanceToEpicenter: StateFlow<Float?> = combine(userLocation, repository.activeEEW) { loc, eew ->
        if (loc != null && eew != null) UserLocationManager.calculateDistanceKm(loc.lat, loc.lon, eew.lat, eew.lon) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userEtaSec: StateFlow<Int?> = combine(distanceToEpicenter, repository.activeEEW) { dist, eew ->
        if (dist != null && eew != null) { val travel = (dist / 3.8f).toInt(); (travel - (eew.pWaveRadiusKm / 6.5f).toInt()).coerceAtLeast(0) } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userEstimatedShindo: StateFlow<String?> = combine(distanceToEpicenter, repository.activeEEW) { dist, eew ->
        if (dist != null && eew != null) {
            val est = (eew.magnitude * 1.3f - log10(dist.toDouble() + 15.0) * 2.5).toFloat()
            when { est < 0.5f -> "0"; est < 1.5f -> "1"; est < 2.5f -> "2"; est < 3.5f -> "3"; est < 4.5f -> "4"; est < 5.0f -> "5弱"; est < 5.5f -> "5強"; est < 6.0f -> "6弱"; est < 6.5f -> "6強"; else -> "7" }
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nearestStation: StateFlow<ObservationStation?> = combine(userLocation, repository.stations) { loc, stations ->
        if (loc != null && stations.isNotEmpty()) stations.minByOrNull { UserLocationManager.calculateDistanceKm(loc.lat, loc.lon, it.lat, it.lon) } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _checklist = MutableStateFlow(listOf(
        DisasterCheckItem("1", "飲料水（1人1日3L × 3日分）", "飲食料"), DisasterCheckItem("2", "非常食（乾パン・アルファ化米・缶詰）", "飲食料"),
        DisasterCheckItem("3", "救急箱・常備薬・お薬手帳", "医療・衛生"), DisasterCheckItem("4", "モバイルバッテリー＆充電ケーブル", "情報・電源"),
        DisasterCheckItem("5", "LED懐中電灯・ヘッドライト", "情報・電源"), DisasterCheckItem("6", "携帯ラジオ（手回し・電池式）", "情報・電源"),
        DisasterCheckItem("7", "非常用簡易トイレ（凝固剤付き）", "医療・衛生"), DisasterCheckItem("8", "ウェットティッシュ・消毒液", "医療・衛生"),
        DisasterCheckItem("9", "軍手・防塵マスク・厚手の靴下", "安全・保護"), DisasterCheckItem("10", "防寒用アルミシート・レインウェア", "安全・保護"),
        DisasterCheckItem("11", "現金（小銭・1000円札）", "貴重品"), DisasterCheckItem("12", "身分証コピー・保険証コピー", "貴重品"), DisasterCheckItem("13", "ホイッスル（防犯・救助笛）", "安全・保護")
    ))
    val checklist: StateFlow<List<DisasterCheckItem>> = _checklist.asStateFlow()

    val drillScenarios = listOf(
        DrillScenario("首都直下地震（相模トラフ沿い）", "M7.3 / 最大震度6強 / 関東広域警報", "東京湾南部", 7.3f, "6強", 30, 35.50, 139.80, 8, true, listOf("東京都", "神奈川県", "千葉県", "埼玉県", "茨城県"), listOf("東京23区南部", "横浜市中区", "川崎市川崎区", "千葉市美浜区"), "津波注意報（東京湾沿岸で50cm程度の津波予想）"),
        DrillScenario("南海トラフ巨大地震（前駆M8級）", "M8.4 / 最大震度7 / 太平洋沿岸大津波警戒", "潮岬沖", 8.4f, "7", 20, 33.20, 135.80, 22, true, listOf("和歌山県", "高知県", "三重県", "静岡県", "愛知県", "徳島県", "大阪府"), listOf("和歌山県南部（串本町）", "高知県室戸市", "三重県尾鷲市", "静岡県御前崎市"), "大津波警報発令（沿岸部に10m超の巨大津波の恐れ！ただちに高台へ避難！）"),
        DrillScenario("宮城県沖プレート境界地震", "M7.5 / 最大震度6弱 / 東北沿岸", "宮城県沖", 7.5f, "6弱", 40, 38.30, 142.10, 15, true, listOf("宮城県", "福島県", "岩手県", "山形県"), listOf("宮城県東部（石巻市・気仙沼市）", "岩手県沿岸南部（陸前高田市）", "福島県浜通り（相馬市）"), "津波警報発令（宮城県・岩手県・福島県沿岸に最大3mの津波到達予想）"),
        DrillScenario("能登半島沖地震", "M6.8 / 最大震度6強 / 日本海側警戒", "能登半島沖", 6.8f, "6強", 10, 37.45, 137.20, 10, true, listOf("石川県", "富山県", "福井県", "新潟県"), listOf("石川県能登（輪島市・珠洲市・能登町）", "富山県西部（氷見市）", "新潟県上中越（上越市）"), "大津波警報発表中（沿岸直ちに高台や避難ビルへ避難！）")
    )

    private var previousEEWCountdown = -1
    private var lastSavedEEWReportId: String? = null
    private var lastAnnouncedEarthquakeId: String? = null

    init {
        if (_isSeismometerEnabled.value) { deviceSeismometer.start() } else { deviceSeismometer.stop() }
        deviceSeismometer.onShakeDetected = { gal, shindo ->
            if (_isSeismometerEnabled.value) {
                voiceAlertManager.announceDeviceShake(gal, shindo)
                KyoshinNotificationManager.notifyDeviceShake(getApplication(), gal, shindo)
            }
        }

        viewModelScope.launch {
            eewDatabaseRepository.seedInitialHistoryIfEmpty()
        }

        viewModelScope.launch {
            val df = SimpleDateFormat("yyyy/MM/dd HH:mm:ss 'JST'", Locale.JAPAN).apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }
            while (true) { _currentTimeStr.value = df.format(Date()); delay(1000) }
        }

        viewModelScope.launch {
            repository.activeEEW.collect { eew ->
                if (eew != null) {
                    val reportKey = "${eew.id}_${eew.reportNum}"
                    if (reportKey != lastSavedEEWReportId) {
                        lastSavedEEWReportId = reportKey
                        eewDatabaseRepository.saveEEWAlert(eew)
                        KyoshinNotificationManager.notifyEEW(getApplication(), eew)
                    }

                    if (previousEEWCountdown == -1) {
                        voiceAlertManager.announceDetailedEEW(eew, userLocation.value?.placeName, distanceToEpicenter.value, userEtaSec.value, userEstimatedShindo.value)
                    }
                    if (eew.countdownSeconds != previousEEWCountdown && eew.countdownSeconds >= 0) {
                        voiceAlertManager.announceCountdown(eew.countdownSeconds)
                    }
                    previousEEWCountdown = eew.countdownSeconds
                } else {
                    previousEEWCountdown = -1
                }
            }
        }

        viewModelScope.launch {
            repository.earthquakes.collect { list ->
                if (list.isNotEmpty()) {
                    eewDatabaseRepository.importP2PEarthquakes(list)
                }
                val first = list.firstOrNull() ?: return@collect
                if (lastAnnouncedEarthquakeId == null) {
                    lastAnnouncedEarthquakeId = first.id
                } else if (first.id != lastAnnouncedEarthquakeId) {
                    lastAnnouncedEarthquakeId = first.id
                    voiceAlertManager.announceDetailedEarthquakeReport(first)
                    KyoshinNotificationManager.notifyEarthquakeReport(getApplication(), first)
                }
            }
        }
    }

    fun selectHistoryLog(log: EEWNotificationEntity?) {
        _selectedHistoryLog.value = log
    }

    fun importP2PEarthquakesToRoom() {
        viewModelScope.launch {
            val list = repository.earthquakes.value
            if (list.isNotEmpty()) {
                eewDatabaseRepository.importP2PEarthquakes(list)
            }
        }
    }

    fun setMapStyle(style: MapStyle) { _mapStyle.value = style }
    fun toggleStationLabels() { _showStationLabels.value = !_showStationLabels.value }
    fun selectTab(tab: AppTab) { _currentTab.value = tab }
    fun dismissEEW() { repository.dismissEEW(); voiceAlertManager.stopSpeaking() }
    fun announceEarthquake(item: EarthquakeItem) { voiceAlertManager.announceDetailedEarthquakeReport(item) }
    fun toggleCheckItem(id: String) { _checklist.value = _checklist.value.map { if (it.id == id) it.copy(isChecked = !it.isChecked) else it } }

    fun triggerDrill(scenario: DrillScenario) {
        val alert = EEWAlert(
            id = "drill_${System.currentTimeMillis()}", reportNum = 1, isFinal = false,
            isWarning = scenario.isWarning, hypocenter = scenario.hypocenter, lat = scenario.lat, lon = scenario.lon,
            depthKm = scenario.depthKm, magnitude = scenario.magnitude, maxShindo = scenario.maxShindo,
            originTimeMillis = System.currentTimeMillis(), countdownSeconds = scenario.countdownSec,
            isActive = true, isDrill = true, affectedPrefectures = scenario.affectedPrefectures
        )
        viewModelScope.launch {
            eewDatabaseRepository.saveEEWAlert(alert)
        }
        repository.triggerDrillEEW(alert)
    }

    fun repeatEEWAnnouncement() {
        val current = repository.activeEEW.value ?: return
        voiceAlertManager.announceDetailedEEW(current, userLocation.value?.placeName, distanceToEpicenter.value, userEtaSec.value, userEstimatedShindo.value)
    }

    fun toggleSeismometer(enabled: Boolean) {
        _isSeismometerEnabled.value = enabled
        sharedPreferences.edit().putBoolean("seismometer_enabled", enabled).apply()
        if (enabled) { deviceSeismometer.start() } else { deviceSeismometer.stop(); deviceSeismometer.resetPeak() }
    }

    fun deleteEEWLog(entity: EEWNotificationEntity) {
        viewModelScope.launch {
            eewDatabaseRepository.deleteLog(entity)
        }
    }

    fun clearAllEEWLogs() {
        viewModelScope.launch {
            eewDatabaseRepository.clearAll()
        }
    }

    fun insertSampleEEWLog(
        hypocenter: String,
        maxShindo: String,
        magnitude: Float,
        depthKm: Int,
        isWarning: Boolean,
        isDrill: Boolean = false,
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ) {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            }
            val now = System.currentTimeMillis()
            val entity = EEWNotificationEntity(
                alertId = "EEW-SIM-${now % 100000}",
                reportNum = if (isDrill) "訓練報" else "速報第1報",
                originTime = dateFormat.format(Date(now)),
                hypocenter = hypocenter,
                magnitude = magnitude,
                depthKm = depthKm,
                maxShindo = maxShindo,
                isWarning = isWarning,
                isDrill = isDrill,
                affectedAreas = if (isWarning) "周辺各地域（広域警戒）" else "震源周辺地域",
                latitude = latitude,
                longitude = longitude,
                receivedAt = now
            )
            eewDatabaseRepository.insertCustomLog(entity)
        }
    }

    fun announceEEWLog(entity: EEWNotificationEntity) {
        voiceAlertManager.playEEWChime()
        val text = buildString {
            if (entity.isDrill) {
                append("これは緊急地震速報の訓練記録です。")
            } else if (entity.isWarning) {
                append("緊急地震速報、警報の記録です。")
            } else {
                append("緊急地震速報、予報の記録です。")
            }
            if (entity.reportNum.isNotEmpty()) {
                append("${entity.reportNum}。")
            }
            if (entity.originTime.isNotEmpty()) {
                append("発生日時は、${entity.originTime}。")
            }
            append("震源地は、${entity.hypocenter}。")
            append("震源の深さは、約${entity.depthKm}キロメートル。")
            append("地震の規模、マグニチュードは${entity.magnitude}。")
            append("推定される最大震度は、震度${entity.maxShindo}です。")
            if (entity.affectedAreas.isNotEmpty()) {
                append("警戒対象地域は、${entity.affectedAreas}です。")
            }
            if (entity.isWarning) {
                append("強い揺れに備え、身の安全を確保してください。")
            }
        }
        viewModelScope.launch {
            delay(500)
            voiceAlertManager.speak(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceSeismometer.stop()
        voiceAlertManager.destroy()
    }
}
