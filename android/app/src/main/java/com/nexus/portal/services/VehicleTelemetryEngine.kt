package com.nexus.portal.services

import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class VehicleTelemetryEngine {
    // Calibration vectors (device frame)
    private var uZ = floatArrayOf(0f, 0f, 1f) // Gravity (downward)
    private var uY = floatArrayOf(0f, 1f, 0f) // Forward
    private var uX = floatArrayOf(1f, 0f, 0f) // Lateral (right)
    private var isCalibratedY = false

    private val gravity = FloatArray(3)
    private val linearAccel = FloatArray(3)
    private var hasGravity = false

    // EMA smoothing alphas - tuned for professional-grade tracking (Draggy-like)
    private val ALPHA_GRAVITY = 0.98f  // Slow filter to isolate gravity
    private val ALPHA_FORWARD = 0.02f  // Slow update rate for mounting forward vector
    private val ALPHA_G_FORCE = 0.08f  // Heavy smoothing to eliminate engine vibration and phone wobble
    private val ALPHA_GPS_ACCEL = 0.40f  // Moderate smoothing for GPS-derived acceleration

    // Smoothed output values
    private var smoothLongG = 0f
    private var smoothLatG = 0f
    private var smoothLeanDeg = 0f
    
    // GPS-based acceleration tracking
    private var lastSpeedMps = 0f
    private var lastGpsAccelMps2 = 0f
    private var lastUpdateTimeMs = 0L

    fun processSensors(
        accelValues: FloatArray,
        gyroValues: FloatArray?,
        speedMps: Float,
        isTwoWheeler: Boolean
    ): TelemetryData {
        // 1. Isolate Gravity (Low-pass)
        if (!hasGravity) {
            System.arraycopy(accelValues, 0, gravity, 0, 3)
            hasGravity = true
        } else {
            for (i in 0..2) {
                gravity[i] = ALPHA_GRAVITY * gravity[i] + (1 - ALPHA_GRAVITY) * accelValues[i]
            }
        }

        // 2. Linear Acceleration (High-pass)
        for (i in 0..2) {
            linearAccel[i] = accelValues[i] - gravity[i]
        }

        // 3. Calibrate Vertical Vector (uZ)
        val gravityMag = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (gravityMag > 0.1f) {
            uZ[0] = gravity[0] / gravityMag
            uZ[1] = gravity[1] / gravityMag
            uZ[2] = gravity[2] / gravityMag
        }

        // 4. Dynamic Forward Alignment (uY)
        // If we detect straight-line acceleration (high forward accel, low turning rate)
        val gyroMag = gyroValues?.let { sqrt(it[0]*it[0] + it[1]*it[1] + it[2]*it[2]) } ?: 0f
        val rawLinAccelMag = sqrt(linearAccel[0]*linearAccel[0] + linearAccel[1]*linearAccel[1] + linearAccel[2]*linearAccel[2])
        
        if (rawLinAccelMag > 1.2f && gyroMag < 0.25f) {
            // Project linear acceleration onto the plane perpendicular to uZ
            val dot = linearAccel[0] * uZ[0] + linearAccel[1] * uZ[1] + linearAccel[2] * uZ[2]
            val projX = linearAccel[0] - dot * uZ[0]
            val projY = linearAccel[1] - dot * uZ[1]
            val projZ = linearAccel[2] - dot * uZ[2]
            val projMag = sqrt(projX * projX + projY * projY + projZ * projZ)

            if (projMag > 0.5f) {
                // Forward vector aligns with acceleration inertial force
                // Positive linear acceleration = positive forward vector
                val targetUY_X = projX / projMag
                val targetUY_Y = projY / projMag
                val targetUY_Z = projZ / projMag

                if (!isCalibratedY) {
                    uY[0] = targetUY_X
                    uY[1] = targetUY_Y
                    uY[2] = targetUY_Z
                    isCalibratedY = true
                } else {
                    uY[0] = (1 - ALPHA_FORWARD) * uY[0] + ALPHA_FORWARD * targetUY_X
                    uY[1] = (1 - ALPHA_FORWARD) * uY[1] + ALPHA_FORWARD * targetUY_Y
                    uY[2] = (1 - ALPHA_FORWARD) * uY[2] + ALPHA_FORWARD * targetUY_Z
                }
                
                // Re-normalize uY
                val uYMag = sqrt(uY[0]*uY[0] + uY[1]*uY[1] + uY[2]*uY[2])
                if (uYMag > 0.1f) {
                    uY[0] /= uYMag; uY[1] /= uYMag; uY[2] /= uYMag
                }
            }
        }

        // 5. Orthogonal Lateral Vector (uX = uY x uZ)
        uX[0] = uY[1] * uZ[2] - uY[2] * uZ[1]
        uX[1] = uY[2] * uZ[0] - uY[0] * uZ[2]
        uX[2] = uY[0] * uZ[1] - uY[1] * uZ[0]
        val uXMag = sqrt(uX[0]*uX[0] + uX[1]*uX[1] + uX[2]*uX[2])
        if (uXMag > 0.1f) {
            uX[0] /= uXMag; uX[1] /= uXMag; uX[2] /= uXMag
        }

        // 6. Calculate Vehicle-Relative Accelerometer forces
        val gLongAccel = (linearAccel[0] * uY[0] + linearAccel[1] * uY[1] + linearAccel[2] * uY[2]) / SensorManager.GRAVITY_EARTH
        val gLatAccel = (linearAccel[0] * uX[0] + linearAccel[1] * uX[1] + linearAccel[2] * uX[2]) / SensorManager.GRAVITY_EARTH

        // 6.5. Calculate GPS-based longitudinal acceleration (fused with accelerometer)
        // GPS provides a low-frequency but very accurate absolute acceleration reference
        val currentTimeMs = System.currentTimeMillis()
        val gpsAccelMps2 = if (lastUpdateTimeMs > 0) {
            val timeDeltaS = (currentTimeMs - lastUpdateTimeMs) / 1000f
            if (timeDeltaS > 0.05f && timeDeltaS < 2f) { // Valid time window (50ms to 2s)
                val speedDeltaMps = speedMps - lastSpeedMps
                speedDeltaMps / timeDeltaS  // m/s^2
            } else {
                lastGpsAccelMps2
            }
        } else {
            0f
        }
        
        // Smooth GPS acceleration with stronger filter (more stable source)
        lastGpsAccelMps2 = (1 - ALPHA_GPS_ACCEL) * lastGpsAccelMps2 + ALPHA_GPS_ACCEL * gpsAccelMps2
        val gpsLongG = lastGpsAccelMps2 / SensorManager.GRAVITY_EARTH
        
        lastSpeedMps = speedMps
        lastUpdateTimeMs = currentTimeMs
        
        // Fuse accelerometer and GPS for longitudinal G
        // GPS is very accurate but low frequency; accelerometer is noisy but high frequency
        // Use complementary fusion: 70% accelerometer for responsiveness, 30% GPS for accuracy
        val fusedLongAccelG = 0.7f * gLongAccel + 0.3f * gpsLongG

        // 7. Calculate Kinematic Telemetry (Yaw Rate + Centripetal Acceleration)
        // Project gyroscope vector onto vertical Z-axis to get true world-vertical yaw rate
        val yawRate = gyroValues?.let {
            it[0] * uZ[0] + it[1] * uZ[1] + it[2] * uZ[2]
        } ?: 0f

        val kinematicLatG = (speedMps * yawRate) / SensorManager.GRAVITY_EARTH

        // 8. Fusion Logic
        val targetLatG: Float
        val targetLeanDeg: Float

        if (isTwoWheeler) {
            // For motorcycles, lateral accelerometer force is physically 0 in coordinated turns.
            // We use the kinematic centripetal force as the true lateral G force.
            targetLatG = kinematicLatG
            targetLeanDeg = Math.toDegrees(atan2(kinematicLatG.toDouble(), 1.0)).toFloat()
        } else {
            // For cars, body roll is low, so we can use the physical lateral accelerometer force
            // combined with kinematic turning G to eliminate engine vibration.
            targetLatG = 0.7f * gLatAccel + 0.3f * kinematicLatG
            targetLeanDeg = 0f // Cars do not lean significantly
        }

        // Apply professional-grade smoothing to reduce vibration noise while preserving dynamics
        smoothLongG = (1 - ALPHA_G_FORCE) * smoothLongG + ALPHA_G_FORCE * fusedLongAccelG
        smoothLatG = (1 - ALPHA_G_FORCE) * smoothLatG + ALPHA_G_FORCE * targetLatG
        smoothLeanDeg = (1 - ALPHA_G_FORCE) * smoothLeanDeg + ALPHA_G_FORCE * targetLeanDeg

        return TelemetryData(
            longitudinalG = smoothLongG,
            lateralG = smoothLatG,
            leanAngle = smoothLeanDeg,
            totalG = sqrt(smoothLongG * smoothLongG + smoothLatG * smoothLatG)
        )
    }

    data class TelemetryData(
        val longitudinalG: Float,
        val lateralG: Float,
        val leanAngle: Float,
        val totalG: Float
    )
}
