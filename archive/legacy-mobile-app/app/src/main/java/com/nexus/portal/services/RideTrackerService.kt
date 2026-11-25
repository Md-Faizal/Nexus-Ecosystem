package com.nexus.portal.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.nexus.portal.MainActivity
import com.nexus.portal.R
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import com.nexus.portal.data.local.ridetracker.RideSession
import com.nexus.portal.util.BluetoothMacUtils
import com.nexus.portal.util.resolveVehicleForConnectedMacs as resolveVehicleConnection
import com.nexus.portal.util.VehicleConnectionResolution
import com.nexus.portal.util.collectLiveGarageMacs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.nexus.portal.data.repository.LocationRepository
import com.nexus.portal.data.repository.SensorData
import com.nexus.portal.data.repository.SensorRepository
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class RideTrackerService : Service() {

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var sensorRepository: SensorRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationJob: kotlinx.coroutines.Job? = null
    private lateinit var fusedLocationClientLocal: com.google.android.gms.location.FusedLocationProviderClient
    private var localLocationCallback: com.google.android.gms.location.LocationCallback? = null

    private val pointBuffer = java.util.concurrent.ConcurrentLinkedQueue<RideLocationPoint>()
    private var lastMovementTimeMs = 0L
    private var consecutiveSpeedTicks = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentIntervalMs = 1000L
    private var currentPriority = Priority.PRIORITY_HIGH_ACCURACY

    private fun requestLocationUpdates(intervalMs: Long = 1000L, priority: Int = Priority.PRIORITY_HIGH_ACCURACY) {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
            currentIntervalMs = intervalMs
            currentPriority = priority

            // Use a dedicated local FusedLocation client for high-frequency active tracking
            if (intervalMs <= 2000L) {
                try {
                    localLocationCallback?.let { fusedLocationClientLocal.removeLocationUpdates(it) }
                } catch (_: Exception) { }

                val cb = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        val loc = result.lastLocation ?: return
                        processLocationUpdate(loc)
                    }
                }
                localLocationCallback = cb

                val request = com.google.android.gms.location.LocationRequest.Builder(priority, intervalMs)
                    .setMinUpdateDistanceMeters(0f)
                    .build()

                fusedLocationClientLocal.requestLocationUpdates(request, cb, Looper.getMainLooper())
                Log.d("RideTrackerService", "Location updates requested via Local FusedClient: interval=$intervalMs ms")
            } else {
                // Lower-frequency / passive tracking via central repository
                try {
                    localLocationCallback?.let {
                        fusedLocationClientLocal.removeLocationUpdates(it)
                        localLocationCallback = null
                    }
                } catch (_: Exception) { }

                locationRepository.startTrackingClient("ride_tracker", intervalMs, priority, 5f)
                Log.d("RideTrackerService", "Location updates requested via Repository: interval=$intervalMs ms, distance=5f")
            }
        } catch (e: Exception) {
            Log.e("RideTrackerService", "Failed to request location updates", e)
        }
    }

    private fun stopLocalLocationUpdates() {
        try {
            localLocationCallback?.let {
                fusedLocationClientLocal.removeLocationUpdates(it)
                localLocationCallback = null
            }
        } catch (e: Exception) {
            Log.w("RideTrackerService", "stopLocalLocationUpdates failed", e)
        }
    }

    private fun updatePauseState(isPaused: Boolean) {
        val currentState = _trackingState.value
        if (currentState.isPaused == isPaused) return
        _trackingState.update { it.copy(isPaused = isPaused) }
        if (isPaused) {
            requestLocationUpdates(10000L, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        } else {
            requestLocationUpdates(1000L, Priority.PRIORITY_HIGH_ACCURACY)
        }
    }

    private fun flushLocationBuffer() {
        if (dbSessionId.isEmpty()) {
            Log.d("RideTrackerService", "flushLocationBuffer: dbSessionId is empty, skipping")
            return
        }
        val pointsToInsert = mutableListOf<RideLocationPoint>()
        var p = pointBuffer.poll()
        while (p != null) {
            pointsToInsert.add(p)
            p = pointBuffer.poll()
        }
        if (pointsToInsert.isEmpty()) {
            Log.d("RideTrackerService", "flushLocationBuffer: no points to insert")
            return
        }
        
        Log.d("RideTrackerService", "flushLocationBuffer: inserting ${pointsToInsert.size} points for session $dbSessionId")
        serviceScope.launch {
            try {
                val db = RideDatabase.getDatabase(this@RideTrackerService)
                db.rideDao().insertLocationPoints(pointsToInsert)
                Log.d("RideTrackerService", "flushLocationBuffer: successfully inserted ${pointsToInsert.size} points")
            } catch (e: Exception) {
                Log.e("RideTrackerService", "Error inserting location points batch", e)
                // Re-queue the points if insertion failed
                pointsToInsert.forEach { pointBuffer.add(it) }
            }
        }
    }

    private suspend fun flushLocationBufferSync() {
        if (dbSessionId.isEmpty()) return
        val pointsToInsert = mutableListOf<RideLocationPoint>()
        var p = pointBuffer.poll()
        while (p != null) {
            pointsToInsert.add(p)
            p = pointBuffer.poll()
        }
        if (pointsToInsert.isNotEmpty()) {
            try {
                val db = RideDatabase.getDatabase(this@RideTrackerService)
                db.rideDao().insertLocationPoints(pointsToInsert)
            } catch (e: Exception) {
                Log.e("RideTrackerService", "Error inserting location points batch sync", e)
            }
        }
    }


    // Raw sensor buffers
    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val gyroValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false
    private var hasGyro = false

    private val telemetryEngine = VehicleTelemetryEngine()
    private var selectedVehicleMode = "AUTO"

    // Low-pass filter alpha
    private val ALPHA = 0.8f

    private var dbSessionId: String = ""
    private var startTime: Long = 0L
    private var isStoppingRide = false
    private var durationJob: kotlinx.coroutines.Job? = null
    private var sensorJob: kotlinx.coroutines.Job? = null

    private var lastAzimuth = -1000f

    // Variance tracking for smart idle detection (vibration analysis)
    private var meanAccelMag = 0f
    private var meanSquaredAccelMag = 0f
    @Volatile private var accelVariance = 0f

    // Google Activity Recognition Fused State
    private var currentDetectedActivity = "UNKNOWN"
    private var activityPendingIntent: PendingIntent? = null
    private var activityRecognitionClient: ActivityRecognitionClient? = null

    private val activityReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent) ?: return
                val mostProbableActivity = result.mostProbableActivity
                val type = mostProbableActivity.type
                val confidence = mostProbableActivity.confidence
                
                if (confidence >= 75) {
                    val activityName = when (type) {
                        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
                        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
                        DetectedActivity.STILL -> "STILL"
                        DetectedActivity.WALKING -> "WALKING"
                        DetectedActivity.RUNNING -> "RUNNING"
                        else -> "UNKNOWN"
                    }
                    Log.d("RideTrackerService", "Detected Activity: $activityName ($confidence%)")
                    currentDetectedActivity = activityName
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_PAUSE = "ACTION_PAUSE_TRACKING"
        const val ACTION_RESUME = "ACTION_RESUME_TRACKING"
        const val ACTION_DEVICE_CONNECTED = "ACTION_DEVICE_CONNECTED"
        const val ACTION_SYNC_CONNECTIONS = "ACTION_SYNC_CONNECTIONS"
        const val ACTION_DEVICE_DISCONNECTED = "ACTION_DEVICE_DISCONNECTED"
        const val ACTION_CANCEL_AUTO_DETECT = "ACTION_CANCEL_AUTO_DETECT"
        const val ACTION_UPDATE_VEHICLE_MODE = "ACTION_UPDATE_VEHICLE_MODE"
        const val ACTION_DISMISS_DISCONNECT_WARNING = "ACTION_DISMISS_DISCONNECT_WARNING"
        const val EXTRA_MAC_ADDRESS = "EXTRA_MAC_ADDRESS"
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
        private const val NOTIFICATION_CHANNEL_ID = "ride_tracker_channel"
        private const val NOTIFICATION_ID = 1003
        private const val NOTIFICATION_COMPLETE_ID = 1004

        /**
         * leanAngle: degrees, positive = right lean, negative = left lean (orientation-independent)
         * accelLateral: lateral G-force in world frame (+right / -left)
         * accelLongitudinal: longitudinal G-force in world frame (+accel / -brake)
         * gForce: total G magnitude
         */
        data class TrackingState(
            val isTracking: Boolean = false,
            val isPaused: Boolean = false,
            val currentSpeedKmh: Float = 0f,
            val totalDistanceMeters: Float = 0f,
            val durationSeconds: Long = 0L,
            val gForce: Float = 0f,
            val accelLateral: Float = 0f,       // world frame left/right (+right)
            val accelLongitudinal: Float = 0f,  // world frame front/back (+accel)
            val leanAngle: Float = 0f,          // degrees, orientation-independent
            val compassHeading: Float = 0f,     // degrees from North
            val currentLat: Double = 0.0,
            val currentLng: Double = 0.0,
            val points: List<RideLocationPoint> = emptyList(),
            val speedHistory: List<Float> = emptyList(),
            
            // Bluetooth Automation
            val isPreRecording: Boolean = false,
            val connectedMacAddress: String? = null,
            val connectedDeviceName: String? = null,
            val connectedVehicleId: String? = null,
            val disconnectTimeMs: Long? = null,
            val connectedDevices: List<Pair<String, String>> = emptyList(), // all currently connected saved devices
            val autoStopRemainingSeconds: Int? = null, // countdown remaining seconds for auto-stop
            val isManualOverride: Boolean = false, // flag indicating user dismissed disconnect warning
            val connectionLabel: String? = null // e.g. "Dash Only", "Dash + Helmet", "Helmet Only"
        )

        private val _trackingState = MutableStateFlow(TrackingState())
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

        // Also expose live location even when not tracking (for map centering on open)
        private val _liveLocation = MutableStateFlow<Location?>(null)
        val liveLocation: StateFlow<Location?> = _liveLocation.asStateFlow()
        
        // Bug #8 fix: buffer=8 prevents overlay events being silently dropped at fast state transitions
        val autoPauseEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
        var isAppInForeground = false
        val cancelledAutoDetectMacs = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val activelyConnectedMacs = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val connectionMutex = kotlinx.coroutines.sync.Mutex()

        fun addCancelledMac(context: Context, mac: String) {
            cancelledAutoDetectMacs.add(BluetoothMacUtils.normalize(mac))
            val prefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("cancelled_macs", cancelledAutoDetectMacs).apply()
        }

        fun removeCancelledMac(context: Context, mac: String) {
            val normalized = BluetoothMacUtils.normalize(mac)
            cancelledAutoDetectMacs.removeIf { BluetoothMacUtils.normalize(it) == normalized }
            val prefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("cancelled_macs", cancelledAutoDetectMacs).apply()
        }

        fun loadCancelledMacs(context: Context) {
            val prefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getStringSet("cancelled_macs", emptySet()) ?: emptySet()
            cancelledAutoDetectMacs.clear()
            cancelledAutoDetectMacs.addAll(saved.map { BluetoothMacUtils.normalize(it) })
        }



        fun addActiveMac(context: Context, mac: String) {
            val normalized = BluetoothMacUtils.normalize(mac)
            if (normalized.isEmpty()) return
            activelyConnectedMacs.add(normalized)
            persistConnectedMacs(context)
        }

        fun removeActiveMac(context: Context, mac: String) {
            val target = BluetoothMacUtils.normalize(mac)
            activelyConnectedMacs.removeIf { BluetoothMacUtils.normalize(it) == target }
            persistConnectedMacs(context)
        }

        fun loadPersistedConnectedMacs(context: Context) {
            val prefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getStringSet("active_connected_macs", emptySet()) ?: emptySet()
            activelyConnectedMacs.clear()
            activelyConnectedMacs.addAll(saved.map { BluetoothMacUtils.normalize(it) }.filter { it.isNotEmpty() })
        }

        fun persistConnectedMacs(context: Context) {
            val prefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet("active_connected_macs", activelyConnectedMacs.map { BluetoothMacUtils.normalize(it) }.toSet())
                .apply()
        }

        fun checkAndStartAutoDetectIfConnected(context: Context) {
            loadCancelledMacs(context)
            // Clear any previously persisted ghost connections from the stale caching system
            val prefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("active_connected_macs").apply()
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.Manifest.permission.BLUETOOTH_CONNECT
            } else {
                android.Manifest.permission.BLUETOOTH
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("RideTrackerService", "checkAndStartAutoDetect aborted: missing permission $permission")
                return
            }
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                try {
                    BluetoothMacUtils.refreshKnownMacsCache(context)
                    val db = com.nexus.portal.data.local.ridetracker.RideDatabase.getDatabase(context)
                    val vehicles = db.vehicleDao().getAllVehiclesSync()
                    val converter = com.nexus.portal.data.local.ridetracker.VehicleConverters()

                    val savedMacs = vehicles.flatMap { v ->
                        converter.toConnectedDevicesList(v.connectedDevicesJson)
                            .mapNotNull { d ->
                                BluetoothMacUtils.normalize(d.macAddress).takeIf { it.isNotEmpty() }
                            }
                    }.toSet()

                    val probed = BluetoothMacUtils.probeConnectedSavedMacs(context, savedMacs)
                    probed.forEach { addActiveMac(context, it) }

                    val eligibleMacs = activelyConnectedMacs.filter { mac ->
                        mac.isNotEmpty() &&
                            !cancelledAutoDetectMacs.contains(mac) &&
                            BluetoothMacUtils.isKnownVehicleMac(context, mac)
                    }
                    if (eligibleMacs.isEmpty()) return@launch

                    val serviceIntent = Intent(context, RideTrackerService::class.java).apply {
                        action = ACTION_SYNC_CONNECTIONS
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("RideTrackerService", "checkAndStartAutoDetect failed", e)
                }
            }
        }
    }

    private suspend fun resolveVehicleForConnectedMacs(
        macs: Set<String>,
        context: Context = this@RideTrackerService
    ): VehicleConnectionResolution? = resolveVehicleConnection(macs, context)

    private fun processLocationUpdate(location: Location) {
        // Always update live location
        _liveLocation.value = location

        val snap = _trackingState.value
        
        // Handle pre-recording buffer when not actively tracking but device is connected
        if (snap.isPreRecording && !snap.isTracking) {
            updateNotification()

            val speedMs = location.speed
            val speedKmh = speedMs * 3.6f
            
            var pointToBuffer: RideLocationPoint? = null
            
            _trackingState.update { currentState ->
                val newPoints = currentState.points.toMutableList()
                val newPoint = RideLocationPoint(
                    sessionId = "", // Will be assigned on commit
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    speedMetersPerSec = speedMs,
                    leanAngle = currentState.leanAngle,
                    gForce = currentState.gForce,
                    accelLateral = currentState.accelLateral,
                    accelLongitudinal = currentState.accelLongitudinal,
                    timestamp = System.currentTimeMillis()
                )
                newPoints.add(newPoint)
                pointToBuffer = newPoint
                
                if (newPoints.size > 60) newPoints.removeAt(0)
                
                val newHistory = (currentState.speedHistory + speedKmh).takeLast(30)
                
                currentState.copy(
                    currentLat = location.latitude,
                    currentLng = location.longitude,
                    currentSpeedKmh = speedKmh,
                    points = newPoints,
                    speedHistory = newHistory
                )
            }
            
            // 10 minute timeout for pre-recording to prevent infinite battery drain
            if (System.currentTimeMillis() - lastMovementTimeMs > 10 * 60 * 1000L) {
                Log.d("RideTrackerService", "Pre-recording timeout reached. Cancelling auto detect.")
                cancelAutoDetect()
                return
            }
            
            // Auto-commit if speed exceeds 5km/h and we are pre-recording
            if (speedKmh > 5f) {
                consecutiveSpeedTicks++
                if (consecutiveSpeedTicks == 3) { // Use == 3 instead of >= 3 to prevent debouncing loop trigger
                    commitPreRecordingToActiveRide()
                }
            } else {
                consecutiveSpeedTicks = 0
            }
            return
        }

        if (!snap.isTracking) return

        val speedMs = location.speed
        val speedKmh = speedMs * 3.6f

        if (snap.isPaused) {
            _trackingState.update { currentState ->
                val newHistory = (currentState.speedHistory + speedKmh).takeLast(30)
                currentState.copy(
                    currentSpeedKmh = speedKmh,
                    currentLat = location.latitude,
                    currentLng = location.longitude,
                    speedHistory = newHistory
                )
            }
            updateNotification(speedKmh, _trackingState.value.totalDistanceMeters, _trackingState.value.durationSeconds)
            return // Stop processing location points and distance if paused!
        }

        var finalDistance = 0f
        var pointToBuffer: RideLocationPoint? = null

        _trackingState.update { currentState ->
            var newDistance = currentState.totalDistanceMeters
            val newPoints = currentState.points.toMutableList()

            if (location.accuracy < 20f) {
                if (newPoints.isNotEmpty()) {
                    val lastLoc = newPoints.last()
                    val lastLocationObj = Location("").apply {
                        latitude = lastLoc.latitude
                        longitude = lastLoc.longitude
                    }
                    val distGained = lastLocationObj.distanceTo(location)
                    if (distGained < 200) { // Reject noisy GPS jumps > 200m
                        newDistance += distGained
                    }
                }

                val newPoint = RideLocationPoint(
                    sessionId = dbSessionId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    speedMetersPerSec = speedMs,
                    leanAngle = currentState.leanAngle,
                    gForce = currentState.gForce,
                    accelLateral = currentState.accelLateral,
                    accelLongitudinal = currentState.accelLongitudinal,
                    timestamp = System.currentTimeMillis()
                )
                newPoints.add(newPoint)
                pointToBuffer = newPoint
                
                Log.d("RideTrackerService", "processLocationUpdate: added point for session $dbSessionId, accuracy=${location.accuracy}, speed=${speedKmh}km/h")
            }
            
            // Maintain rolling speed history (last 30 samples)
            val newHistory = (currentState.speedHistory + speedKmh).takeLast(30)

            finalDistance = newDistance

            currentState.copy(
                currentSpeedKmh = speedKmh,
                totalDistanceMeters = newDistance,
                currentLat = location.latitude,
                currentLng = location.longitude,
                points = newPoints,
                speedHistory = newHistory
            )
        }

        if (dbSessionId.isNotEmpty() && pointToBuffer != null) {
            pointBuffer.add(pointToBuffer!!)
            Log.d("RideTrackerService", "processLocationUpdate: buffered point, buffer size=${pointBuffer.size}")
        }

        updateNotification(speedKmh, finalDistance, _trackingState.value.durationSeconds)
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexusPortal:RideTrackerWakeLock")

        loadCancelledMacs(this)
        fusedLocationClientLocal = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        loadPersistedConnectedMacs(this)

        // Start observing the location flow from the Repository
        locationJob = serviceScope.launch {
            locationRepository.locationFlow.collectLatest { location ->
                processLocationUpdate(location)
            }
        }

        // Bug #1/2 fix: sensorManager fields removed; sensor lifecycle now fully managed
        // by startSensorCollection()/stopSensorCollection() via sensorRepository.sensorFlow.
        createNotificationChannel()
        requestActivityUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        _trackingState.update { TrackingState() }
        removeActivityUpdates()
        stopSensorCollection() // cancels sensorJob; callbackFlow awaitClose unregisters hardware
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIX: ALWAYS satisfy the ForegroundService contract IMMEDIATELY
        // This prevents "ForegroundServiceDidNotStartInTimeException"
        ensureForegroundServiceStarted()

        intent?.getStringExtra("VEHICLE_MODE")?.let {
            selectedVehicleMode = it
        }
        when (intent?.action) {
            ACTION_START -> {
                val startVehicleId = intent?.getStringExtra("VEHICLE_ID")
                startTracking(autoSelectedVehicleId = startVehicleId, isAutoRecorded = false)
            }
            ACTION_STOP -> stopTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_DEVICE_CONNECTED -> {
                val mac = intent.getStringExtra(EXTRA_MAC_ADDRESS)?.let { BluetoothMacUtils.normalize(it) }
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME)

                // 1. ALWAYS satisfy the ForegroundService contract unconditionally to prevent crashes
                val currentState = _trackingState.value
                val wasRunning = currentState.isTracking || currentState.isPreRecording
                try {
                    val tempNotification = buildPreRecordingNotification(name ?: "Bluetooth Device", isDisconnected = false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseLocationForegroundType()) {
                        startForeground(NOTIFICATION_ID, tempNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(NOTIFICATION_ID, tempNotification)
                    }
                } catch (e: Exception) {
                    Log.e("RideTrackerService", "startForeground on connect failed", e)
                }

                // 2. Check if we actually care about this device
                val shouldTrack = mac != null && mac.isNotEmpty() && 
                                  !cancelledAutoDetectMacs.contains(mac) && 
                                  BluetoothMacUtils.isKnownVehicleMac(this, mac)

                if (shouldTrack) {
                    addActiveMac(this, mac!!)
                    updateConnectedDevicesList()
                    handleDeviceConnected(mac, name)
                } else if (!wasRunning) {
                    // 3. If we don't care, and weren't already tracking, stop immediately to kill the notification
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
            ACTION_SYNC_CONNECTIONS -> {
                val currentState = _trackingState.value
                val wasRunning = currentState.isTracking || currentState.isPreRecording
                
                try {
                    val tempNotification = buildPreRecordingNotification("Bluetooth", isDisconnected = false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseLocationForegroundType()) {
                        startForeground(NOTIFICATION_ID, tempNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(NOTIFICATION_ID, tempNotification)
                    }
                } catch (e: Exception) {
                    Log.e("RideTrackerService", "startForeground on sync failed", e)
                }
                
                handleSyncConnections()
            }
            ACTION_DEVICE_DISCONNECTED -> {
                val mac = intent.getStringExtra(EXTRA_MAC_ADDRESS)?.let { BluetoothMacUtils.normalize(it) }
                if (mac != null && mac.isNotEmpty()) {
                    removeCancelledMac(this, mac)
                    removeActiveMac(this, mac)
                    updateConnectedDevicesList()
                    serviceScope.launch {
                        if (BluetoothMacUtils.isKnownVehicleMac(this@RideTrackerService, mac)) {
                            connectionMutex.withLock {
                                handleDeviceDisconnectedLocked(mac)
                            }
                        }
                        // Give updateConnectedDevicesList a moment to complete its DB read before potentially killing the service
                        kotlinx.coroutines.delay(100)
                        checkAndStopServiceIfNotRunning()
                    }
                }
            }
            ACTION_CANCEL_AUTO_DETECT -> {
                val mac = intent?.getStringExtra(EXTRA_MAC_ADDRESS)
                cancelAutoDetect(mac)
            }
            ACTION_UPDATE_VEHICLE_MODE -> {
                intent?.getStringExtra("VEHICLE_MODE")?.let {
                    selectedVehicleMode = it
                }
            }
            ACTION_DISMISS_DISCONNECT_WARNING -> {
                _trackingState.update { currentState ->
                    currentState.copy(
                        disconnectTimeMs = null,
                        autoStopRemainingSeconds = null,
                        isManualOverride = true
                    )
                }
                val snap = _trackingState.value
                showAutoPauseOverlay("Switched to Manual Tracking Mode")
                updateConnectedDevicesList()
                updateNotification(snap.currentSpeedKmh, snap.totalDistanceMeters, snap.durationSeconds)
            }
        }
        return START_STICKY
    }

    private fun ensureForegroundServiceStarted() {
        // Defensive: call startForeground immediately to satisfy the contract
        // If already started, this updates the notification safely
        var notification: Notification? = null
        try {
            notification = if (_trackingState.value.isTracking) {
                val state = _trackingState.value
                buildNotification(state.currentSpeedKmh, state.totalDistanceMeters, state.durationSeconds)
            } else if (_trackingState.value.isPreRecording) {
                buildPreRecordingNotification(_trackingState.value.connectedDeviceName)
            } else {
                // Fallback for any other case when service starts
                buildPreRecordingNotification("Ride Tracker")
            }
        } catch (e: Exception) {
            Log.e("RideTrackerService", "Failed to build notification, using minimal fallback", e)
            // Create minimal fallback notification if building failed
            notification = try {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Ride Tracker")
                    .setContentText("Running...")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setOngoing(true)
                    .build()
            } catch (e2: Exception) {
                Log.e("RideTrackerService", "Even minimal notification failed to build", e2)
                null
            }
        }

        if (notification == null) {
            Log.e("RideTrackerService", "Cannot proceed: no notification available at all")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseLocationForegroundType()) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("RideTrackerService", "ensureForegroundServiceStarted: startForeground called successfully")
        } catch (e: Exception) {
            // If this fails despite our efforts, it may be a system issue
            Log.e("RideTrackerService", "startForeground failed in ensureForegroundServiceStarted", e)
            // Try fallback: startForeground without the service type
            try {
                startForeground(NOTIFICATION_ID, notification)
                Log.d("RideTrackerService", "Fallback startForeground (without type) succeeded")
            } catch (e2: Exception) {
                Log.e("RideTrackerService", "Fallback startForeground also failed", e2)
            }
        }
    }

    private fun cancelAutoDetect(mac: String? = null) {
        val state = _trackingState.value
        if (state.isPreRecording || (state.isTracking && state.durationSeconds < 10)) {
            val macToCancel = mac ?: state.connectedMacAddress
            macToCancel?.let { addCancelledMac(this, it) }
            
            if (state.isTracking) {
                stopTracking()
                showAutoPauseOverlay("Auto-Detect Cancelled")
            } else {
                // Bug #5 fix: stopSensorCollection() cancels the coroutine; callbackFlow
                // awaitClose then unregisters hardware sensors properly.
                _trackingState.update { TrackingState() }
                stopLocalLocationUpdates()
                locationRepository.stopTracking()
                stopSensorCollection()
                stopForeground(true)
                stopSelf()
                showAutoPauseOverlay("Auto-Detect Cancelled")
            }
        }
    }

    private fun commitPreRecordingToActiveRide() {
        val currentState = _trackingState.value
        if (!currentState.isPreRecording) return
        
        // Start tracking instantly using the vehicle we already resolved when BT connected
        startTracking(
            preRecordedPoints = currentState.points,
            autoSelectedVehicleId = currentState.connectedVehicleId,
            isAutoRecorded = true
        )
    }

    private fun handleDeviceConnected(mac: String, deviceName: String?) {
        serviceScope.launch {
            connectionMutex.withLock {
                if (isStoppingRide) return@launch
                handleDeviceConnectedLocked(BluetoothMacUtils.normalize(mac), deviceName)
            }
        }
    }

    private fun handleSyncConnections() {
        serviceScope.launch {
            connectionMutex.withLock {
                if (isStoppingRide) {
                    checkAndStopServiceIfNotRunning()
                    return@launch
                }
                // BT-Bug #3 fix: probe the FULL live set so multi-device vehicles
                // (dash + helmet) are scored correctly, not just the first eligible MAC.
                val liveMacs = collectLiveGarageMacs(this@RideTrackerService, activelyConnectedMacs)
                liveMacs.forEach { addActiveMac(this@RideTrackerService, it) }
                if (liveMacs.isEmpty()) {
                    checkAndStopServiceIfNotRunning()
                    return@launch
                }
                // Pick the highest-scored trigger MAC as the representative
                val triggerMac = liveMacs.firstOrNull { mac ->
                    !cancelledAutoDetectMacs.contains(mac) &&
                        BluetoothMacUtils.isKnownVehicleMac(this@RideTrackerService, mac)
                }
                if (triggerMac == null) {
                    checkAndStopServiceIfNotRunning()
                    return@launch
                }
                handleDeviceConnectedLocked(triggerMac, null)
            }
        }
    }

    private fun checkAndStopServiceIfNotRunning() {
        val state = _trackingState.value
        if (!state.isTracking && !state.isPreRecording) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private suspend fun handleDeviceConnectedLocked(mac: String, deviceName: String?) {
        val currentState = _trackingState.value

        // Resolve which vehicle best matches ALL currently-connected MACs
        val resolution = resolveVehicleForConnectedMacs(activelyConnectedMacs)

        // Scenario 1: Already tracking actively
        if (currentState.isTracking) {
            val wasManualOverride = currentState.isManualOverride
            if (resolution != null) {
                // Update the connectionLabel even mid-ride (e.g. helmet joined after dash)
                val mappedMode = when (resolution.vehicleType.lowercase()) {
                    "car" -> "CAR"
                    "bike", "scooter" -> "BIKE"
                    else -> "AUTO"
                }
                selectedVehicleMode = mappedMode
                _trackingState.update { s ->
                    s.copy(
                        connectedVehicleId  = resolution.vehicleId,
                        connectedDeviceName = resolution.vehicleName,
                        connectionLabel     = resolution.connectionLabel,
                        disconnectTimeMs    = null,
                        isManualOverride    = false
                    )
                }
                
                if (dbSessionId.isNotEmpty()) {
                    val db = RideDatabase.getDatabase(this@RideTrackerService)
                    val session = db.rideDao().getSessionById(dbSessionId)
                    session?.let {
                        db.rideDao().updateSession(
                            it.copy(
                                vehicleId = resolution.vehicleId,
                                connectionLabel = resolution.connectionLabel,
                                vehicleType = if (mappedMode == "CAR") "four_wheeler" else "two_wheeler"
                            )
                        )
                    }
                }
                val msg = if (wasManualOverride) {
                    "Connection restored! Auto-Tracking via ${resolution.connectionLabel} — ${resolution.vehicleName}"
                } else {
                    "${resolution.connectionLabel} — ${resolution.vehicleName}"
                }
                showAutoPauseOverlay(msg)
            } else {
                _trackingState.update { s ->
                    s.copy(
                        disconnectTimeMs = null,
                        isManualOverride = false
                    )
                }
                showAutoPauseOverlay("Connection restored with $deviceName!")
            }
            updateConnectedDevicesList()
            val snap = _trackingState.value
            updateNotification(snap.currentSpeedKmh, snap.totalDistanceMeters, snap.durationSeconds)
            return
        }

        // Scenario 2: Already pre-recording
        if (currentState.isPreRecording) {
            if (resolution != null) {
                _trackingState.update { s ->
                    s.copy(
                        connectedMacAddress = mac,
                        connectedDeviceName = resolution.vehicleName,
                        connectedVehicleId  = resolution.vehicleId,
                        connectionLabel     = resolution.connectionLabel,
                        disconnectTimeMs    = null
                    )
                }
                showAutoPauseOverlay("${resolution.connectionLabel} — ${resolution.vehicleName}")
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildPreRecordingNotification(
                    "${resolution.connectionLabel} | ${resolution.vehicleName}", isDisconnected = false))
            } else {
                // Reconnected same device — just clear countdown
                _trackingState.update { s -> s.copy(disconnectTimeMs = null) }
                showAutoPauseOverlay("Connection restored with $deviceName!")
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildPreRecordingNotification(deviceName, isDisconnected = false))
            }
            updateConnectedDevicesList()
            return
        }

        // Scenario 3: Fresh connection — start pre-recording if device belongs to a vehicle
        if (resolution != null) {
            val mappedMode = when (resolution.vehicleType.lowercase()) {
                "car" -> "CAR"
                "bike", "scooter" -> "BIKE"
                else -> "AUTO"
            }
            selectedVehicleMode = mappedMode

            showAutoPauseOverlay("${resolution.connectionLabel} — ${resolution.vehicleName}. Waiting for movement...")

            updateConnectedDevicesList()

            lastMovementTimeMs = System.currentTimeMillis()

            _trackingState.update { s ->
                s.copy(
                    isPreRecording      = true,
                    connectedMacAddress = mac,
                    connectedDeviceName = resolution.vehicleName,
                    connectedVehicleId  = resolution.vehicleId,
                    connectionLabel     = resolution.connectionLabel
                )
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    requestLocationUpdates()
                    startSensorCollection() // Bug #1 fix: launches coroutine collecting sensorFlow
                } catch (e: Exception) {
                    Log.e("RideTrackerService", "Failed to start pre-recording sensors/location", e)
                }
            }

            val notifLabel = "${resolution.connectionLabel} | ${resolution.vehicleName}"
            val finalNotification = buildPreRecordingNotification(notifLabel, isDisconnected = false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, finalNotification)
            updateConnectedDevicesList()
        } else {
            val db = RideDatabase.getDatabase(this@RideTrackerService)
            val vehicles = db.vehicleDao().getAllVehiclesSync()
            val converter = com.nexus.portal.data.local.ridetracker.VehicleConverters()
            val onVehicle = vehicles.any { v ->
                converter.toConnectedDevicesList(v.connectedDevicesJson)
                    .any { BluetoothMacUtils.macsEqual(it.macAddress, mac) }
            }
            if (!onVehicle) {
                Log.w("RideTrackerService", "Connected MAC not mapped to any vehicle: $mac")
                removeActiveMac(this@RideTrackerService, mac)
            } else {
                Log.w("RideTrackerService", "MAC on vehicle but resolution failed; keeping MAC for next sync: $mac")
            }
            updateConnectedDevicesList()
            checkAndStopServiceIfNotRunning()
        }
    }

    private fun handleDeviceDisconnected(mac: String) {
        serviceScope.launch {
            connectionMutex.withLock {
                handleDeviceDisconnectedLocked(mac)
            }
        }
    }

    private suspend fun handleDeviceDisconnectedLocked(mac: String) {
        val state = _trackingState.value
        val normalizedMac = BluetoothMacUtils.normalize(mac)

        // Re-resolve with the remaining MACs (mac was already removed from activelyConnectedMacs)
        val resolution = resolveVehicleForConnectedMacs(activelyConnectedMacs)

        if (resolution != null) {
            // Still have a valid device connected — perform a smooth handover
            val mappedMode = when (resolution.vehicleType.lowercase()) {
                "car" -> "CAR"
                "bike", "scooter" -> "BIKE"
                else -> "AUTO"
            }
            selectedVehicleMode = mappedMode

            _trackingState.update { s ->
                s.copy(
                    connectedVehicleId  = resolution.vehicleId,
                    connectedDeviceName = resolution.vehicleName,
                    connectionLabel     = resolution.connectionLabel,
                    disconnectTimeMs    = null
                )
            }
            showAutoPauseOverlay("${resolution.connectionLabel} — ${resolution.vehicleName}")

            if (state.isPreRecording) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildPreRecordingNotification(
                    "${resolution.connectionLabel} | ${resolution.vehicleName}", isDisconnected = false))
            } else if (state.isTracking && dbSessionId.isNotEmpty()) {
                // Update active session in DB with new vehicle / label
                val db = RideDatabase.getDatabase(this@RideTrackerService)
                val session = db.rideDao().getSessionById(dbSessionId)
                session?.let {
                    db.rideDao().updateSession(
                        it.copy(
                            vehicleId       = resolution.vehicleId,
                            connectionLabel = resolution.connectionLabel
                        )
                    )
                }
                updateNotification(state.currentSpeedKmh, state.totalDistanceMeters, state.durationSeconds)
            }
            updateConnectedDevicesList()
        } else {
            // No more saved devices connected → standard disconnect / countdown logic
            proceedWithDisconnect(state, normalizedMac)
        }
    }

    private fun proceedWithDisconnect(state: TrackingState, mac: String) {
        if (state.isPreRecording) {
            // Not tracking yet, start reconnect countdown instead of instantly stopping!
            val disconnectTime = System.currentTimeMillis()
            _trackingState.update { s -> s.copy(disconnectTimeMs = disconnectTime) }
            showAutoPauseOverlay("Signal lost with ${state.connectedDeviceName}. Reconnecting...")
            
            // Update notification
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildPreRecordingNotification(state.connectedDeviceName, isDisconnected = true))
            
            // Start a reconnect countdown coroutine
            serviceScope.launch {
                delay(30_000)
                val currentState = _trackingState.value
                if (currentState.isPreRecording && currentState.disconnectTimeMs == disconnectTime && currentState.connectedMacAddress == mac) {
                    // Still disconnected after 30s. Shut down cleanly!
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        // Bug #4 fix: stopSensorCollection cancels the flow coroutine;
                        // hardware is unregistered via callbackFlow awaitClose.
                        _trackingState.update { TrackingState() }
                        stopLocalLocationUpdates()
                        locationRepository.stopTracking()
                        stopSensorCollection()
                        stopForeground(true)
                        stopSelf()
                        showAutoPauseOverlay("Auto-Detect Stopped (Connection Lost)")
                    }
                }
            }
        } else if (state.isTracking) {
            // Already tracking, start disconnect countdown logic
            if (!state.isManualOverride) {
                _trackingState.update { s ->
                    s.copy(
                        disconnectTimeMs = System.currentTimeMillis(),
                        isManualOverride = false
                    )
                }
            }
        }
    }

    private fun startTracking(
        preRecordedPoints: List<RideLocationPoint> = emptyList(),
        autoSelectedVehicleId: String? = null,
        isAutoRecorded: Boolean = false
    ) {
        if (_trackingState.value.isTracking && preRecordedPoints.isEmpty()) return

        // Bug #6/#7 fix: reset per-session state so stale ticks/sensor filters don't
        // leak from a previous ride into this new tracking session.
        resetSessionState()
        durationJob?.cancel()
        _trackingState.value.connectedMacAddress?.let { cancelledAutoDetectMacs.remove(it) }
        val currentState = _trackingState.value
        val finalPoints = if (preRecordedPoints.isNotEmpty()) {
            preRecordedPoints
        } else if (currentState.isPreRecording) {
            currentState.points
        } else {
            emptyList()
        }
        
        // Backdate startTime to the first pre-recorded point so the DB session includes the buffer
        startTime = finalPoints.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        
        val finalVehicleId = autoSelectedVehicleId ?: currentState.connectedVehicleId
        
        // If we have pre-recorded points, calculate their accumulated stats
        var initialDistance = 0f
        if (finalPoints.size > 1) {
            for (i in 1 until finalPoints.size) {
                val p1 = Location("").apply { latitude = finalPoints[i-1].latitude; longitude = finalPoints[i-1].longitude }
                val p2 = Location("").apply { latitude = finalPoints[i].latitude; longitude = finalPoints[i].longitude }
                initialDistance += p1.distanceTo(p2)
            }
        }
        
        _trackingState.update { state ->
            state.copy(
                isTracking = true,
                isPreRecording = false,
                points = finalPoints,
                totalDistanceMeters = initialDistance,
                durationSeconds = finalPoints.size.toLong(), // approximate duration
                disconnectTimeMs = null // Clear any stale pre-recording disconnects
            )
        }

        durationJob = serviceScope.launch {
            // ── Sub-machine local state ───────────────────────────────────────────────
            var stationarySeconds = 0           // how long we've been slow (for auto-pause)
            var stationaryDisconnectSeconds = 0 // how long stopped AND BT disconnected (for auto-stop)
            var movingSeconds = 0               // consecutive seconds of movement (for robust auto-resume)
            var isAutoPaused = false            // true = WE auto-paused (not the user)
            var isManuallyPaused = false        // true = user explicitly hit Pause
            var disconnectPromptShown = false   // whether we already showed the "stopping in 30s" overlay
            var isAutoStopFired = false         // finality guard — blocks all further writes after stop
            var flushTick = 0
            var continuousPauseSeconds = 0      // how long we've been paused (for the 4-hour failsafe)
            // ─────────────────────────────────────────────────────────────────────────

            while (_trackingState.value.isTracking && !isAutoStopFired) {
                delay(1000)

                flushTick++
                if (flushTick >= 15) {
                    flushTick = 0
                    flushLocationBuffer()
                }

                // ── STEP 1: SNAPSHOT ─────────────────────────────────────────────────
                // Read all state once per tick so both sub-machines use the same values.
                val snap = _trackingState.value
                if (!snap.isTracking || isAutoStopFired) break

                val speed          = snap.currentSpeedKmh
                val isBtDisconnected = snap.disconnectTimeMs != null
                val isOverride     = snap.isManualOverride

                // Dynamic thresholds based on selected vehicle mode
                // Lower pause thresholds for snapper pause, but generous duration to handle bumper traffic.
                val pauseSpeedThreshold = if (selectedVehicleMode == "CAR") 3f else 2f
                val pauseDurationThreshold = if (selectedVehicleMode == "CAR") 45 else 30
                // Lower resume speed for snappy wake up, but with debounce logic.
                val resumeSpeedThreshold = if (selectedVehicleMode == "CAR") 5f else 3f

                // ── STEP 2: EVALUATE OVERRIDES ───────────────────────────────────────
                // Determine if the current isPaused was set by the USER (manual) or by US (auto).
                // Manual pause takes precedence: if isPaused is true but isAutoPaused is false,
                // the user hit the Pause button themselves.
                if (snap.isPaused && !isAutoPaused) {
                    // User explicitly paused (or resumed auto-pause via manual resume then re-paused)
                    isManuallyPaused = true
                } else if (!snap.isPaused) {
                    // Either user resumed, or auto-resume cleared it — either way not manually paused
                    isManuallyPaused = false
                }
                // isManuallyPaused and isAutoPaused are mutually exclusive: isManuallyPaused wins.
                if (isManuallyPaused && isAutoPaused) {
                    isAutoPaused = false // user took over, we relinquish our auto-pause claim
                }

                // ── SUB-MACHINE 1: AUTO-PAUSE ────────────────────────────────────────
                // Runs ONLY when BT is connected (or in manual override) AND user has NOT manually paused.
                // Suppressed during BT disconnect — auto-stop is the sole arbiter in that state.
                if (!isBtDisconnected || isOverride) {
                    if (speed > resumeSpeedThreshold) {
                        stationarySeconds = 0
                        if (isAutoPaused && !isManuallyPaused && snap.isPaused) {
                            val isUserInVehicle = currentDetectedActivity == "IN_VEHICLE" || currentDetectedActivity == "ON_BICYCLE"
                            if (accelVariance > 0.04f || speed > 8f || (isUserInVehicle && speed > 3f)) { // Require vibration/motion OR fail-safe speed OR confirmed vehicle state
                                movingSeconds++
                                val requiredDuration = if (isUserInVehicle) 1 else 2
                                if (movingSeconds >= requiredDuration) { // Require 2s (or 1s if confirmed in-vehicle) of speed > resumeSpeedThreshold
                                    isAutoPaused = false
                                    movingSeconds = 0
                                    updatePauseState(false)
                                    showAutoPauseOverlay("Ride Auto-Resumed")
                                }
                            } else {
                                movingSeconds = 0
                            }
                        } else {
                            movingSeconds = 0
                        }
                    } else {
                        movingSeconds = 0
                        if (speed < pauseSpeedThreshold && !snap.isPaused) {
                            stationarySeconds++
                        }
                    }

                    if (stationarySeconds >= pauseDurationThreshold && !isAutoPaused && !snap.isPaused) {
                        isAutoPaused = true
                        stationarySeconds = 0 // reset immediately so it can't re-fire next tick
                        updatePauseState(true)
                        showAutoPauseOverlay("Ride Auto-Paused (Stationary)")
                    }
                } else {
                    // BT disconnected — auto-stop takes over. Release any auto-pause we were holding
                    // but do NOT touch a manual pause.
                    if (isAutoPaused && !isManuallyPaused) {
                        isAutoPaused = false
                        updatePauseState(false)
                    }
                    stationarySeconds = 0 // reset so auto-pause starts fresh if BT reconnects
                    movingSeconds = 0
                }

                // ── SUB-MACHINE 2: AUTO-STOP ─────────────────────────────────────────
                // Runs ONLY when BT is disconnected AND NOT in manual override.
                // Key rule: respects isManuallyPaused (Option B) — if user explicitly paused,
                // we hold the countdown at 0 until they resume.
                if (isBtDisconnected && !isOverride) {
                    val isHeldByManualPause = isManuallyPaused // user said "I'll be back"

                    if (speed > 5f) {
                        // Movement detected while disconnected -> Auto-promote to manual override tracking
                        _trackingState.update { state ->
                            state.copy(
                                disconnectTimeMs = null,
                                autoStopRemainingSeconds = null,
                                isManualOverride = true,
                                isPaused = false
                            )
                        }
                        showAutoPauseOverlay("Switched to Manual Tracking Mode (Movement Detected)")
                        stationaryDisconnectSeconds = 0
                        disconnectPromptShown = false
                    } else if (speed < 5f && !isHeldByManualPause) {
                        // Stationary AND disconnected AND user didn't manually pause — count down
                        stationaryDisconnectSeconds++
                        val remaining = (180 - stationaryDisconnectSeconds).coerceAtLeast(0)
                        _trackingState.update { it.copy(autoStopRemainingSeconds = remaining) }

                        if (stationaryDisconnectSeconds == 30 && !disconnectPromptShown) {
                            showAutoPauseOverlay("Bluetooth Disconnected. Stopping ride in 150s if stationary.")
                            disconnectPromptShown = true
                        }

                        if (stationaryDisconnectSeconds >= 180) {
                            // 180s (3 minutes) stationary + disconnected + not manually paused → stop ride
                            isAutoStopFired = true
                            stopTracking()
                        }
                    }
                } else {
                    // BT reconnected (or in manual override) — fully reset auto-stop state
                    if (stationaryDisconnectSeconds > 0 || _trackingState.value.autoStopRemainingSeconds != null) {
                        stationaryDisconnectSeconds = 0
                        disconnectPromptShown = false
                        _trackingState.update { it.copy(autoStopRemainingSeconds = null) }
                    }
                }

                // ── STEP 3: DURATION TICKER ──────────────────────────────────────────
                // Runs after both sub-machines have settled the isPaused state for this tick.
                if (!isAutoStopFired) {
                    val latest = _trackingState.value
                    if (!latest.isPaused) {
                        continuousPauseSeconds = 0
                        val newDuration = latest.durationSeconds + 1
                        _trackingState.update { it.copy(durationSeconds = newDuration) }
                        updateNotification(latest.currentSpeedKmh, latest.totalDistanceMeters, newDuration)
                    } else {
                        continuousPauseSeconds++
                        if (continuousPauseSeconds >= 4 * 60 * 60) { // 4 hours
                            Log.d("RideTrackerService", "Failsafe: Auto-stopping ride due to 4 hour continuous pause.")
                            isAutoStopFired = true
                            stopTracking()
                            break
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            try {
                val db = RideDatabase.getDatabase(this@RideTrackerService)
                val dbVehicleType = if (selectedVehicleMode == "CAR") "four_wheeler" else "two_wheeler"
                dbSessionId = java.util.UUID.randomUUID().toString()
                Log.d("RideTrackerService", "startTracking: created new session ID: $dbSessionId")
                
                val isPublicSetting = com.nexus.portal.managers.RideSettingsManager.getInstance(applicationContext).defaultRideVisibilityPublic.value
                val newSession = RideSession(
                    id = dbSessionId,
                    startTime = startTime,
                    endTime = null,
                    totalDistanceMeters = 0f,
                    maxSpeedKmh = 0f,
                    averageSpeedKmh = 0f,
                    maxLeanAngle = 0f,
                    peakLatG = 0f,
                    flowRating = 100,
                    vehicleType = dbVehicleType,
                    vehicleId = finalVehicleId,
                    connectedMacAddress = _trackingState.value.connectedMacAddress,
                    isAutoRecorded = isAutoRecorded,
                    connectionLabel = _trackingState.value.connectionLabel,
                    isPublic = isPublicSetting
                )
                db.rideDao().upsertSession(newSession)
                Log.d("RideTrackerService", "startTracking: upserted session $dbSessionId to database")
                
                // Insert pre-recorded points into DB and assign them the session ID
                if (finalPoints.isNotEmpty()) {
                    val assignedPoints = finalPoints.map { it.copy(sessionId = dbSessionId) }
                    db.rideDao().insertLocationPoints(assignedPoints)
                    _trackingState.update { it.copy(points = assignedPoints) }
                    Log.d("RideTrackerService", "startTracking: inserted ${assignedPoints.size} pre-recorded points")
                }
            } catch (e: Exception) {
                Log.e("RideTrackerService", "Failed to save session to database", e)
                // Continue anyway - session can still be tracked in memory
            }
        }

        try {
            startForegroundService()
            requestLocationUpdates()
            startSensorCollection()
        } catch (e: Exception) {
            Log.e("RideTrackerService", "Failed to start foreground service or register sensors", e)
            // Attempt recovery
            try {
                requestLocationUpdates()
            } catch (e2: Exception) {
                Log.e("RideTrackerService", "Location updates also failed", e2)
            }
            try {
                startSensorCollection()
            } catch (e2: Exception) {
                Log.e("RideTrackerService", "Sensor collection also failed", e2)
            }
        }
    }

    private fun pauseTracking() {
        if (!_trackingState.value.isTracking || _trackingState.value.isPaused) return
        updatePauseState(true)
    }

    private fun resumeTracking() {
        if (!_trackingState.value.isTracking || !_trackingState.value.isPaused) return
        updatePauseState(false)
    }

    private fun stopTracking() {
        if (!_trackingState.value.isTracking) return

        durationJob?.cancel()
        stopLocalLocationUpdates()
        locationRepository.stopTracking()
        // Bug #1 fix: stopSensorCollection cancels the sensorJob coroutine; the
        // callbackFlow awaitClose block then unregisters hardware sensors automatically.
        stopSensorCollection()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        isStoppingRide = true // Prevent new connections from triggering
        _trackingState.update { it.copy(isTracking = false) }
        val currentState = _trackingState.value

        serviceScope.launch {
            connectionMutex.withLock {
                flushLocationBufferSync()
                stopTrackingLocked(currentState)
                isStoppingRide = false
            }
        }
    }

    private suspend fun stopTrackingLocked(currentState: TrackingState) {
        var shouldShowNotification = true
        if (dbSessionId.isNotEmpty()) {
            val db = RideDatabase.getDatabase(this@RideTrackerService)
            val session = db.rideDao().getSessionById(dbSessionId)
            session?.let {
                val points = currentState.points
                val maxSpeed = points.maxOfOrNull { it.speedMetersPerSec * 3.6f } ?: 0f
                val avgSpeed = if (points.isNotEmpty()) points.map { it.speedMetersPerSec * 3.6f }.average().toFloat() else 0f
                
                val finalMaxLean = points.maxOfOrNull { kotlin.math.abs(it.leanAngle) } ?: 0f
                val finalPeakLatG = points.maxOfOrNull { kotlin.math.abs(it.accelLateral) } ?: 0f
                
                val resolvedVehicleType = if (selectedVehicleMode == "AUTO") {
                    val activePoints = points.filter { it.speedMetersPerSec * 3.6f > 5f }
                    val significantLeanCount = activePoints.count { kotlin.math.abs(it.leanAngle) > 8f }
                    if (finalMaxLean > 12f || (activePoints.isNotEmpty() && (significantLeanCount.toFloat() / activePoints.size) > 0.03f)) {
                        "two_wheeler"
                    } else {
                        "four_wheeler"
                    }
                } else {
                    if (selectedVehicleMode == "CAR") "four_wheeler" else "two_wheeler"
                }
                
                var flowScore = 100
                val hardBrakes = points.count { it.gForce > 0.6f }
                flowScore -= hardBrakes * 2
                val extremeLeanCount = points.count { kotlin.math.abs(it.leanAngle) > 40f }
                flowScore -= extremeLeanCount
                flowScore = flowScore.coerceIn(0, 100)

                if (currentState.totalDistanceMeters < 50f || currentState.durationSeconds < 60) {
                    db.rideDao().updateSession(
                        it.copy(
                            isDeleted = true,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                    shouldShowNotification = false
                } else {
                    db.rideDao().updateSession(
                        it.copy(
                            endTime = System.currentTimeMillis(),
                            totalDistanceMeters = currentState.totalDistanceMeters,
                            maxSpeedKmh = maxSpeed,
                            averageSpeedKmh = avgSpeed,
                            maxLeanAngle = finalMaxLean,
                            peakLatG = finalPeakLatG,
                            vehicleType = resolvedVehicleType,
                            flowRating = flowScore,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (shouldShowNotification) {
                showRideCompleteNotification(currentState)
                showAutoPauseOverlay("Ride Completed & Saved!")
            } else {
                showAutoPauseOverlay("Ride Auto-Stopped (Session too short to save)")
            }
        }
        
        // NOTIF-Bug #2 fix: cancel the stale tracking notification immediately so it doesn't
        // linger at the previous speed/distance values during the reconnect resolution below.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }

        // Trigger sync to cloud
        com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(this@RideTrackerService)
        
        var autoDetectStarted = false
        currentState.connectedMacAddress?.let { removeCancelledMac(this@RideTrackerService, it) }
        
        // Check if there's any saved vehicle device currently connected using priority resolution
        val dbConnection = RideDatabase.getDatabase(this@RideTrackerService)
        val vehicles = dbConnection.vehicleDao().getAllVehiclesSync()
        val converter = com.nexus.portal.data.local.ridetracker.VehicleConverters()
        
        val resolution = resolveVehicleForConnectedMacs(activelyConnectedMacs)
        var connectedMac: String? = null
        var connectedName: String? = null
        
        if (resolution != null) {
            val resolvedVehicle = vehicles.firstOrNull { it.id == resolution.vehicleId }
            if (resolvedVehicle != null) {
                val devices = converter.toConnectedDevicesList(resolvedVehicle.connectedDevicesJson)
                val activeDevice = devices.firstOrNull { BluetoothMacUtils.containsMac(activelyConnectedMacs, it.macAddress) }
                if (activeDevice != null) {
                    connectedMac = activeDevice.macAddress
                    connectedName = activeDevice.name
                }
            }
            if (connectedMac == null) {
                connectedMac = activelyConnectedMacs.firstOrNull()
                connectedName = resolution.vehicleName
            }
            
            // Yes, a saved device is still connected! Instead of stopping the service, transition to pre-recording
            val mappedMode = when (resolution.vehicleType.lowercase()) {
                "car" -> "CAR"
                "bike", "scooter" -> "BIKE"
                else -> "AUTO"
            }
            selectedVehicleMode = mappedMode
            lastMovementTimeMs = System.currentTimeMillis()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _trackingState.update { s ->
                    TrackingState(
                        isPreRecording = true,
                        connectedMacAddress = connectedMac,
                        connectedDeviceName = resolution.vehicleName,
                        connectedVehicleId = resolution.vehicleId,
                        connectionLabel = resolution.connectionLabel,
                        connectedDevices = s.connectedDevices
                    )
                }
                requestLocationUpdates()
                startSensorCollection() // restart flow collection for the new pre-recording session

                showAutoPauseOverlay("Connected to ${resolution.vehicleName}. Waiting for movement...")
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildPreRecordingNotification("${resolution.connectionLabel} | ${resolution.vehicleName}", isDisconnected = false))
                updateConnectedDevicesList()
            }
            autoDetectStarted = true
        }
        
        if (!autoDetectStarted) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _trackingState.update { TrackingState() } // Reset
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun isDeviceConnected(macAddress: String): Boolean {
        if (macAddress.isBlank()) return false
        return BluetoothMacUtils.containsMac(activelyConnectedMacs, macAddress)
    }

    private fun updateConnectedDevicesList() {
        serviceScope.launch {
            val db = RideDatabase.getDatabase(this@RideTrackerService)
            val vehicles = db.vehicleDao().getAllVehiclesSync()
            val converter = com.nexus.portal.data.local.ridetracker.VehicleConverters()
            
            val list = mutableListOf<Pair<String, String>>()
            for (v in vehicles) {
                val devices = converter.toConnectedDevicesList(v.connectedDevicesJson)
                for (d in devices) {
                    if (BluetoothMacUtils.containsMac(activelyConnectedMacs, d.macAddress)) {
                        list.add(Pair(d.name, BluetoothMacUtils.normalize(d.macAddress)))
                    }
                }
            }
            
            _trackingState.update { currentState ->
                currentState.copy(
                    connectedDevices = list
                )
            }
        }
    }

    // ── Sensor lifecycle ─────────────────────────────────────────────────────────
    // All direct SensorEventListener / SensorManager usage removed.
    // Sensors are now managed exclusively via sensorRepository.sensorFlow (callbackFlow).
    // Hardware registration/unregistration is handled by the callbackFlow awaitClose block.

    /** Start (or restart) collecting the sensor flow. Cancels any existing collection first. */
    private fun startSensorCollection() {
        sensorJob?.cancel()
        sensorJob = serviceScope.launch {
            sensorRepository.sensorFlow.collect { data ->
                processSensorData(data)
            }
        }
    }

    /** Cancel sensor flow collection. The callbackFlow awaitClose block unregisters hardware. */
    private fun stopSensorCollection() {
        sensorJob?.cancel()
        sensorJob = null
    }

    /**
     * Reset all per-session state so values from a previous ride do not bleed into
     * the next session (Bugs #6 and #7).
     */
    private fun resetSessionState() {
        consecutiveSpeedTicks = 0
        lastAzimuth = -1000f
        hasGravity = false
        hasMagnetic = false
        hasGyro = false
        gravity.fill(0f)
        linearAcceleration.fill(0f)
        geomagnetic.fill(0f)
        gyroValues.fill(0f)
        meanAccelMag = 0f
        meanSquaredAccelMag = 0f
        accelVariance = 0f
    }

    /**
     * Processes a SensorData packet from sensorRepository.sensorFlow.
     * Contains all logic previously in onSensorChanged() + onAccuracyChanged().
     * Called from a coroutine on serviceScope (Dispatchers.IO).
     */
    private fun processSensorData(data: SensorData) {
        // ── Gyroscope ──────────────────────────────────────────────────────────
        System.arraycopy(data.gyroValues, 0, gyroValues, 0, 3)
        hasGyro = true

        // ── Magnetometer ───────────────────────────────────────────────────────
        geomagnetic[0] = data.magValues[0]
        geomagnetic[1] = data.magValues[1]
        geomagnetic[2] = data.magValues[2]
        hasMagnetic = true

        // ── Accelerometer with low-pass gravity filter ─────────────────────────
        val accelValues = data.accelValues
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * accelValues[0]
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * accelValues[1]
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * accelValues[2]
        linearAcceleration[0] = accelValues[0] - gravity[0]
        linearAcceleration[1] = accelValues[1] - gravity[1]
        linearAcceleration[2] = accelValues[2] - gravity[2]
        hasGravity = true

        // ── Vibration variance (used for auto-resume confidence) ───────────────
        val mag = sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
            linearAcceleration[1] * linearAcceleration[1] +
            linearAcceleration[2] * linearAcceleration[2]
        )
        val varianceAlpha = 0.98f
        meanAccelMag = varianceAlpha * meanAccelMag + (1 - varianceAlpha) * mag
        meanSquaredAccelMag = varianceAlpha * meanSquaredAccelMag + (1 - varianceAlpha) * (mag * mag)
        accelVariance = abs(meanSquaredAccelMag - (meanAccelMag * meanAccelMag))

        // ── Adaptive GPS rate (pre-recording & auto-pause battery saving) ───────
        val snap = _trackingState.value
        if (snap.isPreRecording || (snap.isTracking && snap.isPaused)) {
            if (accelVariance > 0.05f) {
                lastMovementTimeMs = System.currentTimeMillis()
                if (currentIntervalMs != 1000L) {
                    requestLocationUpdates(1000L, Priority.PRIORITY_HIGH_ACCURACY)
                }
            } else if (snap.isPreRecording) {
                val now = System.currentTimeMillis()
                if (now - lastMovementTimeMs > 180_000L) { // 3 minutes idle → slow GPS
                    if (currentIntervalMs != 10000L) {
                        requestLocationUpdates(10000L, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    }
                }
            }
        }

        // ── Vehicle telemetry (G-forces, lean angle) ───────────────────────────
        val gyro = if (hasGyro) gyroValues else null
        val speed = _trackingState.value.currentSpeedKmh / 3.6f
        val isTwoWheeler = selectedVehicleMode == "BIKE" || selectedVehicleMode == "SCOOTER" ||
            (selectedVehicleMode == "AUTO" && abs(_trackingState.value.leanAngle) > 12f)

        val telemetry = telemetryEngine.processSensors(
            accelValues = accelValues,
            gyroValues = gyro,
            speedMps = speed,
            isTwoWheeler = isTwoWheeler
        )

        _trackingState.update { s ->
            s.copy(
                gForce = telemetry.totalG,
                accelLateral = telemetry.lateralG,
                accelLongitudinal = telemetry.longitudinalG,
                leanAngle = telemetry.leanAngle
            )
        }

        // ── Compass heading via rotation matrix ────────────────────────────────
        if (hasGravity && hasMagnetic) {
            val rotationMatrix = FloatArray(9)
            val inclinationMatrix = FloatArray(9)
            val success = SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, geomagnetic)
            if (success) {
                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuthDeg < 0) azimuthDeg += 360f

                if (lastAzimuth < -500f) {
                    lastAzimuth = azimuthDeg
                } else {
                    var diff = azimuthDeg - lastAzimuth
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    lastAzimuth += diff * 0.15f
                    if (lastAzimuth < 0) lastAzimuth += 360f
                    if (lastAzimuth >= 360f) lastAzimuth -= 360f
                }
                _trackingState.update { s -> s.copy(compassHeading = lastAzimuth) }
            }
        }
    }

    private fun requestActivityUpdates() {
        try {
            val permission = android.Manifest.permission.ACTIVITY_RECOGNITION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w("RideTrackerService", "Activity Recognition permission not granted")
                return
            }

            activityRecognitionClient = ActivityRecognition.getClient(this)
            val intent = Intent("com.nexus.portal.ACTION_PROCESS_ACTIVITY").apply {
                setPackage(packageName)
            }
            activityPendingIntent = PendingIntent.getBroadcast(
                this,
                99,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(activityReceiver, IntentFilter("com.nexus.portal.ACTION_PROCESS_ACTIVITY"), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(activityReceiver, IntentFilter("com.nexus.portal.ACTION_PROCESS_ACTIVITY"))
            }

            activityRecognitionClient?.requestActivityUpdates(30000L, activityPendingIntent!!)
                ?.addOnFailureListener { Log.e("RideTrackerService", "Activity Recognition updates request failed", it) }
        } catch (e: SecurityException) {
            Log.e("RideTrackerService", "Activity Recognition SecurityException", e)
        } catch (e: Exception) {
            Log.e("RideTrackerService", "Failed to start Activity Recognition", e)
        }
    }

    private fun removeActivityUpdates() {
        try {
            activityPendingIntent?.let {
                activityRecognitionClient?.removeActivityUpdates(it)
            }
            unregisterReceiver(activityReceiver)
        } catch (_: Exception) {}
    }

    // Also always request location updates for map centering even when not tracking
    fun startPassiveLocation() {
        try {
            locationRepository.startTracking(2000L, com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5f)
            startSensorCollection() // enables compass heading for map display
        } catch (e: SecurityException) {
            Log.e("RideTrackerService", "Location permission missing", e)
        }
    }

    private fun startForegroundService() {
        val notification = buildNotification(0f, 0f, 0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseLocationForegroundType()) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(speed: Float, distance: Float, durationSeconds: Long): Notification {
        val state = _trackingState.value
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.nexus.portal.ACTION_RIDE_TRACKER"
            putExtra("navigate_to", "ride_tracker")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, RideTrackerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPaused = state.isPaused
        val pauseResumeActionIntent = Intent(this, RideTrackerService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pendingPauseResumeIntent = PendingIntent.getService(
            this, 2, pauseResumeActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val keepActiveIntent = Intent(this, RideTrackerService::class.java).apply {
            action = ACTION_DISMISS_DISCONNECT_WARNING
        }
        val pendingKeepActiveIntent = PendingIntent.getService(
            this, 4, keepActiveIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val m = durationSeconds / 60
        val s = durationSeconds % 60
        val durationStr = String.format("%02d:%02d", m, s)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (state.isManualOverride) {
            val statusText = if (isPaused) {
                "Paused | Manual Mode | Dist: ${String.format("%.1f", distance / 1000)} km | Time: $durationStr"
            } else {
                "Manual Mode | Dist: ${String.format("%.1f", distance / 1000)} km | Time: $durationStr"
            }
            builder.setContentTitle(if (isPaused) "Ride Tracker — Paused (Manual Mode)" else "Ride Tracker — Manual Mode")
            builder.setContentText(statusText)
            
            builder.addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pendingPauseResumeIntent
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStopIntent)
        } else if (state.disconnectTimeMs != null) {
            val deviceName = state.connectedDeviceName ?: "device"
            val remaining = state.autoStopRemainingSeconds
            
            if (remaining != null && remaining <= 150) {
                builder.setContentTitle("Ride Tracker — Signal Lost")
                builder.setContentText("Lost connection with $deviceName. Stopping ride in $remaining seconds...")
                builder.setSubText("Stationary Countdown")
                
                builder.addAction(android.R.drawable.ic_media_play, "Keep Ride Active", pendingKeepActiveIntent)
                builder.addAction(
                    if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    if (isPaused) "Resume" else "Pause",
                    pendingPauseResumeIntent
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStopIntent)
            } else {
                builder.setContentTitle("Ride Tracker — Signal Lost")
                builder.setContentText("Lost connection with $deviceName. Ride active while moving.")
                builder.setSubText("Signal Lost")
                
                builder.addAction(android.R.drawable.ic_media_play, "Keep Ride Active", pendingKeepActiveIntent)
                builder.addAction(
                    if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    if (isPaused) "Resume" else "Pause",
                    pendingPauseResumeIntent
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStopIntent)
            }
        } else {
            val statusText = if (isPaused) {
                "Paused | Dist: ${String.format("%.1f", distance / 1000)} km | Time: $durationStr"
            } else {
                "Speed: ${speed.toInt()} km/h | Dist: ${String.format("%.1f", distance / 1000)} km | Time: $durationStr"
            }
            
            builder.setContentTitle(if (isPaused) "Ride Tracker — Paused" else "Ride Tracker — Active Session")
            builder.setContentText(statusText)
            
            builder.addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pendingPauseResumeIntent
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStopIntent)
        }

        return builder.build()
    }

    private fun buildPreRecordingNotification(deviceName: String?, isDisconnected: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.nexus.portal.ACTION_RIDE_TRACKER_PRE"
            putExtra("navigate_to", "ride_tracker")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, RideTrackerService::class.java).apply {
            action = ACTION_CANCEL_AUTO_DETECT
            putExtra(EXTRA_MAC_ADDRESS, _trackingState.value.connectedMacAddress)
        }
        val pendingCancelIntent = PendingIntent.getService(
            this, 3, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (isDisconnected) {
            "Signal lost with ${deviceName ?: "device"}. Reconnecting..."
        } else {
            "Connected to ${deviceName ?: "device"}. Waiting for movement..."
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(if (isDisconnected) "Ride Tracker — Signal Lost" else "Ride Tracker — Bluetooth Connected")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel Auto-Detect", pendingCancelIntent)
            .build()
    }

    private fun showRideCompleteNotification(state: TrackingState) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.nexus.portal.ACTION_RIDE_TRACKER_COMPLETE"
            putExtra("navigate_to", "ride_tracker")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val points = state.points
        val avgSpeed = if (points.isNotEmpty()) points.map { it.speedMetersPerSec * 3.6f }.average().toFloat() else 0f
        val maxLean = points.maxOfOrNull { kotlin.math.abs(it.leanAngle) } ?: 0f

        val summaryText = String.format(
            "Dist: %.2f km | Avg Spd: %d km/h | Max Lean: %d°",
            state.totalDistanceMeters / 1000f,
            avgSpeed.toInt(),
            maxLean.toInt()
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ride Complete!")
            .setContentText(summaryText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(false) // Allow swipe-to-dismiss
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_COMPLETE_ID, builder)
    }

    private fun updateNotification(speed: Float = 0f, distance: Float = 0f, durationSeconds: Long = 0L) {
        val state = _trackingState.value
        if (!state.isTracking && !state.isPreRecording) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Active notification checker (Battery Monitor revival pattern)
        val isActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.activeNotifications.any { it.id == NOTIFICATION_ID }
        } else {
            true
        }

        val notification = if (state.isTracking) {
            buildNotification(speed, distance, durationSeconds)
        } else {
            buildPreRecordingNotification(state.connectedDeviceName, isDisconnected = state.disconnectTimeMs != null)
        }

        if (!isActive) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseLocationForegroundType()) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showAutoPauseOverlay(message: String) {
        serviceScope.launch {
            autoPauseEventFlow.tryEmit(message)
            // NOTIF-Bug #1 fix: check SYSTEM_ALERT_WINDOW permission before starting overlay
            // service; prevents silent BadTokenException crashes when permission is not granted.
            val hasOverlayPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(this@RideTrackerService)
            } else true

            if (hasOverlayPermission) {
                try {
                    val intent = Intent(this@RideTrackerService, AutoPauseOverlayService::class.java).apply {
                        putExtra("MESSAGE", message)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } catch (e: Exception) {
                    Log.e("RideTrackerService", "Overlay service start failed", e)
                }
            } else {
                // Fallback: post a low-priority status notification when overlay is unavailable
                Log.d("RideTrackerService", "SYSTEM_ALERT_WINDOW not granted, skipping overlay. Message: $message")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(this@RideTrackerService, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun canUseLocationForegroundType(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val hasFgsLoc = ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_LOCATION") == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val ok = hasFgsLoc && (hasFine || hasCoarse)
        if (!ok) {
            val missing = mutableListOf<String>()
            if (!hasFgsLoc) missing.add("FOREGROUND_SERVICE_LOCATION")
            if (!hasFine) missing.add("ACCESS_FINE_LOCATION")
            if (!hasCoarse) missing.add("ACCESS_COARSE_LOCATION")
            val msg = "FGS location subtype unavailable; missing: ${missing.joinToString(", ")}. Falling back to plain FGS."
            Log.w("RideTrackerService", msg)
            try {
                ErrorLogger.logError(applicationContext, Throwable(msg), false)
            } catch (_: Exception) { }
        }
        return ok
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Sensor fusion: accelerometer + magnetometer -> rotation matrix -> world frame G-forces.
     *
     * World frame:
     *   X = East  (positive = right lateral G when facing North)
     *   Y = North (positive = forward accel)
     *   Z = Up    (gravity)
     *
     * We extract:
     *   accelLateral      = worldAccel[0] (East component) — left/right
     *   accelLongitudinal = worldAccel[1] (North component) — forward/brake
     *   leanAngle         = roll from rotation matrix (orientation-independent)
     */
    // onSensorChanged and onAccuracyChanged removed — sensor processing now lives in
    // processSensorData(), called from the sensorRepository.sensorFlow collector coroutine.
    // This eliminates the mixed direct-listener + repository pattern that caused Bugs #1–#5.
}
