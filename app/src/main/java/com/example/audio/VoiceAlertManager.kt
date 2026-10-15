package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.model.EEWAlert
import com.example.model.EarthquakeItem
import com.example.model.ObservationStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

enum class VoiceDetailLevel(val label: String) {
    DETAILED("詳細読み上げ（NHK放送級）"),
    STANDARD("標準読み上げ"),
    CONCISE("簡潔（カウントダウン優先）")
}

class VoiceAlertManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "VoiceAlertManager"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val prefs = context.getSharedPreferences("voice_alert_prefs", Context.MODE_PRIVATE)

    private val _isVoiceEnabled = MutableStateFlow(prefs.getBoolean("voice_enabled", true))
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private val _isSoundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1.15f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _detailLevel = MutableStateFlow(
        try {
            VoiceDetailLevel.valueOf(prefs.getString("detail_level", VoiceDetailLevel.DETAILED.name) ?: VoiceDetailLevel.DETAILED.name)
        } catch (e: Exception) {
            VoiceDetailLevel.DETAILED
        }
    )
    val detailLevel: StateFlow<VoiceDetailLevel> = _detailLevel.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            tts?.setSpeechRate(_speechRate.value)
            isTtsReady = true
            Log.i(tag, "TextToSpeech successfully initialized with rate=${_speechRate.value}")
        } else {
            Log.e(tag, "TextToSpeech initialization failed: $status")
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        _isVoiceEnabled.value = enabled
        prefs.edit().putBoolean("voice_enabled", enabled).apply()
        if (!enabled) {
            tts?.stop()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        _speechRate.value = clamped
        prefs.edit().putFloat("speech_rate", clamped).apply()
        tts?.setSpeechRate(clamped)
        Log.i(tag, "setSpeechRate updated: $clamped (isTtsReady=$isTtsReady)")
    }

    fun setDetailLevel(level: VoiceDetailLevel) {
        _detailLevel.value = level
        prefs.edit().putString("detail_level", level.name).apply()
    }

    fun testSpeech(customRate: Float? = null) {
        if (customRate != null) {
            setSpeechRate(customRate)
        }
        val rate = _speechRate.value
        val speedDesc = when {
            rate <= 0.85f -> "ゆっくり"
            rate in 0.86f..1.1f -> "標準速度"
            rate in 1.11f..1.35f -> "やや速め"
            rate in 1.36f..1.65f -> "速め"
            else -> "緊急速報用高速"
        }
        val text = "読み上げ速度のテストです。現在の設定速度は、${String.format("%.2f", rate)}倍、$speedDesc です。"
        speak(text, flush = true)
    }

    fun speak(text: String, flush: Boolean = false) {
        if (!_isVoiceEnabled.value || !isTtsReady) return
        // Enforce the current speech rate right before speaking to guarantee the rate is applied
        tts?.setSpeechRate(_speechRate.value)
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, "utterance_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    /**
     * Synthesize authentic alert chime (880Hz / 659.25Hz alternating harmonics)
     */
    fun playEEWChime() {
        if (!_isSoundEnabled.value) return
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 44100
                val durationSec = 0.85
                val numSamples = (sampleRate * durationSec).toInt()
                val samples = ShortArray(numSamples)

                val tone1Freq = 880.0
                val tone2Freq = 659.25

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val freq = if ((time / 0.2).toInt() % 2 == 0) tone1Freq else tone2Freq
                    val angle = 2.0 * Math.PI * freq * time
                    val amp = sin(Math.PI * (i.toDouble() / numSamples))
                    samples[i] = (sin(angle) * 30000 * amp).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
            } catch (e: Exception) {
                Log.e(tag, "AudioTrack chime failed", e)
            }
        }
    }

    fun playCountdownBeep(highPitch: Boolean = false) {
        if (!_isSoundEnabled.value) return
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 22050
                val durationSec = 0.08
                val numSamples = (sampleRate * durationSec).toInt()
                val samples = ShortArray(numSamples)
                val freq = if (highPitch) 1320.0 else 880.0

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val angle = 2.0 * Math.PI * freq * time
                    val env = 1.0 - (i.toDouble() / numSamples)
                    samples[i] = (sin(angle) * 28000 * env).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
            } catch (e: Exception) {
                Log.e(tag, "Countdown beep failed", e)
            }
        }
    }

    fun vibrateAlert(isStrong: Boolean = false) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = if (isStrong) {
                    longArrayOf(0, 300, 100, 300, 100, 500)
                } else {
                    longArrayOf(0, 200, 100, 200)
                }
                val amplitudes = if (isStrong) {
                    intArrayOf(0, 255, 0, 255, 0, 255)
                } else {
                    intArrayOf(0, 180, 0, 180)
                }
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e(tag, "Vibration failed", e)
        }
    }

    /**
     * Highly detailed Emergency Earthquake Warning announcement with location, detailed municipal regions, and tsunami warning
     */
    fun announceDetailedEEW(
        alert: EEWAlert,
        userLocationName: String? = null,
        userDistanceKm: Float? = null,
        userEtaSec: Int? = null,
        userEstimatedShindo: String? = null
    ) {
        playEEWChime()
        vibrateAlert(isStrong = alert.isWarning)

        val reportPrefix = if (alert.isDrill) "これは、緊急地震速報の訓練放送です。" else ""
        val warningCategory = if (alert.isWarning) "緊急地震速報、警報が発表されました。" else "緊急地震速報、予報です。"

        val message = buildString {
            append(reportPrefix)
            append(warningCategory)
            if (alert.reportNum > 1) {
                append("第${alert.reportNum}報。")
            }
            append("震源地は、${alert.hypocenter}。")
            append("震源の深さは約${alert.depthKm}キロメートル、")
            append("地震の規模、マグニチュードは${alert.magnitude}、")
            append("推定される最大震度は、震度${alert.maxShindo}です。")

            // Location information integration
            if (userDistanceKm != null && userLocationName != null) {
                append("現在地、${userLocationName}から震源までの距離は、およそ${userDistanceKm.toInt()}キロです。")
                if (userEtaSec != null && userEtaSec > 0) {
                    append("主要動到達まで、あと約${userEtaSec}秒です。")
                }
                if (userEstimatedShindo != null) {
                    append("あなたの地域での予想震度は、震度${userEstimatedShindo}です。")
                }
            }

            // Prefecture level
            if (alert.affectedPrefectures.isNotEmpty()) {
                append("強い揺れが警戒される都道府県は、${alert.affectedPrefectures.take(5).joinToString("、")}です。")
            }

            // Detailed Municipal / Sub-regional areas
            if (alert.detailedAreas.isNotEmpty()) {
                append("詳しい警戒地域は、${alert.detailedAreas.take(6).joinToString("、")}です。")
            }

            // Tsunami Warning announcement
            if (alert.tsunamiWarning.isNotEmpty() && alert.tsunamiWarning != "津波の心配なし") {
                append("津波情報です。${alert.tsunamiWarning}。海岸や河口付近にいる方は、直ちに高台や避難ビルなど安全な場所へ避難してください！")
            }

            if (alert.isWarning) {
                append("倒れる家具や落下物から身を守り、ただちに安全な場所に避難してください！")
            } else {
                append("今後の情報に十分注意してください。")
            }
        }
        speak(message, flush = true)
    }

    /**
     * Detailed countdown announcements with vocal urgency
     */
    fun announceCountdown(seconds: Int) {
        playCountdownBeep(highPitch = seconds <= 3)
        if (!_isVoiceEnabled.value) return

        when (seconds) {
            20 -> speak("主要動到達まで、あと20秒", flush = true)
            15 -> speak("あと15秒", flush = true)
            10 -> speak("あと10秒", flush = true)
            9 -> speak("9", flush = true)
            8 -> speak("8", flush = true)
            7 -> speak("7", flush = true)
            6 -> speak("6", flush = true)
            5 -> speak("5秒前", flush = true)
            4 -> speak("4", flush = true)
            3 -> speak("3", flush = true)
            2 -> speak("2", flush = true)
            1 -> speak("1", flush = true)
            0 -> speak("主要動到達！頭部を保護し、強い揺れに備えてください！", flush = true)
        }
    }

    /**
     * Highly detailed real earthquake report with prefectural intensity breakdown,
     * detailed cities/municipalities, and urgent tsunami warnings
     */
    fun announceDetailedEarthquakeReport(item: EarthquakeItem) {
        val message = buildString {
            append("気象庁発表の地震情報をお伝えします。")
            val cleanTime = item.time.replace("-", "年").replace(" ", "日 ").replace(":", "時") + "分"
            append("${item.time}頃、")
            append("${item.hypocenter}を震源とする地震がありました。")
            append("震源の深さは約${item.depthKm}キロメートル、")
            append("地震の規模、マグニチュードは${item.magnitude}と推定されます。")
            append("この地震により、最大震度${item.maxScale}が観測されました。")

            // Detailed Breakdown of all observed prefectures and municipal cities
            if (item.intensityPoints.isNotEmpty()) {
                val grouped = item.intensityPoints.groupBy { it.scale }
                // Sort scales from highest to lowest
                val scaleOrder = listOf("7", "6強", "6弱", "5強", "5弱", "4", "3", "2", "1")
                val sortedScales = grouped.keys.sortedWith(compareBy { scale ->
                    val idx = scaleOrder.indexOf(scale)
                    if (idx >= 0) idx else 99
                })

                for (scale in sortedScales) {
                    val pts = grouped[scale] ?: continue
                    val prefNames = pts.map { it.prefecture }.distinct()
                    append("震度${scale}を観測した地域は、${prefNames.joinToString("、")}です。")

                    // List detailed cities/municipalities in this intensity bracket
                    val allCities = pts.flatMap { it.cities }.distinct()
                    if (allCities.isNotEmpty()) {
                        append("詳しい観測市町村は、${allCities.take(10).joinToString("、")}です。")
                    }
                }
            } else if (item.detailedRegions.isNotEmpty()) {
                append("震度を観測した詳しい地域は、${item.detailedRegions.joinToString("、")}です。")
            }

            // Tsunami Warning details and immediate action alert
            if (item.tsunamiInfo.contains("大津波警報")) {
                append("大津波警報が発表されています！沿岸部や川の河口付近にいる方は、直ちに高台や津波避難ビルへ逃げてください！命を守る行動を最優先してください！")
            } else if (item.tsunamiInfo.contains("津波警報")) {
                append("津波警報が発表されています！海岸付近にいる方は、直ちに高台など安全な場所へ避難してください！津波は第1波より第2波以降が高くなることがあります！")
            } else if (item.tsunamiInfo.contains("津波注意報")) {
                append("津波注意報が発表されています。海の中にいる方は直ちに海から上がり、海岸から離れてください。")
            } else {
                append("${item.tsunamiInfo}。")
            }

            append("今後の揺れや余震活動にも十分ご注意ください。")
        }
        speak(message, flush = true)
    }

    /**
     * Station tremor / strong motion detection voice reading
     */
    fun announceStationMotion(station: ObservationStation) {
        val message = "強震モニタ検知。${station.name}観測点で、最大加速度 ${String.format("%.1f", station.pga)} ガル、リアルタイム震度 ${String.format("%.1f", station.realtimeShindo)} を検知しました。"
        speak(message, flush = false)
    }

    /**
     * Device accelerometer local shake detection announcement
     */
    fun announceDeviceShake(gal: Float, shindoText: String) {
        playCountdownBeep(highPitch = true)
        vibrateAlert(isStrong = true)
        val message = "端末センサーが揺れを検知しました。瞬時最大加速度 ${gal.toInt()} ガル、推定計測震度は震度 $shindoText です。周囲の安全を確認してください。"
        speak(message, flush = true)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
