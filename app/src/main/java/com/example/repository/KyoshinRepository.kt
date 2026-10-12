package com.example.repository

import android.util.Log
import com.example.model.*
import com.example.network.NetworkClient
import com.example.network.toEarthquakeItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlin.random.Random

class KyoshinRepository(
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val tag = "KyoshinRepository"

    private val _stations = MutableStateFlow<List<ObservationStation>>(emptyList())
    val stations: StateFlow<List<ObservationStation>> = _stations.asStateFlow()

    private val _activeEEW = MutableStateFlow<EEWAlert?>(null)
    val activeEEW: StateFlow<EEWAlert?> = _activeEEW.asStateFlow()

    private val _earthquakes = MutableStateFlow<List<EarthquakeItem>>(emptyList())
    val earthquakes: StateFlow<List<EarthquakeItem>> = _earthquakes.asStateFlow()

    private val _liveWaveform = MutableStateFlow<List<SeismographSample>>(emptyList())
    val liveWaveform: StateFlow<List<SeismographSample>> = _liveWaveform.asStateFlow()

    private val _selectedStation = MutableStateFlow<ObservationStation?>(null)
    val selectedStation: StateFlow<ObservationStation?> = _selectedStation.asStateFlow()

    private val _displayMode = MutableStateFlow(DisplayMode.SHINDO)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _depthMode = MutableStateFlow(DepthMode.SURFACE)
    val depthMode: StateFlow<DepthMode> = _depthMode.asStateFlow()

    private val waveformBuffer = ArrayDeque<SeismographSample>(120)
    private var simulationJob: Job? = null
    private var countdownJob: Job? = null

    init {
        initStations()
        startLiveMonitoringLoop()
        fetchEarthquakes()
    }

    fun selectStation(station: ObservationStation?) {
        _selectedStation.value = station
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun setDepthMode(mode: DepthMode) {
        _depthMode.value = mode
        val currentStations = _stations.value
        _stations.value = currentStations.map { it.copy(isBorehole = mode == DepthMode.BOREHOLE) }
    }

    fun triggerDrillEEW(drill: EEWAlert) {
        countdownJob?.cancel()
        _activeEEW.value = drill

        countdownJob = externalScope.launch {
            var remaining = drill.countdownSeconds
            val startTime = System.currentTimeMillis()

            while (remaining > 0 && _activeEEW.value?.id == drill.id) {
                delay(1000)
                remaining--
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f

                // P-wave travels ~6.5 km/s, S-wave travels ~3.8 km/s
                val pRadius = elapsedSec * 6.5f
                val sRadius = elapsedSec * 3.8f

                _activeEEW.value = _activeEEW.value?.copy(
                    countdownSeconds = remaining,
                    pWaveRadiusKm = pRadius,
                    sWaveRadiusKm = sRadius
                )
            }

            // Keep warning active for 15 seconds after arrival, expanding waves
            for (i in 0 until 15) {
                delay(1000)
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
                _activeEEW.value = _activeEEW.value?.copy(
                    countdownSeconds = 0,
                    pWaveRadiusKm = elapsedSec * 6.5f,
                    sWaveRadiusKm = elapsedSec * 3.8f
                )
            }

            // End drill
            _activeEEW.value = null
        }
    }

    fun dismissEEW() {
        countdownJob?.cancel()
        _activeEEW.value = null
    }

    fun fetchEarthquakes() {
        externalScope.launch {
            try {
                val response = NetworkClient.p2pService.getEarthquakeHistory(limit = 25)
                val items = response.mapNotNull { it.toEarthquakeItem() }
                if (items.isNotEmpty()) {
                    _earthquakes.value = items
                    Log.i(tag, "Fetched ${items.size} earthquakes from P2P API")
                } else {
                    loadFallbackEarthquakes()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch earthquakes, using fallback: ${e.message}")
                loadFallbackEarthquakes()
            }
        }
    }

    private fun loadFallbackEarthquakes() {
        _earthquakes.value = listOf(
            EarthquakeItem(
                id = "demo_1",
                time = "10/11 20:45",
                hypocenter = "千葉県北東部",
                maxScale = "3",
                magnitude = 4.3,
                depthKm = 40,
                tsunamiInfo = "津波の心配なし",
                lat = 35.7,
                lon = 140.6,
                intensityPoints = listOf(
                    PrefectureIntensity("千葉県", "3", listOf("銚子市", "旭市", "匝瑳市", "香取市")),
                    PrefectureIntensity("茨城県", "2", listOf("神栖市", "鹿嶋市", "潮来市")),
                    PrefectureIntensity("東京都", "1", listOf("江戸川区", "江東区"))
                ),
                detailedRegions = listOf("千葉県北東部", "銚子市川口町", "旭市ニ", "神栖市溝口")
            ),
            EarthquakeItem(
                id = "demo_2",
                time = "10/11 14:12",
                hypocenter = "福島県沖",
                maxScale = "4",
                magnitude = 5.1,
                depthKm = 50,
                tsunamiInfo = "津波注意報発表中",
                lat = 37.5,
                lon = 141.6,
                intensityPoints = listOf(
                    PrefectureIntensity("福島県", "4", listOf("相馬市", "南相馬市", "いわき市", "双葉町")),
                    PrefectureIntensity("宮城県", "3", listOf("仙台市宮城野区", "石巻市", "名取市", "岩沼市")),
                    PrefectureIntensity("茨城県", "2", listOf("日立市", "北茨城市", "高萩市"))
                ),
                detailedRegions = listOf("福島県浜通り", "相馬市中村", "南相馬市原町区", "いわき市小名浜", "名取市増田")
            ),
            EarthquakeItem(
                id = "demo_3",
                time = "10/10 22:30",
                hypocenter = "日向灘",
                maxScale = "5弱",
                magnitude = 6.2,
                depthKm = 30,
                tsunamiInfo = "津波注意報（宮崎県・高知県沿岸）",
                lat = 31.9,
                lon = 131.8,
                intensityPoints = listOf(
                    PrefectureIntensity("宮崎県", "5弱", listOf("宮崎市", "日南市", "串間市")),
                    PrefectureIntensity("鹿児島県", "4", listOf("大崎町", "志布志市", "鹿屋市")),
                    PrefectureIntensity("大分県", "3", listOf("佐伯市", "津久見市"))
                ),
                detailedRegions = listOf("宮崎県南部平野部", "日南市油津", "宮崎市霧島", "串間市奈留", "志布志市志布志町")
            ),
            EarthquakeItem(
                id = "demo_4",
                time = "10/09 08:15",
                hypocenter = "能登半島沖",
                maxScale = "6強",
                magnitude = 7.1,
                depthKm = 10,
                tsunamiInfo = "大津波警報発表中（沿岸直ちに避難）",
                lat = 37.4,
                lon = 137.2,
                intensityPoints = listOf(
                    PrefectureIntensity("石川県", "6強", listOf("輪島市", "珠洲市", "能登町", "穴水町")),
                    PrefectureIntensity("富山県", "5強", listOf("富山市", "高岡市", "氷見市")),
                    PrefectureIntensity("新潟県", "5弱", listOf("上越市", "長岡市", "佐渡市"))
                ),
                detailedRegions = listOf("石川県能登地方", "輪島市鳳至町", "珠洲市正院町", "能登町宇出津", "氷見市加納")
            ),
            EarthquakeItem(
                id = "demo_5",
                time = "10/08 19:04",
                hypocenter = "茨城県南部",
                maxScale = "4",
                magnitude = 4.8,
                depthKm = 50,
                tsunamiInfo = "津波の心配なし",
                lat = 36.1,
                lon = 139.9,
                intensityPoints = listOf(
                    PrefectureIntensity("茨城県", "4", listOf("坂東市", "常総市", "つくばみらい市")),
                    PrefectureIntensity("栃木県", "3", listOf("下野市", "真岡市", "宇都宮市")),
                    PrefectureIntensity("埼玉県", "3", listOf("春日部市", "久喜市", "宮代町")),
                    PrefectureIntensity("東京都", "2", listOf("足立区", "北区", "練馬区"))
                ),
                detailedRegions = listOf("茨城県南部", "坂東市岩井", "常総市新石下", "下野市田中", "春日部市粕壁")
            )
        )
    }

    private fun startLiveMonitoringLoop() {
        simulationJob?.cancel()
        simulationJob = externalScope.launch {
            var tick = 0L
            while (isActive) {
                delay(1000)
                tick++

                val currentEEW = _activeEEW.value
                val isBorehole = _depthMode.value == DepthMode.BOREHOLE
                val boreholeDamping = if (isBorehole) 0.6f else 1.0f

                val updatedStations = _stations.value.map { station ->
                    var shindo = 0f
                    var pga = 0.05f

                    if (currentEEW != null) {
                        // Calculate distance from epicenter in km
                        val dLat = (station.lat - currentEEW.lat) * 111.0
                        val dLon = (station.lon - currentEEW.lon) * 91.0
                        val distKm = sqrt(dLat * dLat + dLon * dLon).toFloat()

                        val pRadius = currentEEW.pWaveRadiusKm
                        val sRadius = currentEEW.sWaveRadiusKm

                        if (distKm <= sRadius) {
                            // S-wave hit station: strong shaking
                            val distFactor = (distKm + 15f)
                            val peakIntensity = (currentEEW.magnitude * 1.3f - log10(distFactor.toDouble()) * 2.5).toFloat()
                            val decayedIntensity = (peakIntensity * exp(-(sRadius - distKm) / 80f)).coerceIn(0.1f, 7.0f)
                            shindo = decayedIntensity * boreholeDamping
                            pga = (10f.pow((shindo - 0.94f) / 2.0f)).coerceIn(0.1f, 1500f)
                        } else if (distKm <= pRadius) {
                            // P-wave hit station: initial tremor
                            val pIntensity = (currentEEW.magnitude * 0.5f - 1.2f).coerceIn(0.1f, 2.5f)
                            shindo = pIntensity * boreholeDamping
                            pga = (pIntensity * 2.5f).coerceAtLeast(0.2f)
                        } else {
                            // Ambient micro-tremor
                            shindo = (Random.nextFloat() * 0.15f)
                            pga = (Random.nextFloat() * 0.2f + 0.05f) * boreholeDamping
                        }
                    } else {
                        // Ambient background micro-tremors (常時微動)
                        val noise = (sin(tick * 0.5 + station.lat) * 0.08 + Random.nextFloat() * 0.08).toFloat()
                        shindo = noise.coerceIn(0f, 0.3f)
                        pga = (shindo * 1.5f + 0.05f) * boreholeDamping
                    }

                    val pgv = (pga * 0.12f).coerceAtLeast(0.005f)
                    val pgd = (pgv * 0.08f).coerceAtLeast(0.001f)

                    station.copy(
                        realtimeShindo = shindo,
                        pga = pga,
                        pgv = pgv,
                        pgd = pgd,
                        isBorehole = isBorehole
                    )
                }
                _stations.value = updatedStations

                // Generate 3-axis waveform for selected station or primary epicenter
                val primaryStation = _selectedStation.value ?: updatedStations.maxByOrNull { it.pga }
                val maxGal = primaryStation?.pga ?: 0.5f
                val phase = tick * 0.8
                val sample = SeismographSample(
                    timestamp = System.currentTimeMillis(),
                    ew = (sin(phase * 4.2) * maxGal + (Random.nextFloat() - 0.5f) * (maxGal * 0.3f)).toFloat(),
                    ns = (cos(phase * 3.7) * maxGal * 0.9f + (Random.nextFloat() - 0.5f) * (maxGal * 0.3f)).toFloat(),
                    ud = (sin(phase * 5.1 + 1.2) * maxGal * 0.6f + (Random.nextFloat() - 0.5f) * (maxGal * 0.2f)).toFloat()
                )

                if (waveformBuffer.size >= 80) {
                    waveformBuffer.removeFirst()
                }
                waveformBuffer.addLast(sample)
                _liveWaveform.value = waveformBuffer.toList()
            }
        }
    }

    private fun initStations() {
        // High density representative observation stations covering all 47 prefectures
        // Geographically projected onto 0.0..1.0 bounding box via GeoProjection
        data class RawStation(val name: String, val lat: Double, val lon: Double, val pref: String)

        val rawList = listOf(
            // Hokkaido
            RawStation("稚内", 45.41, 141.67, "北海道"),
            RawStation("旭川", 43.77, 142.36, "北海道"),
            RawStation("網走", 44.02, 144.27, "北海道"),
            RawStation("根室", 43.33, 145.58, "北海道"),
            RawStation("釧路", 42.98, 144.38, "北海道"),
            RawStation("帯広", 42.92, 143.20, "北海道"),
            RawStation("札幌", 43.06, 141.35, "北海道"),
            RawStation("室蘭", 42.31, 140.97, "北海道"),
            RawStation("函館", 41.77, 140.73, "北海道"),
            RawStation("留萌", 43.94, 141.63, "北海道"),
            RawStation("浦河", 42.16, 142.77, "北海道"),

            // Tohoku
            RawStation("青森", 40.82, 140.74, "青森県"),
            RawStation("八戸", 40.51, 141.48, "青森県"),
            RawStation("盛岡", 39.70, 141.15, "岩手県"),
            RawStation("宮古", 39.64, 141.95, "岩手県"),
            RawStation("大船渡", 39.08, 141.71, "岩手県"),
            RawStation("秋田", 39.72, 140.10, "秋田県"),
            RawStation("横手", 39.31, 140.55, "秋田県"),
            RawStation("山形", 38.25, 140.33, "山形県"),
            RawStation("酒田", 38.91, 139.83, "山形県"),
            RawStation("仙台", 38.26, 140.87, "宮城県"),
            RawStation("石巻", 38.43, 141.30, "宮城県"),
            RawStation("気仙沼", 38.90, 141.57, "宮城県"),
            RawStation("福島", 37.76, 140.47, "福島県"),
            RawStation("郡山", 37.40, 140.36, "福島県"),
            RawStation("いわき", 37.05, 140.88, "福島県"),
            RawStation("会津若松", 37.49, 139.93, "福島県"),

            // Kanto
            RawStation("水戸", 36.36, 140.47, "茨城県"),
            RawStation("日立", 36.59, 140.65, "茨城県"),
            RawStation("つくば", 36.08, 140.11, "茨城県"),
            RawStation("宇都宮", 36.56, 139.88, "栃木県"),
            RawStation("日光", 36.75, 139.59, "栃木県"),
            RawStation("前橋", 36.39, 139.06, "群馬県"),
            RawStation("高崎", 36.32, 139.00, "群馬県"),
            RawStation("さいたま", 35.86, 139.64, "埼玉県"),
            RawStation("熊谷", 36.14, 139.38, "埼玉県"),
            RawStation("秩父", 35.99, 139.08, "埼玉県"),
            RawStation("千葉", 35.60, 140.12, "千葉県"),
            RawStation("銚子", 35.73, 140.83, "千葉県"),
            RawStation("館山", 34.99, 139.86, "千葉県"),
            RawStation("東京大手町", 35.68, 139.76, "東京都"),
            RawStation("八王子", 35.65, 139.33, "東京都"),
            RawStation("大島", 34.75, 139.35, "東京都"),
            RawStation("横浜", 35.44, 139.64, "神奈川県"),
            RawStation("小田原", 35.25, 139.15, "神奈川県"),
            RawStation("横須賀", 35.28, 139.67, "神奈川県"),

            // Chubu / Hokuriku
            RawStation("新潟", 37.91, 139.04, "新潟県"),
            RawStation("長岡", 37.44, 138.85, "新潟県"),
            RawStation("上越", 37.14, 138.23, "新潟県"),
            RawStation("佐渡", 38.01, 138.36, "新潟県"),
            RawStation("富山", 36.69, 137.21, "富山県"),
            RawStation("高岡", 36.75, 137.01, "富山県"),
            RawStation("金沢", 36.56, 136.65, "石川県"),
            RawStation("輪島", 37.39, 136.90, "石川県"),
            RawStation("珠洲", 37.43, 137.26, "石川県"),
            RawStation("福井", 36.06, 136.22, "福井県"),
            RawStation("敦賀", 35.64, 136.05, "福井県"),
            RawStation("甲府", 35.66, 138.56, "山梨県"),
            RawStation("富士吉田", 35.48, 138.79, "山梨県"),
            RawStation("長野", 36.65, 138.18, "長野県"),
            RawStation("松本", 36.23, 137.97, "長野県"),
            RawStation("飯田", 35.51, 137.82, "長野県"),
            RawStation("岐阜", 35.42, 136.76, "岐阜県"),
            RawStation("高山", 36.14, 137.25, "岐阜県"),
            RawStation("静岡", 34.97, 138.38, "静岡県"),
            RawStation("浜松", 34.71, 137.72, "静岡県"),
            RawStation("沼津", 35.10, 138.86, "静岡県"),
            RawStation("下田", 34.67, 138.94, "静岡県"),
            RawStation("名古屋", 35.18, 136.90, "愛知県"),
            RawStation("豊橋", 34.76, 137.38, "愛知県"),
            RawStation("岡崎", 34.95, 137.16, "愛知県"),

            // Kinki
            RawStation("津", 34.71, 136.50, "三重県"),
            RawStation("四日市", 34.96, 136.62, "三重県"),
            RawStation("尾鷲", 34.07, 136.19, "三重県"),
            RawStation("大津", 35.01, 135.86, "滋賀県"),
            RawStation("彦根", 35.27, 136.25, "滋賀県"),
            RawStation("京都", 35.01, 135.76, "京都府"),
            RawStation("舞鶴", 35.47, 135.38, "京都府"),
            RawStation("大阪", 34.69, 135.50, "大阪府"),
            RawStation("堺", 34.57, 135.48, "大阪府"),
            RawStation("神戸", 34.69, 135.19, "兵庫県"),
            RawStation("姫路", 34.83, 134.69, "兵庫県"),
            RawStation("豊岡", 35.54, 134.82, "兵庫県"),
            RawStation("洲本", 34.34, 134.89, "兵庫県"),
            RawStation("奈良", 34.68, 135.83, "奈良県"),
            RawStation("十津川", 33.99, 135.79, "奈良県"),
            RawStation("和歌山", 34.23, 135.17, "和歌山県"),
            RawStation("田辺", 33.72, 135.37, "和歌山県"),
            RawStation("潮岬", 33.43, 135.76, "和歌山県"),

            // Chugoku
            RawStation("鳥取", 35.50, 134.23, "鳥取県"),
            RawStation("米子", 35.43, 133.33, "鳥取県"),
            RawStation("松江", 35.47, 133.05, "島根県"),
            RawStation("浜田", 34.89, 132.07, "島根県"),
            RawStation("隠岐", 36.20, 133.32, "島根県"),
            RawStation("岡山", 34.65, 133.91, "岡山県"),
            RawStation("倉敷", 34.58, 133.77, "岡山県"),
            RawStation("津山", 35.06, 134.00, "岡山県"),
            RawStation("広島", 34.38, 132.45, "広島県"),
            RawStation("福山", 34.48, 133.36, "広島県"),
            RawStation("呉", 34.24, 132.56, "広島県"),
            RawStation("山口", 34.17, 131.47, "山口県"),
            RawStation("下関", 33.95, 130.93, "山口県"),
            RawStation("萩", 34.40, 131.39, "山口県"),

            // Shikoku
            RawStation("徳島", 34.07, 134.55, "徳島県"),
            RawStation("美波", 33.73, 134.53, "徳島県"),
            RawStation("高松", 34.34, 134.04, "香川県"),
            RawStation("丸亀", 34.28, 133.79, "香川県"),
            RawStation("松山", 33.83, 132.76, "愛媛県"),
            RawStation("今治", 34.06, 132.99, "愛媛県"),
            RawStation("宇和島", 33.22, 132.56, "愛媛県"),
            RawStation("高知", 33.55, 133.53, "高知県"),
            RawStation("室戸", 33.28, 134.14, "高知県"),
            RawStation("宿毛", 32.93, 132.72, "高知県"),

            // Kyushu
            RawStation("福岡", 33.59, 130.40, "福岡県"),
            RawStation("北九州", 33.88, 130.87, "福岡県"),
            RawStation("久留米", 33.31, 130.51, "福岡県"),
            RawStation("佐賀", 33.26, 130.30, "佐賀県"),
            RawStation("唐津", 33.44, 129.96, "佐賀県"),
            RawStation("長崎", 32.75, 129.87, "長崎県"),
            RawStation("佐世保", 33.17, 129.72, "長崎県"),
            RawStation("対馬", 34.20, 129.28, "長崎県"),
            RawStation("熊本", 32.80, 130.70, "熊本県"),
            RawStation("八代", 32.50, 130.60, "熊本県"),
            RawStation("天草", 32.45, 130.19, "熊本県"),
            RawStation("阿蘇", 32.93, 131.11, "熊本県"),
            RawStation("大分", 33.23, 131.60, "大分県"),
            RawStation("別府", 33.28, 131.49, "大分県"),
            RawStation("佐伯", 32.96, 131.89, "大分県"),
            RawStation("宮崎", 31.90, 131.42, "宮崎県"),
            RawStation("延岡", 32.58, 131.66, "宮崎県"),
            RawStation("都城", 31.71, 131.06, "宮崎県"),
            RawStation("鹿児島", 31.59, 130.55, "鹿児島県"),
            RawStation("鹿屋", 31.37, 130.85, "鹿児島県"),
            RawStation("枕崎", 31.27, 130.29, "鹿児島県"),
            RawStation("奄美", 28.37, 129.49, "鹿児島県"),

            // Okinawa
            RawStation("那覇", 26.21, 127.68, "沖縄県"),
            RawStation("名護", 26.59, 127.97, "沖縄県"),
            RawStation("石垣", 24.34, 124.15, "沖縄県"),
            RawStation("宮古島", 24.80, 125.28, "沖縄県")
        )

        val stationList = rawList.mapIndexed { idx, raw ->
            ObservationStation(
                id = "station_$idx",
                name = raw.name,
                prefecture = raw.pref,
                region = "",
                lat = raw.lat,
                lon = raw.lon,
                mapX = GeoProjection.toNormX(raw.lon),
                mapY = GeoProjection.toNormY(raw.lat),
                realtimeShindo = 0f,
                pga = 0.05f,
                pgv = 0.01f,
                pgd = 0.001f,
                isBorehole = false
            )
        }
        _stations.value = stationList
    }
}
