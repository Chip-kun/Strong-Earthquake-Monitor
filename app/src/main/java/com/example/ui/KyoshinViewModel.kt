package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
data class DrillScenario(val title: String, val subtitle: String, val hypocenter: String, val magnitude: Float, val maxShindo: String, val depthKm: Int, val lat: Double, val lon: Double, val countdownSec: Int, val isWarning: Boolean, val affectedPrefectures: List<String> = emptyList())

class KyoshinViewModel(application: Application) : AndroidViewModel(application) {

    val repository = KyoshinRepository(viewModelScope)
    val voiceAlertManager = VoiceAlertManager(application)
    val deviceSeismometer = DeviceSeismometer(application)
    val locationManager = UserLocationManager(application)

    // 【修正】ズレていたインデントを綺麗に修正
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
        DrillScenario("首都直下地震（都心南部）", "M7.3 / 最大震度6強 / 緊急地震速報（警報）", "東京都23区", 7.3f, "6強", 25, 35.65, 139.75, 10, true, listOf("東京都", "神奈川県", "千葉県", "埼玉県")),
        DrillScenario("南海トラフ巨大地震（潮岬沖）", "M8.4 / 最大震度7 / 大津波警報発令想定", "潮岬南方沖", 8.4f, "7", 20, 33.20, 135.80, 22, true, listOf("高知県", "和歌山県", "徳島県", "三重県", "愛知県", "静岡県", "大阪府")),
        DrillScenario("東北地方太平洋沖地震", "M7.5 / 最大震度6弱 / 広域揺れ警報", "宮城県沖", 7.5f, "6弱", 40, 38.30, 142.10, 15, true, listOf("宮城県", "福島県", "岩手県", "山形県")),
        DrillScenario("能登半島沖地震", "M6.8 / 最大震度6強 / 日本海側警戒", "能登半島沖", 6.8f, "6強", 10, 37.45, 137.20, 10, true, listOf("石川県", "富山県", "福井県", "新潟県"))
    )

    private var previousEEWCountdown = -1
    private var lastAnnouncedEarthquakeId: String? = null

    init {
        if (_isSeismometerEnabled.value) { deviceSeismometer.start() } else { deviceSeismometer.stop() }
        deviceSeismometer.onShakeDetected = { gal, shindo ->
            if (_isSeismometerEnabled.value) { voiceAlertManager.announceDeviceShake(gal, shindo) }
        }
        viewModelScope.launch {
            val df = SimpleDateFormat("yyyy/MM/dd HH:mm:ss 'JST'", Locale.JAPAN).apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }
            while (true) { _currentTimeStr.value = df.format(Date()); delay(1000) }
        }
        viewModelScope.launch {
            repository.activeEEW.collect { eew ->
                if (eew != null) {
                    if (previousEEWCountdown == -1) { voiceAlertManager.announceDetailedEEW(eew, userLocation.value?.placeName, distanceToEpicenter.value, userEtaSec.value, userEstimatedShindo.value) }
                    if (eew.countdownSeconds != previousEEWCountdown && eew.countdownSeconds >= 0) { voiceAlertManager.announceCountdown(eew.countdownSeconds) }
                    previousEEWCountdown = eew.countdownSeconds
                } else { previousEEWCountdown = -1 }
            }
        }
        viewModelScope.launch {
            repository.earthquakes.collect { list ->
                val first = list.firstOrNull() ?: return@collect
                if (lastAnnouncedEarthquakeId == null) { lastAnnouncedEarthquakeId = first.id }
                else if (first.id != lastAnnouncedEarthquakeId) { lastAnnouncedEarthquakeId = first.id; voiceAlertManager.announceDetailedEarthquakeReport(first) }
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

    override fun onCleared() {
        super.onCleared()
        deviceSeismometer.stop()
        voiceAlertManager.destroy()
    }
}
