package com.nexus.portal.data.repository

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class SensorData(
    val accelValues: FloatArray,
    val gyroValues: FloatArray,
    val magValues: FloatArray,
    val timestamp: Long
)

/**
 * Industrial Standard Sensor Repository.
 * Wraps SensorManager to provide a continuous, cold sensor stream via callbackFlow.
 * Sensors are registered automatically when the flow is collected and unregistered
 * when collection is cancelled — no manual startListening/stopListening required.
 * The service controls the sensor lifecycle by launching/cancelling a collection coroutine.
 */
interface SensorRepository {
    val sensorFlow: Flow<SensorData>
}



class DefaultSensorRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorRepository {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override val sensorFlow: Flow<SensorData> = callbackFlow {
        val accel = FloatArray(3)
        val gyro = FloatArray(3)
        val mag = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                var changed = false
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accel, 0, 3)
                        changed = true
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        System.arraycopy(event.values, 0, gyro, 0, 3)
                        changed = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, mag, 0, 3)
                        changed = true
                    }
                }

                if (changed) {
                    trySend(SensorData(
                        accelValues = accel.clone(),
                        gyroValues = gyro.clone(),
                        magValues = mag.clone(),
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Always register sensors when the flow is collected (no isListening guard).
        // The callbackFlow lifecycle is the sole gate for hardware sensor registration.
        if (sensorManager != null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        awaitClose {
            sensorManager?.unregisterListener(listener)
        }
    }
}
