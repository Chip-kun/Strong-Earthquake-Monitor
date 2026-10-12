package com.example.ui

import android.app.Application
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

enum class AppTab(val title: String) {
    MONITOR("強震モニタ"),
    EARTHQUAKES("地震履歴"),
    SEISMOMETER("マイ地震計"),
    DRILL("緊急訓練"),
    GUIDE("防災・安否")
}

enum class MapStyle(val label: String) {
    SATELLITE("リアル地形衛星"),
    RADAR("レーダー暗色"),
    TECTONIC("海溝・プレート")
}

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
    val affectedPrefectures: List<String> = emptyList()
)

data class DisasterCheckItem(
    val id: String,
    val name: String,
    val category: String,
    val isChecked: Boolean = false
)

class KyoshinViewModel(application: Application) : AndroidViewModel(application) {

    val repository = KyoshinRepository(viewModelScope)
    val voiceAlertManager = VoiceAlertManager(application)
    val deviceSeismometer = DeviceSeismometer(application)
    val locationManager = UserLocationManager(application)
        // 【追加】設定を端末に保存するための準備
    private val sharedPreferences = application.getSharedPreferences("kyoshin_settings", Context.MODE_PRIVATE)

    // 【追加】オン/オフ状態を管理する変数（初期値は保存データ、無ければデフォルトはオン）
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

    // Calculated relationship between User and active EEW
    val userLocation = locationManager.userLocation

    val distanceToEpicenter: StateFlow<Float?> = combine(
        userLocation,
        repository.activeEEW
    ) { loc, eew ->
        if (loc != null && eew != null) {
            UserLocationManager.calculateDistanceKm(loc.lat, loc.lon, eew.lat, eew.lon)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userEtaSec: StateFlow<Int?> = combine(
        distanceToEpicenter,
        repository.activeEEW
    ) { dist, eew ->
        if (dist != null && eew != null) {
            val sSpeedKmS = 3.8f
            val travelTimeSec = (dist / sSpeedKmS).toInt()
            (travelTimeSec - (eew.pWaveRadiusKm / 6.5f).toInt()).coerceAtLeast(0)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userEstimatedShindo: StateFlow<String?> = combine(
        distanceToEpicenter,
        repository.activeEEW
    ) { dist, eew ->
        if (dist != null && eew != null) {
            val distFactor = (dist + 15f)
            val estIntensity = (eew.magnitude * 1.3f - log10(distFactor.toDouble()) * 2.5).toFloat()
            when {
                estIntensity < 0.5f -> "0"
                estIntensity < 1.5f -> "1"
                estIntensity < 2.5f -> "2"
                estIntensity < 3.5f -> "3"
                estIntensity < 4.5f -> "4"
                estIntensity < 5.0f -> "5弱"
                estIntensity < 5.5f -> "5強"
                estIntensity < 6.0f -> "6弱"
                estIntensity < 6.5f -> "6強"
                else -> "7"
            }
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nearestStation: StateFlow<ObservationStation?> = combine(
        userLocation,
        repository.stations
    ) { loc, stations ->
        if (loc != null && stations.isNotEmpty()) {
            stations.minByOrNull { st ->
                UserLocationManager.calculateDistanceKm(loc.lat, loc.lon, st.lat, st.lon)
            }
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Disaster Bag Checklist state
    private val _checklist = MutableStateFlow(
        listOf(
            DisasterCheckItem("1", "飲料水（1人1日3L × 3日分）", "飲食料"),
            DisasterCheckItem("2", "非常食（乾パン・アルファ化米・缶詰）", "飲食料"),
            DisasterCheckItem("3", "救急箱・常備薬・お薬手帳", "医療・衛生"),
            DisasterCheckItem("4", "モバイルバッテリー＆充電ケーブル", "情報・電源"),
            DisasterCheckItem("5", "LED懐中電灯・ヘッドライト", "情報・電源"),
            DisasterCheckItem("6", "携帯ラジオ（手回し・電池式）", "情報・電源"),
            DisasterCheckItem("7", "非常用簡易トイレ（凝固剤付き）", "医療・衛生"),
            DisasterCheckItem("8", "ウェットティッシュ・消毒液", "医療・衛生"),
            DisasterCheckItem("9", "軍手・防塵マスク・厚手の靴下", "安全・保護"),
            DisasterCheckItem("10", "防寒用アルミシート・レインウェア", "安全・保護"),
            DisasterCheckItem("11", "現金（小銭・1000円札）", "貴重品"),
            DisasterCheckItem("12", "身分証コピー・保険証コピー", "貴重品"),
            DisasterCheckItem("13", "ホイッスル（防犯・救助笛）", "安全・保護")
        )
    )
    val checklist: StateFlow<List<DisasterCheckItem>> = _checklist.asStateFlow()

    val drillScenarios = listOf(
        DrillScenario(
            title = "首都直下地震（都心南部）",
            subtitle = "M7.3 / 最大震度6強 / 緊急地震速報（警報）",
            hypocenter = "東京都23区",
            magnitude = 7.3f,
            maxShindo = "6強",
            depthKm = 25,
            lat = 35.65,
            lon = 139.75,
            countdownSec = 10,
            isWarning = true,
            affectedPrefectures = listOf("東京都", "神奈川県", "千葉県", "埼玉県")
        ),
        DrillScenario(
            title = "南海トラフ巨大地震（潮岬沖）",
            subtitle = "M8.4 / 最大震度7 / 大津波警報発令想定",
            hypocenter = "潮岬南方沖",
            magnitude = 8.4f,
            maxShindo = "7",
            depthKm = 20,
            lat = 33.20,
            lon = 135.80,
            countdownSec = 22,
            isWarning = true,
            affectedPrefectures = listOf("高知県", "和歌山県", "徳島県", "三重県", "愛知県", "静岡県", "大阪府")
        ),
        DrillScenario(
            title = "東北地方太平洋沖地震",
            subtitle = "M7.5 / 最大震度6弱 / 広域揺れ警報",
            hypocenter = "宮城県沖",
            magnitude = 7.5f,
            maxShindo = "6弱",
            depthKm = 40,
            lat = 38.30,
            lon = 142.10,
            countdownSec = 15,
            isWarning = true,
            affectedPrefectures = listOf("宮城県", "福島県", "岩手県", "山形県")
        ),
        DrillScenario(
            title = "能登半島沖地震",
            subtitle = "M6.8 / 最大震度6強 / 日本海側警戒",
            hypocenter = "能登半島沖",
            magnitude = 6.8f,
            maxShindo = "6強",
            depthKm = 10,
            lat = 37.45,
            lon = 137.20,
            countdownSec = 10,
            isWarning = true,
            affectedPrefectures = listOf("石川県", "富山県", "福井県", "新潟県")
        )
    )

    private var previousEEWCountdown = -1
    private var lastAnnouncedEarthquakeId: String? = null

　　　    // 【214行目から下をすべてこれに置き換え】
    init {
        if (_isSeismometerEnabled.value) {
            deviceSeismometer.start()
        } else {
            deviceSeismometer.stop()
        }
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
