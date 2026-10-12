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
                    PrefectureIntensity("千葉県", "3"),
                    PrefectureIntensity("茨城県", "2"),
                    PrefectureIntensity("東京都", "1")
                )
            ),
            EarthquakeItem(
                id = "demo_2",
                time = "10/11 14:12",
                hypocenter = "福島県沖",
                maxScale = "4",
                magnitude = 5.1,
                depthKm = 50,
                tsunamiInfo = "津波の心配なし",
                lat = 37.5,
                lon = 141.6,
                intensityPoints = listOf(
                    PrefectureIntensity("福島県", "4"),
                    PrefectureIntensity("宮城県", "3"),
                    PrefectureIntensity("茨城県", "2")
                )
            ),
            EarthquakeItem(
                id = "demo_3",
                time = "10/10 22:30",
                hypocenter = "日向灘",
                maxScale = "3",
                magnitude = 4.7,
                depthKm = 30,
                tsunamiInfo = "津波の心配なし",
                lat = 31.9,
                lon = 131.8,
                intensityPoints = listOf(
                    PrefectureIntensity("宮崎県", "3"),
                    PrefectureIntensity("鹿児島県", "2"),
                    PrefectureIntensity("大分県", "1")
                )
            ),
            EarthquakeItem(
                id = "demo_4",
                time = "10/09 08:15",
                hypocenter = "能登半島沖",
                maxScale = "3",
                magnitude = 4.2,
                depthKm = 10,
                tsunamiInfo = "津波の心配なし",
                lat = 37.4,
                lon = 137.2,
                intensityPoints = listOf(
                    PrefectureIntensity("石川県", "3"),
                    PrefectureIntensity("富山県", "2")
                )
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
                    PrefectureIntensity("茨城県", "4"),
                    PrefectureIntensity("栃木県", "3"),
                    PrefectureIntensity("埼玉県", "3"),
                    PrefectureIntensity("東京都", "2")
                )
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
        // High density representative stations covering all 47 prefectures and key seismic monitoring zones
        // Normalized map coordinates: Japan is projected onto 0.0..1.0 bounding box
        // Lon: ~128°E to 146°E -> mapped to X: 0.05 to 0.95
        // Lat: ~30°N to 45.5°N -> mapped to Y: 0.95 to 0.05 (North is top)
        fun projectY(lat: Double): Float {
            return (0.08f + (45.5f - lat.toFloat()) * 0.042f).coerceIn(0.02f, 0.98f)
        }
        fun projectX(lat: Double, lon: Double): Float {
            val baseLon = 130.40
            val baseX = 0.24f
            val dLon = (lon - baseLon).toFloat()
            val latTilt = (lat.toFloat() - 35.0f) * 0.006f
            return (baseX + dLon * 0.0405f + latTilt).coerceIn(0.02f, 0.98f)
        }

        val rawList = listOf(
            // Hokkaido
            Triple("稚内", 45.41, 141.67),
            Triple("旭川", 43.77, 142.36),
            Triple("網走", 44.02, 144.27),
            Triple("根室", 43.33, 145.58),
            Triple("釧路", 42.98, 144.38),
            Triple("帯広", 42.92, 143.20),
            Triple("札幌", 43.06, 141.35),
            Triple("室蘭", 42.31, 140.97),
            Triple("函館", 41.77, 140.73),
            Triple("留萌", 43.94, 141.63),
            Triple("浦河", 42.16, 142.77),

            // Tohoku
            Triple("青森", 40.82, 140.74),
            Triple("八戸", 40.51, 141.48),
            Triple("盛岡", 39.70, 141.15),
            Triple("宮古", 39.64, 141.95),
            Triple("大船渡", 39.08, 141.71),
            Triple("秋田", 39.72, 140.10),
            Triple("横手", 39.31, 140.55),
            Triple("山形", 38.25, 140.33),
            Triple("酒田", 38.91, 139.83),
            Triple("仙台", 38.26, 140.87),
            Triple("石巻", 38.43, 141.30),
            Triple("気仙沼", 38.90, 141.57),
            Triple("福島", 37.76, 140.47),
            Triple("郡山", 37.40, 140.36),
            Triple("いわき", 37.05, 140.88),
            Triple("会津若松", 37.49, 139.93),

            // Kanto
            Triple("水戸", 36.36, 140.47),
            Triple("日立", 36.59, 140.65),
            Triple("つくば", 36.08, 140.11),
            Triple("宇都宮", 36.56, 139.88),
            Triple("日光", 36.75, 139.59),
            Triple("前橋", 36.39, 139.06),
            Triple("高崎", 36.32, 139.00),
            Triple("さいたま", 35.86, 139.64),
            Triple("熊谷", 36.14, 139.38),
            Triple("秩父", 35.99, 139.08),
            Triple("千葉", 35.60, 140.12),
            Triple("銚子", 35.73, 140.83),
            Triple("館山", 34.99, 139.86),
            Triple("東京大手町", 35.68, 139.76),
            Triple("八王子", 35.65, 139.33),
            Triple("大島", 34.75, 139.35),
            Triple("横浜", 35.44, 139.64),
            Triple("小田原", 35.25, 139.15),
            Triple("横須賀", 35.28, 139.67),

            // Chubu / Hokuriku
            Triple("新潟", 37.91, 139.04),
            Triple("長岡", 37.44, 138.85),
            Triple("上越", 37.14, 138.23),
            Triple("佐渡", 38.01, 138.36),
            Triple("富山", 36.69, 137.21),
            Triple("高岡", 36.75, 137.01),
            Triple("金沢", 36.56, 136.65),
            Triple("輪島", 37.39, 136.90),
            Triple("珠洲", 37.43, 137.26),
            Triple("福井", 36.06, 136.22),
            Triple("敦賀", 35.64, 136.05),
            Triple("甲府", 35.66, 138.56),
            Triple("富士吉田", 35.48, 138.79),
            Triple("長野", 36.65, 138.18),
            Triple("松本", 36.23, 137.97),
            Triple("飯田", 35.51, 137.82),
            Triple("岐阜", 35.42, 136.76),
            Triple("高山", 36.14, 137.25),
            Triple("静岡", 34.97, 138.38),
            Triple("浜松", 34.71, 137.72),
            Triple("沼津", 35.10, 138.86),
            Triple("下田", 34.67, 138.94),
            Triple("名古屋", 35.18, 136.90),
            Triple("豊橋", 34.76, 137.38),
            Triple("岡崎", 34.95, 137.16),

            // Kinki
            Triple("津", 34.71, 136.50),
            Triple("四日市", 34.96, 136.62),
            Triple("尾鷲", 34.07, 136.19),
            Triple("大津", 35.01, 135.86),
            Triple("彦根", 35.27, 136.25),
            Triple("京都", 35.01, 135.76),
            Triple("舞鶴", 35.47, 135.38),
            Triple("大阪", 34.69, 135.50),
            Triple("堺", 34.57, 135.48),
            Triple("神戸", 34.69, 135.19),
            Triple("姫路", 34.83, 134.69),
            Triple("豊岡", 35.54, 134.82),
            Triple("洲本", 34.34, 134.89),
            Triple("奈良", 34.68, 135.83),
            Triple("十津川", 33.99, 135.79),
            Triple("和歌山", 34.23, 135.17),
            Triple("田辺", 33.72, 135.37),
            Triple("潮岬", 33.43, 135.76),

            // Chugoku
            Triple("鳥取", 35.50, 134.23),
            Triple("米子", 35.43, 133.33),
            Triple("松江", 35.47, 133.05),
            Triple("浜田", 34.89, 132.07),
            Triple("隠岐", 36.20, 133.32),
            Triple("岡山", 34.65, 133.91),
            Triple("倉敷", 34.58, 133.77),
            Triple("津山", 35.06, 134.00),
            Triple("広島", 34.38, 132.45),
            Triple("福山", 34.48, 133.36),
            Triple("呉", 34.24, 132.56),
            Triple("山口", 34.17, 131.47),
            Triple("下関", 33.95, 130.93),
            Triple("萩", 34.40, 131.39),

            // Shikoku
            Triple("徳島", 34.07, 134.55),
            Triple("美波", 33.73, 134.53),
            Triple("高松", 34.34, 134.04),
            Triple("丸亀", 34.28, 133.79),
            Triple("松山", 33.83, 132.76),
            Triple("今治", 34.06, 132.99),
            Triple("宇和島", 33.22, 132.56),
            Triple("高知", 33.55, 133.53),
            Triple("室戸", 33.28, 134.14),
            Triple("宿毛", 32.93, 132.72),

            // Kyushu
            Triple("福岡", 33.59, 130.40),
            Triple("北九州", 33.88, 130.87),
            Triple("久留米", 33.31, 130.51),
            Triple("佐賀", 33.26, 130.30),
            Triple("唐津", 33.44, 129.96),
            Triple("長崎", 32.75, 129.87),
            Triple("佐世保", 33.17, 129.72),
            Triple("対馬", 34.20, 129.28),
            Triple("熊本", 32.80, 130.70),
            Triple("八代", 32.50, 130.60),
            Triple("天草", 32.45, 130.19),
            Triple("阿蘇", 32.93, 131.11),
            Triple("大分", 33.23, 131.60),
            Triple("別府", 33.28, 131.49),
            Triple("佐伯", 32.96, 131.89),
            Triple("宮崎", 31.90, 131.42),
            Triple("延岡", 32.58, 131.66),
            Triple("都城", 31.71, 131.06),
            Triple("鹿児島", 31.59, 130.55),
            Triple("鹿屋", 31.37, 130.85),
            Triple("枕崎", 31.27, 130.29),
            Triple("奄美", 28.37, 129.49),

            // Okinawa
            Triple("那覇", 26.21, 127.68),
            Triple("名護", 26.59, 127.97),
            Triple("石垣", 24.34, 124.15),
            Triple("宮古島", 24.80, 125.28)
        )

        val stationList = rawList.mapIndexed { idx, (name, lat, lon) ->
            ObservationStation(
                id = "station_$idx",
                name = name,
                prefecture = "",
                region = "",
                lat = lat,
                lon = lon,
                mapX = projectX(lat, lon),
                mapY = projectY(lat),
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
