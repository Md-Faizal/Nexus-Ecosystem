package com.nexus.portal.services

import android.location.Location
import com.nexus.portal.data.local.ridetracker.PerformanceRunEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

data class TelemetryPoint(
    val elapsedMs: Long,
    val speedKmh: Float,
    val lat: Double,
    val lng: Double,
    val altitude: Double,
    val slopePercent: Float,
    val lateralG: Float,
    val longitudinalG: Float
)

class PerformanceTelemetryTracker {

    // Test types
    enum class TestMode {
        ACCELERATION, DRAG_DISTANCE, BRAKING, G_FORCE, LAP_TIMER
    }

    // Live state tracking
    data class LiveState(
        val activeMode: TestMode = TestMode.ACCELERATION,
        val currentSpeedKmh: Float = 0f,
        val isRecording: Boolean = false,
        val isReady: Boolean = true,
        val elapsedMs: Long = 0L,
        
        // Acceleration live stats
        val live0To60Time: Float? = null,
        val live0To100KmhTime: Float? = null,
        val live60To130MphTime: Float? = null,
        val live100To200KmhTime: Float? = null,
        val live0To130MphTime: Float? = null,
        
        // Drag live stats
        val dragDistanceMeters: Float = 0f,
        val live1_8Time: Float? = null,
        val live1_8Speed: Float? = null,
        val live1_4Time: Float? = null,
        val live1_4Speed: Float? = null,
        val live1_2Time: Float? = null,
        val live1_2Speed: Float? = null,
        
        // Braking live stats
        val brakingStartSpeedKmh: Float = 0f,
        val brakingTime: Float? = null,
        val brakingDistance: Float? = null,
        
        // G-Forces
        val currentLatG: Float = 0f,
        val currentLongG: Float = 0f,
        val peakLatG: Float = 0f,
        val peakLongG: Float = 0f,
        
        // Lap Timer
        val activeLapStartTimeMs: Long = 0L,
        val lapsList: List<Long> = emptyList(),
        val currentLapDurationMs: Long = 0L,
        val isLapGateSet: Boolean = false,
        val lapGateLat: Double? = null,
        val lapGateLng: Double? = null
    )

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    // Internal recording buffers
    private var recordStartTimeMs = 0L
    private val pointsList = mutableListOf<TelemetryPoint>()
    
    // Test detection triggers
    private var startSpeedTimeMs = -1L
    private var start60MphTimeMs = -1L
    private var start100KmhTimeMs = -1L
    
    // Drag distance metrics
    private var dragStartLat = 0.0
    private var dragStartLng = 0.0
    private var dragAccumulatedDist = 0f
    
    // Braking metrics
    private var isBrakingActive = false
    private var brakingStartTimeMs = 0L
    private var brakingStartDistance = 0f
    private var maxBrakingSpeedCaptured = 0f
    
    // Lap Timer Gate Coordinate
    private var lapGateLat = 0.0
    private var lapGateLng = 0.0
    private var lastLapTriggerTimeMs = 0L
    private var lapStartTimeMs = 0L

    // Slope tracking helper
    private var lastLocation: Location? = null

    fun setMode(mode: TestMode) {
        resetTracking()
        _state.value = _state.value.copy(activeMode = mode)
    }

    fun setLapGate(lat: Double, lng: Double) {
        lapGateLat = lat
        lapGateLng = lng
        _state.value = _state.value.copy(isLapGateSet = true, lapGateLat = lat, lapGateLng = lng)
    }

    fun resetLapGate() {
        lapGateLat = 0.0
        lapGateLng = 0.0
        _state.value = _state.value.copy(isLapGateSet = false, lapGateLat = null, lapGateLng = null)
    }

