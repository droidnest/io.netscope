package io.netscope.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.netscope.NetScope

/**
 * Detects a shake gesture using the accelerometer and invokes [onShake].
 *
 * Usage:
 * ```kotlin
 * val shakeDetector = NetScopeShakeDetector(context) {
 *     NetScopeActivity.launch(context)
 * }
 *
 * // In onResume / onStart:
 * shakeDetector.start()
 *
 * // In onPause / onStop:
 * shakeDetector.stop()
 * ```
 *
 * @param context   Any context; only used to obtain [SensorManager].
 * @param threshold Acceleration in m/s², on top of gravity, that a jolt must pass to count
 *                  (default `13f`). A shake is [SHAKE_PEAKS] such jolts within [SHAKE_WINDOW_MS],
 *                  so a single bump, putting the phone down or walking does not open NetScope.
 * @param onShake   Callback invoked on the sensor-listener thread when a shake
 *                  is detected.  Dispatch to the main thread if you need UI work.
 */
class NetScopeShakeDetector(
    context: Context,
    private val threshold: Float = 13f,
    private val onShake: () -> Unit,
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Compares squares, so the sensor hot path needs no sqrt():
    // |a| - GRAVITY_EARTH > threshold  ⇔  |a|² > (GRAVITY_EARTH + threshold)²
    private val magnitudeThresholdSquared =
        (SensorManager.GRAVITY_EARTH + threshold) * (SensorManager.GRAVITY_EARTH + threshold)

    private val peaks = ShakePeaks()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitudeSquared = x * x + y * y + z * z
            if (magnitudeSquared > magnitudeThresholdSquared && peaks.isShake(nowMs = event.timestamp / NANOS_PER_MS)) {
                onShake()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /**
     * Starts listening for shake gestures.
     * Call from `Activity.onResume()` or `Service.onStartCommand()`.
     * A no-op when no accelerometer is present.
     */
    fun start() {
        if (NetScope.isInitialized() && !NetScope.shakeEnabled) return
        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Stops listening for shake gestures.
     * Call from `Activity.onPause()` or when the detector is no longer needed.
     */
    fun stop() {
        sensorManager.unregisterListener(listener)
        peaks.reset()
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

internal const val SHAKE_PEAKS = 3
internal const val SHAKE_WINDOW_MS = 1_000L
internal const val PEAK_GAP_MS = 120L
internal const val SHAKE_COOLDOWN_MS = 1_500L

/**
 * Counts the jolts over the threshold and tells when they make a shake. Readings closer than
 * [PEAK_GAP_MS] belong to the same jolt, which lasts several samples, so they count once.
 */
internal class ShakePeaks {
    private val times = ArrayDeque<Long>()
    private var lastShakeMs = Long.MIN_VALUE / 2

    /** Records a reading over the threshold at [nowMs], and returns whether it completes a shake. */
    fun isShake(nowMs: Long): Boolean {
        if (nowMs - lastShakeMs < SHAKE_COOLDOWN_MS) return false
        val last = times.lastOrNull()
        if (last != null && nowMs - last < PEAK_GAP_MS) return false
        times.addLast(nowMs)
        while (nowMs - times.first() > SHAKE_WINDOW_MS) times.removeFirst()
        if (times.size < SHAKE_PEAKS) return false
        times.clear()
        lastShakeMs = nowMs
        return true
    }

    fun reset() {
        times.clear()
    }
}
