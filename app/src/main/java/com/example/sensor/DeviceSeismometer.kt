package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.SeismographSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class SeismometerState(
    val currentGal: Float = 0f,
    val peakGal: Float = 0f,
    val estimatedShindo: String = "0",
    val isShaking: Boolean = false,
    val samples: List<SeismographSample> = emptyList()
)

class DeviceSeismometer(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _state = MutableStateFlow(SeismometerState())
    val state: StateFlow<SeismometerState> = _state.asStateFlow()

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.8f
    private val alpha = 0.8f // High-pass filter constant

    private var currentPeak = 0f
    private val recentSamples = ArrayDeque<SeismographSample>(120)

    var onShakeDetected: ((gal: Float, shindo: String) -> Unit)? = null
    private var lastShakeTriggerTime = 0L

    fun start() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    fun resetPeak() {
        currentPeak = 0f
        _state.value = _state.value.copy(peakGal = 0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // High-pass filter to isolate dynamic acceleration from Earth's static gravity
        gravityX = alpha * gravityX + (1 - alpha) * event.values[0]
        gravityY = alpha * gravityY + (1 - alpha) * event.values[1]
        gravityZ = alpha * gravityZ + (1 - alpha) * event.values[2]

        val linearX = event.values[0] - gravityX
        val linearY = event.values[1] - gravityY
        val linearZ = event.values[2] - gravityZ

        // Convert m/s^2 to Gal (1 m/s^2 = 100 Gal)
        val linearMag = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        val currentGal = (linearMag * 100f).coerceAtLeast(0f)

        if (currentGal > currentPeak) {
            currentPeak = currentGal
        }

        val shindoEstimate = calculateEstimatedShindo(currentGal)
        val isShaking = currentGal >= 5.0f

        // Buffer waveform samples for graphing
        val sample = SeismographSample(
            timestamp = System.currentTimeMillis(),
            ew = linearX * 100f,
            ns = linearY * 100f,
            ud = linearZ * 100f
        )
        if (recentSamples.size >= 100) {
            recentSamples.removeFirst()
        }
        recentSamples.addLast(sample)

        _state.value = SeismometerState(
            currentGal = currentGal,
            peakGal = currentPeak,
            estimatedShindo = shindoEstimate,
            isShaking = isShaking,
            samples = recentSamples.toList()
        )

        // Trigger shake alert if Gal > 15 (~Shindo 2+) and debounce by 5 seconds
        val now = System.currentTimeMillis()
        if (currentGal >= 15f && (now - lastShakeTriggerTime > 5000)) {
            lastShakeTriggerTime = now
            onShakeDetected?.invoke(currentGal, shindoEstimate)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateEstimatedShindo(gal: Float): String {
        if (gal < 0.6f) return "0"
        // Approximation of JMA instrumental intensity formula: I ≈ 2 * log10(gal) + 0.94
        val rawIntensity = 2.0 * log10(gal.toDouble()) + 0.94
        return when {
            rawIntensity < 0.5 -> "0"
            rawIntensity < 1.5 -> "1"
            rawIntensity < 2.5 -> "2"
            rawIntensity < 3.5 -> "3"
            rawIntensity < 4.5 -> "4"
            rawIntensity < 5.0 -> "5弱"
            rawIntensity < 5.5 -> "5強"
            rawIntensity < 6.0 -> "6弱"
            rawIntensity < 6.5 -> "6強"
            else -> "7"
        }
    }
}
