package io.netscope.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

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
 * @param threshold Acceleration threshold in m/s² above which a shake is detected
 *                  (default `12f` ≈ 1.2 g, comfortable for deliberate shakes).
 * @param onShake   Callback invoked on the sensor-listener thread when a shake
 *                  is detected.  Dispatch to the main thread if you need UI work.
 */
class NetScopeShakeDetector(
    context: Context,
    private val threshold: Float = 12f,
    private val onShake: () -> Unit,
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Pre-computed: avoids sqrt() on the 100Hz sensor hot-path.
    // Original condition: sqrt(|a|) - GRAVITY_EARTH > threshold
    // Equivalent:         |a|² > (GRAVITY_EARTH + threshold)²
    private val magnitudeThresholdSquared =
        (SensorManager.GRAVITY_EARTH + threshold) * (SensorManager.GRAVITY_EARTH + threshold)

    private var lastShakeTime = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitudeSquared = x * x + y * y + z * z
            if (magnitudeSquared > magnitudeThresholdSquared) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > COOLDOWN_MS) {
                    lastShakeTime = now
                    onShake()
                }
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
    }

    private companion object {
        const val COOLDOWN_MS = 1_000L
    }
}