    fun startRecording() {
        resetTracking()
        recordStartTimeMs = System.currentTimeMillis()
        pointsList.clear()
        
        if (_state.value.activeMode == TestMode.LAP_TIMER && _state.value.isLapGateSet) {
            lapStartTimeMs = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isRecording = true,
                isReady = false,
                activeLapStartTimeMs = lapStartTimeMs
            )
        } else {
            _state.value = _state.value.copy(
                isRecording = true,
                isReady = true,
                elapsedMs = 0L
            )
        }
    }

    fun stopRecording(vehicleId: String?, vehicleName: String, vehicleType: String): PerformanceRunEntity? {
        if (!_state.value.isRecording) return null
        
        val durationMs = System.currentTimeMillis() - recordStartTimeMs
        val maxSpeed = pointsList.maxOfOrNull { it.speedKmh } ?: 0f
        
        if (pointsList.size < 5 || durationMs < 3000L || maxSpeed < 5f) {
            resetTracking()
            return null
        }
        
        val snap = _state.value
        _state.value = snap.copy(isRecording = false, isReady = true)
        
        // Finalize Lap if in Lap Timer mode
        val finalLaps = if (snap.activeMode == TestMode.LAP_TIMER && snap.lapsList.isNotEmpty()) {
            snap.lapsList
        } else if (snap.activeMode == TestMode.LAP_TIMER && snap.currentLapDurationMs > 5000L) {
            snap.lapsList + snap.currentLapDurationMs
        } else {
            snap.lapsList
        }

        // Convert points to JSON
        val ptsArray = JSONArray()
        pointsList.forEach { pt ->
            ptsArray.put(JSONObject().apply {
                put("elapsedMs", pt.elapsedMs)
                put("speedKmh", pt.speedKmh)
                put("lat", pt.lat)
                put("lng", pt.lng)
                put("altitude", pt.altitude)
                put("slopePercent", pt.slopePercent)
                put("lateralG", pt.lateralG)
                put("longitudinalG", pt.longitudinalG)
            })
        }
        
        val lapsArray = JSONArray()
        finalLaps.forEach { lapsArray.put(it) }

        return PerformanceRunEntity(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            vehicleName = vehicleName,
            vehicleType = vehicleType,
            timestamp = System.currentTimeMillis(),
            accel0To60Mph = snap.live0To60Time,
            accel0To100Kmh = snap.live0To100KmhTime,
            accel60To130Mph = snap.live60To130MphTime,
            accel100To200Kmh = snap.live100To200KmhTime,
            accel0To130Mph = snap.live0To130MphTime,
            drag1_8Time = snap.live1_8Time,
            drag1_8TrapSpeedKmh = snap.live1_8Speed,
            drag1_4Time = snap.live1_4Time,
            drag1_4TrapSpeedKmh = snap.live1_4Speed,
            drag1_2Time = snap.live1_2Time,
            drag1_2TrapSpeedKmh = snap.live1_2Speed,
            brakingStartSpeedKmh = snap.brakingStartSpeedKmh,
            brakingTime = snap.brakingTime,
            brakingDistanceMeters = snap.brakingDistance,
            peakLateralG = snap.peakLatG,
            peakLongitudinalG = snap.peakLongG,
            lapTimesJson = lapsArray.toString(),
            telemetryJson = ptsArray.toString()
        )
    }

    fun resetTracking() {
        startSpeedTimeMs = -1L
        start60MphTimeMs = -1L
        start100KmhTimeMs = -1L
        dragAccumulatedDist = 0f
        isBrakingActive = false
        brakingStartTimeMs = 0L
        brakingStartDistance = 0f
        maxBrakingSpeedCaptured = 0f
        lastUpdateTimeMs = 0L
        lapStartTimeMs = 0L
        lastLapTriggerTimeMs = 0L
        lastLocation = null
        
        _state.value = LiveState(
            activeMode = _state.value.activeMode,
            isLapGateSet = _state.value.isLapGateSet,
            lapGateLat = _state.value.lapGateLat,
            lapGateLng = _state.value.lapGateLng
        )
    }

    private var lastUpdateTimeMs = 0L

    fun processTelemetry(
        location: Location,
        latG: Float,
        longG: Float
    ) {
        val snap = _state.value
        if (!snap.isRecording) return

        val nowMs = System.currentTimeMillis()
        val elapsedMs = nowMs - recordStartTimeMs

        val speedMs = location.speed
        val speedKmh = speedMs * 3.6f
        
        // Compute Road Slope (Elevation Tracking)
        var slopePercent = 0f
        lastLocation?.let { prev ->
            val distance = prev.distanceTo(location)
            if (distance > 2f) {
                val elevationDelta = location.altitude - prev.altitude
                slopePercent = (elevationDelta / distance).toFloat() * 100f
                // Clamp slope to reasonable road angles (-30% to +30%)
                slopePercent = slopePercent.coerceIn(-30f, 30f)
            } else {
                slopePercent = prev.bearing // Reuse previous slope if distance is tiny to smooth jitter
            }
        }
        if (lastLocation != null && lastLocation!!.distanceTo(location) <= 2f) {
            // Keep previous slope
            slopePercent = if (pointsList.isNotEmpty()) pointsList.last().slopePercent else 0f
        }
        
        lastLocation = location

        // Update Peak G-Forces
        val nextPeakLatG = maxOf(snap.peakLatG, abs(latG))
        val nextPeakLongG = maxOf(snap.peakLongG, abs(longG))

        // Save telemetry point
        val telemetryPoint = TelemetryPoint(
            elapsedMs = elapsedMs,
            speedKmh = speedKmh,
            lat = location.latitude,
            lng = location.longitude,
            altitude = location.altitude,
            slopePercent = slopePercent,
            lateralG = latG,
            longitudinalG = longG
        )
        pointsList.add(telemetryPoint)

        // Local dynamic updates depending on test mode
        var update = snap.copy(
            currentSpeedKmh = speedKmh,
            elapsedMs = elapsedMs,
            currentLatG = latG,
            currentLongG = longG,
            peakLatG = nextPeakLatG,
            peakLongG = nextPeakLongG
        )

        // ──────── ACCELERATION TESTS ────────
        // Start of acceleration threshold
        if (speedKmh > 0.5f && startSpeedTimeMs == -1L) {
            startSpeedTimeMs = nowMs
            update = update.copy(isReady = false)
        }

        if (startSpeedTimeMs != -1L) {
            val duration = (nowMs - startSpeedTimeMs) / 1000f

            // 0-60 mph (96.56 km/h)
            var next0To60 = snap.live0To60Time
            if (speedKmh >= 96.56f && snap.live0To60Time == null) {
                next0To60 = duration
            }
            
            // 0-100 km/h
            var next0To100 = snap.live0To100KmhTime
            if (speedKmh >= 100f && snap.live0To100KmhTime == null) {
                next0To100 = duration
            }

            // 60-130 mph (96.56 km/h to 209.2 km/h)
            var next60To130 = snap.live60To130MphTime
            if (speedKmh >= 96.56f && start60MphTimeMs == -1L) {
                start60MphTimeMs = nowMs
            }
            if (speedKmh >= 209.2f && start60MphTimeMs != -1L && snap.live60To130MphTime == null) {
                next60To130 = (nowMs - start60MphTimeMs) / 1000f
            }

            // 100-200 km/h (100 km/h to 200 km/h)
            var next100To200 = snap.live100To200KmhTime
            if (speedKmh >= 100f && start100KmhTimeMs == -1L) {
                start100KmhTimeMs = nowMs
            }
            if (speedKmh >= 200f && start100KmhTimeMs != -1L && snap.live100To200KmhTime == null) {
                next100To200 = (nowMs - start100KmhTimeMs) / 1000f
            }

            // 0-130 mph (209.2 km/h)
            var next0To130 = snap.live0To130MphTime
            if (speedKmh >= 209.2f && snap.live0To130MphTime == null) {
                next0To130 = duration
            }

            update = update.copy(
                live0To60Time = next0To60,
                live0To100KmhTime = next0To100,
                live60To130MphTime = next60To130,
                live100To200KmhTime = next100To200,
                live0To130MphTime = next0To130
            )
        }

        // ──────── DRAG RACING TESTS ────────
        if (speedKmh > 0.5f && dragAccumulatedDist == 0f) {
            dragStartLat = location.latitude
            dragStartLng = location.longitude
            update = update.copy(isReady = false)
        }

        if (startSpeedTimeMs != -1L) {
            val duration = (nowMs - startSpeedTimeMs) / 1000f
            
            // Calculate accumulated distance from start point
            val startLoc = Location("").apply {
                latitude = dragStartLat
                longitude = dragStartLng
            }
            dragAccumulatedDist = startLoc.distanceTo(location)

            // 1/8 Mile = 201.17 Meters
            var t1_8 = snap.live1_8Time
            var s1_8 = snap.live1_8Speed
            if (dragAccumulatedDist >= 201.17f && snap.live1_8Time == null) {
                t1_8 = duration
                s1_8 = speedKmh
            }

            // 1/4 Mile = 402.34 Meters
            var t1_4 = snap.live1_4Time
            var s1_4 = snap.live1_4Speed
            if (dragAccumulatedDist >= 402.34f && snap.live1_4Time == null) {
                t1_4 = duration
                s1_4 = speedKmh
            }

            // 1/2 Mile = 804.67 Meters
            var t1_2 = snap.live1_2Time
            var s1_2 = snap.live1_2Speed
            if (dragAccumulatedDist >= 804.67f && snap.live1_2Time == null) {
                t1_2 = duration
                s1_2 = speedKmh
            }

            update = update.copy(
                dragDistanceMeters = dragAccumulatedDist,
                live1_8Time = t1_8,
                live1_8Speed = s1_8,
                live1_4Time = t1_4,
                live1_4Speed = s1_4,
                live1_2Time = t1_2,
                live1_2Speed = s1_2
            )
        }

        // ──────── BRAKING TESTS ────────
        // Measure from any speed upwards of 60kph, and round to nearest 10kph
        if (speedKmh >= 60f && !isBrakingActive && longG < -0.15f) {
            isBrakingActive = true
            val roundedSpeed = kotlin.math.round(speedKmh / 10f) * 10f
            maxBrakingSpeedCaptured = roundedSpeed
            brakingStartTimeMs = nowMs
            brakingStartDistance = 0f
            update = update.copy(
                brakingStartSpeedKmh = roundedSpeed,
                isReady = false
            )
        }

        if (isBrakingActive) {
            val timeSec = (nowMs - brakingStartTimeMs) / 1000f
            
            // Track distance since start of braking
            if (pointsList.size > 1) {
                val lastPt = pointsList[pointsList.size - 2]
                val lastL = Location("").apply { latitude = lastPt.lat; longitude = lastPt.lng }
                brakingStartDistance += lastL.distanceTo(location)
            }

            if (speedKmh <= 1.0f) {
                // Decelerated to full stop
                isBrakingActive = false
                update = update.copy(
                    brakingTime = timeSec,
                    brakingDistance = brakingStartDistance
                )
            } else {
                // Update live feedback
                update = update.copy(
                    brakingTime = timeSec,
                    brakingDistance = brakingStartDistance
                )
            }
        }

        // ──────── LAP TIMER ────────
        if (snap.isLapGateSet) {
            val gateLocation = Location("").apply {
                latitude = lapGateLat
                longitude = lapGateLng
            }
            
            val distanceToGate = location.distanceTo(gateLocation)
            val currentLapDuration = nowMs - lapStartTimeMs
            
            // Trigger lap split when within 15 meters of gate,
            // speed is reasonable, and at least 10s has passed since starting the lap
            if (distanceToGate <= 15f && speedKmh > 10f && currentLapDuration > 10000L && (nowMs - lastLapTriggerTimeMs) > 10000L) {
                lastLapTriggerTimeMs = nowMs
                lapStartTimeMs = nowMs
                val updatedLaps = snap.lapsList + currentLapDuration
                update = update.copy(
                    lapsList = updatedLaps,
                    activeLapStartTimeMs = lapStartTimeMs,
                    currentLapDurationMs = 0L
                )
            } else {
                update = update.copy(
                    currentLapDurationMs = currentLapDuration
                )
            }
        }

        _state.value = update
    }
}
