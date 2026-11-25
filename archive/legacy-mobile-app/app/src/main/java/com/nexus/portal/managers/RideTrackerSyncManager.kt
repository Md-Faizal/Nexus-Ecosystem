package com.nexus.portal.managers

import android.content.Context
import android.util.Log
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import com.nexus.portal.data.local.ridetracker.RideSession
import com.nexus.portal.data.local.ridetracker.VehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nexus.portal.util.BluetoothMacUtils

object RideTrackerSyncManager {

    private const val PREFS_NAME = "ride_tracker_sync_prefs"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    fun getSavedLastSyncTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun saveLastSyncTime(context: Context, time: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private fun updateProgress(progress: Float, status: String) {
        _syncProgress.value = progress.coerceIn(0f, 1f)
        _syncStatus.value = status
        Log.d("RideTrackerSync", "Progress: ${(progress * 100).toInt()}% - $status")
    }

    suspend fun performFullSync(context: Context): Pair<Boolean, String?> {
        _syncProgress.value = 0f
        _syncStatus.value = "Starting sync..."
        
        val syncStartTime = System.currentTimeMillis()
        
        val (fetchSuccess, fetchError) = fetchFromCloudInternal(context, progressOffset = 0f, progressScale = 0.5f)
        if (!fetchSuccess) {
            _syncStatus.value = "Download failed"
            return Pair(false, "Download failed:\n$fetchError")
        }
        
        val (syncSuccess, syncError) = syncWithCloudInternal(context, progressOffset = 0.5f, progressScale = 0.5f)
        if (!syncSuccess) {
            _syncStatus.value = "Upload failed"
            return Pair(false, "Upload failed:\n$syncError")
        }
        
        saveLastSyncTime(context, syncStartTime)
        
        _syncProgress.value = 1f
        _syncStatus.value = "Sync complete"
        return Pair(true, null)
    }

    fun schedulePeriodicSync(context: Context, enabled: Boolean) {
        try {
            val workManager = androidx.work.WorkManager.getInstance(context.applicationContext)
            if (enabled) {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                val syncRequest = androidx.work.PeriodicWorkRequestBuilder<com.nexus.portal.services.RideTrackerSyncWorker>(
                    2, java.util.concurrent.TimeUnit.HOURS
                )
                .setConstraints(constraints)
                .build()
                workManager.enqueueUniquePeriodicWork(
                    "RideTrackerPeriodicSync",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                Log.d("RideTrackerSync", "Scheduled periodic sync worker (every 2 hours)")
            } else {
                workManager.cancelUniqueWork("RideTrackerPeriodicSync")
                Log.d("RideTrackerSync", "Cancelled periodic sync worker")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Log.e("RideTrackerSync", "Error scheduling periodic sync: ${t.message}")
        }
    }

    suspend fun syncWithCloud(context: Context): Pair<Boolean, String?> {
        val syncStartTime = System.currentTimeMillis()
        val res = syncWithCloudInternal(context, 0f, 1f)
        if (res.first) {
            saveLastSyncTime(context, syncStartTime)
        }
        return res
    }

    suspend fun fetchFromCloud(context: Context): Pair<Boolean, String?> {
        val syncStartTime = System.currentTimeMillis()
        val res = fetchFromCloudInternal(context, 0f, 1f)
        if (res.first) {
            saveLastSyncTime(context, syncStartTime)
        }
        return res
    }

    private suspend fun syncWithCloudInternal(
        context: Context,
        progressOffset: Float,
        progressScale: Float
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val db = RideDatabase.getDatabase(context)
            val settingsManager = RideSettingsManager.getInstance(context)

            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId

            updateProgress(progressOffset + 0.05f * progressScale, "Preparing upload payload...")

            val payload = JSONObject()
            payload.put("profileId", profileId)
            payload.put("deviceId", deviceId)
            payload.put("settings", settingsManager.getSettingsJson())

            // Vehicles (including deleted tombstones)
            val vehiclesList = db.vehicleDao().getAllVehiclesIncludingDeleted().first()
            
            // Upload any offline local images to server first
            val updatedVehicles = vehiclesList.mapIndexed { idx, v ->
                val imgProgress = progressOffset + (0.05f + (idx.toFloat() / maxOf(1, vehiclesList.size)) * 0.10f) * progressScale
                updateProgress(imgProgress, "Checking vehicle images...")

                var updated = false
                var newImg = v.imageUrl
                var newOrigImg = v.originalImageUrl
                
                if (v.imageUrl.isNotBlank() && !v.imageUrl.startsWith("http://") && !v.imageUrl.startsWith("https://")) {
                    val uploaded = uploadLocalImage(v.imageUrl, v.id)
                    if (uploaded.startsWith("http://") || uploaded.startsWith("https://")) {
                        newImg = uploaded
                        updated = true
                    }
                }
                
                if (v.originalImageUrl.isNotBlank() && !v.originalImageUrl.startsWith("http://") && !v.originalImageUrl.startsWith("https://")) {
                    if (v.originalImageUrl == v.imageUrl && newImg.startsWith("http")) {
                        newOrigImg = newImg
                        updated = true
                    } else {
                        val uploaded = uploadLocalImage(v.originalImageUrl, v.id)
                        if (uploaded.startsWith("http://") || uploaded.startsWith("https://")) {
                            newOrigImg = uploaded
                            updated = true
                        }
                    }
                }
                
                if (updated) {
                    val updatedVehicle = v.copy(imageUrl = newImg, originalImageUrl = newOrigImg, lastUpdated = System.currentTimeMillis())
                    db.vehicleDao().insertVehicle(updatedVehicle)
                    updatedVehicle
                } else {
                    v
                }
            }

            val vehiclesArray = JSONArray()
            updatedVehicles.forEach { v ->
                val vJson = JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("type", v.type)
                    put("make", v.make)
                    put("model", v.model)
                    put("year", v.year)
                    put("licensePlate", v.licensePlate)
                    put("vin", v.vin)
                    put("insurancePolicyNumber", v.insurancePolicyNumber)
                    put("insuranceDate", v.insuranceDate)
                    put("pucDate", v.pucDate)
                    put("nextServiceDate", v.nextServiceDate)
                    put("fuelType", v.fuelType)
                    put("tankCapacity", v.tankCapacity)
                    put("description", v.description)
                    put("color", v.color)
                    put("engineCC", v.engineCC)
                    put("odometer", v.odometer)
                    put("topSpeed", v.topSpeed)
                    put("fuelRange", v.fuelRange)
                    put("maintenanceDue", v.maintenanceDue)
                    put("isActive", if (v.isActive) 1 else 0)
                    put("imageUrl", v.imageUrl)
                    put("originalImageUrl", v.originalImageUrl)
                    put("statsAvgSpeed", v.statsAvgSpeed)
                    put("statsLeanAngle", v.statsLeanAngle)
                    put("connectedDevicesJson", v.connectedDevicesJson)
                    put("tripHistoryPreviewCount", v.tripHistoryPreviewCount)
                    put("lastUpdated", v.lastUpdated)
                    put("isDeleted", if (v.isDeleted) 1 else 0)
                    put("isOccasional", if (v.isOccasional) 1 else 0)
                }
                vehiclesArray.put(vJson)
            }
            payload.put("vehicles", vehiclesArray)

            // Sessions & Locations (including deleted tombstones)
            val lastSyncTime = getSavedLastSyncTime(context)
            val sessionsList = db.rideDao().getSessionsUpdatedAfter(lastSyncTime)
            val sessionsArray = JSONArray()
            val locationsArray = JSONArray()

            sessionsList.forEachIndexed { idx, s ->
                val prepProgress = progressOffset + (0.20f + (idx.toFloat() / maxOf(1, sessionsList.size)) * 0.30f) * progressScale
                updateProgress(prepProgress, "Preparing ride data... (${(idx * 100 / maxOf(1, sessionsList.size))}%)")

                val sJson = JSONObject().apply {
                    put("id", s.id)
                    put("startTime", s.startTime)
                    put("endTime", s.endTime ?: JSONObject.NULL)
                    put("totalDistanceMeters", s.totalDistanceMeters)
                    put("maxSpeedKmh", s.maxSpeedKmh)
                    put("averageSpeedKmh", s.averageSpeedKmh)
                    put("maxLeanAngle", s.maxLeanAngle)
                    put("peakLatG", s.peakLatG)
                    put("flowRating", s.flowRating)
                    put("vehicleType", s.vehicleType)
                    put("deviceName", s.deviceName)
                    put("vehicleId", s.vehicleId ?: JSONObject.NULL)
                    put("isAutoRecorded", if (s.isAutoRecorded) 1 else 0)
                    put("connectionLabel", s.connectionLabel ?: JSONObject.NULL)
                    put("lastUpdated", s.lastUpdated)
                    put("isDeleted", if (s.isDeleted) 1 else 0)
                    put("isPublic", if (s.isPublic) 1 else 0)
                }
                sessionsArray.put(sJson)

                // Only upload location points for non-deleted sessions
                if (!s.isDeleted) {
                    val locList = db.rideDao().getLocationsForSession(s.id).first()
                    locList.forEach { l ->
                        val lJson = JSONObject().apply {
                            put("id", l.id)
                            put("sessionId", l.sessionId)
                            put("latitude", l.latitude)
                            put("longitude", l.longitude)
                            put("altitude", l.altitude)
                            put("speedMetersPerSec", l.speedMetersPerSec)
                            put("leanAngle", l.leanAngle)
                            put("gForce", l.gForce)
                            put("accelLateral", l.accelLateral)
                            put("accelLongitudinal", l.accelLongitudinal)
                            put("timestamp", l.timestamp)
                        }
                        locationsArray.put(lJson)
                    }
                }
            }

            payload.put("sessions", sessionsArray)
            payload.put("locationPoints", locationsArray)

            // Performance Runs (including deleted tombstones)
            val unsyncedRuns = db.performanceRunDao().getUnsyncedRuns()
            val runsArray = JSONArray()
            unsyncedRuns.forEach { run ->
                val rJson = JSONObject().apply {
                    put("id", run.id)
                    put("vehicleId", run.vehicleId ?: JSONObject.NULL)
                    put("vehicleName", run.vehicleName)
                    put("vehicleType", run.vehicleType)
                    put("timestamp", run.timestamp)
                    put("accel0To60Mph", run.accel0To60Mph?.toDouble() ?: JSONObject.NULL)
                    put("accel0To100Kmh", run.accel0To100Kmh?.toDouble() ?: JSONObject.NULL)
                    put("accel60To130Mph", run.accel60To130Mph?.toDouble() ?: JSONObject.NULL)
                    put("accel100To200Kmh", run.accel100To200Kmh?.toDouble() ?: JSONObject.NULL)
                    put("accel0To130Mph", run.accel0To130Mph?.toDouble() ?: JSONObject.NULL)
                    put("accel0To300Kmh", run.accel0To300Kmh?.toDouble() ?: JSONObject.NULL)
                    put("drag1_8Time", run.drag1_8Time?.toDouble() ?: JSONObject.NULL)
                    put("drag1_8TrapSpeedKmh", run.drag1_8TrapSpeedKmh?.toDouble() ?: JSONObject.NULL)
                    put("drag1_4Time", run.drag1_4Time?.toDouble() ?: JSONObject.NULL)
                    put("drag1_4TrapSpeedKmh", run.drag1_4TrapSpeedKmh?.toDouble() ?: JSONObject.NULL)
                    put("drag1_2Time", run.drag1_2Time?.toDouble() ?: JSONObject.NULL)
                    put("drag1_2TrapSpeedKmh", run.drag1_2TrapSpeedKmh?.toDouble() ?: JSONObject.NULL)
                    put("brakingStartSpeedKmh", run.brakingStartSpeedKmh?.toDouble() ?: JSONObject.NULL)
                    put("brakingTime", run.brakingTime?.toDouble() ?: JSONObject.NULL)
                    put("brakingDistanceMeters", run.brakingDistanceMeters?.toDouble() ?: JSONObject.NULL)
                    put("peakLateralG", run.peakLateralG.toDouble())
                    put("peakLongitudinalG", run.peakLongitudinalG.toDouble())
                    put("lapTimesJson", run.lapTimesJson)
                    put("videoPath", run.videoPath ?: JSONObject.NULL)
                    put("telemetryJson", run.telemetryJson)
                    put("lastUpdated", run.lastUpdated)
                    put("isDeleted", if (run.isDeleted) 1 else 0)
                    put("profileId", run.profileId)
                    put("deviceId", run.deviceId)
                }
                runsArray.put(rJson)
            }
            payload.put("runs", runsArray)

            // Send to cloud
            val url = URL(NetworkConfig.rideTrackerSyncUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val payloadBytes = payload.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val totalBytes = payloadBytes.size
            connection.setFixedLengthStreamingMode(totalBytes)

            updateProgress(progressOffset + 0.60f * progressScale, "Uploading data... (0%)")

            val os = connection.outputStream
            var bytesWritten = 0
            val chunkSize = 16384
            while (bytesWritten < totalBytes) {
                val lengthToWrite = minOf(chunkSize, totalBytes - bytesWritten)
                os.write(payloadBytes, bytesWritten, lengthToWrite)
                bytesWritten += lengthToWrite
                
                val uploadProgress = bytesWritten.toFloat() / totalBytes
                val clamped = uploadProgress.coerceIn(0f, 1f)
                updateProgress(
                    progressOffset + (0.60f + clamped * 0.35f) * progressScale,
                    "Uploading data... (${(clamped * 100).toInt()}%)"
                )
            }
            os.flush()
            os.close()

            updateProgress(progressOffset + 0.95f * progressScale, "Waiting for server response...")
            val code = connection.responseCode
            connection.disconnect()

            if (code == HttpURLConnection.HTTP_OK) {
                Log.d("RideTrackerSync", "Sync successful")
                if (unsyncedRuns.isNotEmpty()) {
                    db.performanceRunDao().markSynced(unsyncedRuns.map { it.id })
                }
                return@withContext Pair(true, null)
            } else {
                Log.e("RideTrackerSync", "Sync failed with code: $code")
                return@withContext Pair(false, "Server returned HTTP $code")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RideTrackerSync", "Exception during sync: ${e.message}")
            return@withContext Pair(false, e.message ?: "Unknown error")
        }
    }

    private suspend fun fetchFromCloudInternal(
        context: Context,
        progressOffset: Float,
        progressScale: Float
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileRepository.activeProfile.value.id
            val lastSyncTime = getSavedLastSyncTime(context)
            val url = URL("${NetworkConfig.rideTrackerSyncUrl}?profileId=$profileId&since=$lastSyncTime")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            updateProgress(progressOffset + 0.05f * progressScale, "Downloading data...")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val contentLength = connection.contentLength
                val inputStream = connection.inputStream
                val bos = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                var totalBytesRead = 0
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bos.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val downloadProgress = totalBytesRead.toFloat() / contentLength
                        val clamped = downloadProgress.coerceIn(0f, 1f)
                        updateProgress(
                            progressOffset + (0.05f + clamped * 0.40f) * progressScale,
                            "Downloading data... (${(clamped * 100).toInt()}%)"
                        )
                    } else {
                        val kbDownloaded = totalBytesRead / 1024
                        updateProgress(
                            progressOffset + 0.25f * progressScale,
                            "Downloading data... (${kbDownloaded} KB)"
                        )
                    }
                }
                inputStream.close()
                val responseString = bos.toString("UTF-8")
                connection.disconnect()

                updateProgress(progressOffset + 0.50f * progressScale, "Parsing download response...")
                val json = JSONObject(responseString)
                if (json.optBoolean("success", false)) {
                    val db = RideDatabase.getDatabase(context)

                    // Parse settings
                    if (!json.isNull("settings")) {
                        val settings = json.getJSONObject("settings")
                        val manager = RideSettingsManager.getInstance(context)
                        manager.updateSettingsFromJson(settings)
                    }

                    // Parse vehicles with LWW
                    if (!json.isNull("vehicles")) {
                        val vehicles = json.getJSONArray("vehicles")
                        val vehiclesToInsert = mutableListOf<VehicleEntity>()
                        for (i in 0 until vehicles.length()) {
                            val v = vehicles.getJSONObject(i)
                            val cloudLastUpdated = v.optLong("lastUpdated", 0L)
                            val localLastUpdated = db.vehicleDao().getVehicleLastUpdated(v.getString("id")) ?: 0L
                            // Only overwrite if cloud data is newer
                            if (cloudLastUpdated >= localLastUpdated) {
                                val entity = VehicleEntity(
                                    id = v.getString("id"),
                                    name = v.optString("name"),
                                    type = v.optString("type"),
                                    make = v.optString("make"),
                                    model = v.optString("model"),
                                    year = v.optString("year"),
                                    licensePlate = v.optString("licensePlate"),
                                    vin = v.optString("vin"),
                                    insurancePolicyNumber = v.optString("insurancePolicyNumber"),
                                    insuranceDate = v.optLong("insuranceDate"),
                                    pucDate = v.optLong("pucDate"),
                                    nextServiceDate = v.optLong("nextServiceDate"),
                                    fuelType = v.optString("fuelType"),
                                    tankCapacity = v.optString("tankCapacity"),
                                    description = v.optString("description"),
                                    color = v.optString("color"),
                                    engineCC = v.optString("engineCC"),
                                    odometer = v.optString("odometer"),
                                    topSpeed = v.optString("topSpeed"),
                                    fuelRange = v.optString("fuelRange"),
                                    maintenanceDue = v.optString("maintenanceDue", "Checkup required"),
                                    isActive = v.optInt("isActive", 1) == 1,
                                    imageUrl = if (v.isNull("imageUrl")) "https://images.unsplash.com/photo-1558981806-ec527fa84c39?q=80&w=2070&auto=format&fit=crop" else v.optString("imageUrl"),
                                    originalImageUrl = if (v.isNull("originalImageUrl")) "" else v.optString("originalImageUrl"),
                                    statsAvgSpeed = v.optString("statsAvgSpeed", "0.0"),
                                    statsLeanAngle = v.optString("statsLeanAngle", "0.0"),
                                    connectedDevicesJson = v.optString("connectedDevicesJson", "[]"),
                                    tripHistoryPreviewCount = v.optInt("tripHistoryPreviewCount", 0),
                                    lastUpdated = cloudLastUpdated,
                                    isDeleted = v.optInt("isDeleted", 0) == 1,
                                    isOccasional = v.optInt("isOccasional", 0) == 1
                                )
                                vehiclesToInsert.add(entity)
                            }
                        }
                        if (vehiclesToInsert.isNotEmpty()) {
                            db.vehicleDao().insertVehicles(vehiclesToInsert)
                            // BT-Bug #4 fix: refresh SharedPrefs MAC cache so newly synced
                            // vehicles are immediately visible to BluetoothConnectionReceiver.
                            BluetoothMacUtils.refreshKnownMacsCache(context)
                        }
                    }

                    updateProgress(progressOffset + 0.60f * progressScale, "Saving sessions...")

                    // Parse sessions with LWW
                    if (!json.isNull("sessions")) {
                        val sessions = json.getJSONArray("sessions")
                        val sessionsToUpsert = mutableListOf<RideSession>()
                        for (i in 0 until sessions.length()) {
                            val s = sessions.getJSONObject(i)
                            val cloudLastUpdated = s.optLong("lastUpdated", 0L)
                            val localLastUpdated = db.rideDao().getSessionLastUpdated(s.getString("id")) ?: 0L
                            if (cloudLastUpdated >= localLastUpdated) {
                                 val entity = RideSession(
                                    id = s.getString("id"),
                                    startTime = s.optLong("startTime"),
                                    endTime = if (s.isNull("endTime")) null else s.optLong("endTime"),
                                    totalDistanceMeters = s.optDouble("totalDistanceMeters").toFloat(),
                                    maxSpeedKmh = s.optDouble("maxSpeedKmh").toFloat(),
                                    averageSpeedKmh = s.optDouble("averageSpeedKmh").toFloat(),
                                    maxLeanAngle = s.optDouble("maxLeanAngle").toFloat(),
                                    peakLatG = s.optDouble("peakLatG").toFloat(),
                                    flowRating = s.optInt("flowRating"),
                                    vehicleType = s.optString("vehicleType"),
                                    deviceName = s.optString("deviceName", "Unknown Device"),
                                    vehicleId = if (s.isNull("vehicleId")) null else s.optString("vehicleId"),
                                    isAutoRecorded = s.optInt("isAutoRecorded", 0) == 1,
                                    connectionLabel = if (s.isNull("connectionLabel")) null else s.optString("connectionLabel"),
                                    lastUpdated = cloudLastUpdated,
                                    isDeleted = s.optInt("isDeleted", 0) == 1,
                                    isPublic = s.optInt("isPublic", 1) == 1
                                )
                                sessionsToUpsert.add(entity)
                            }
                        }
                        if (sessionsToUpsert.isNotEmpty()) {
                            db.rideDao().upsertSessions(sessionsToUpsert)
                        }
                    }

                    // Parse performance runs with LWW conflict resolution
                    if (!json.isNull("runs")) {
                        val runs = json.getJSONArray("runs")
                        val runsToUpsert = mutableListOf<com.nexus.portal.data.local.ridetracker.PerformanceRunEntity>()
                        for (i in 0 until runs.length()) {
                            val r = runs.getJSONObject(i)
                            val cloudLastUpdated = r.optLong("lastUpdated", 0L)
                            val localLastUpdated = db.performanceRunDao().getRunLastUpdated(r.getString("id")) ?: 0L
                            if (cloudLastUpdated >= localLastUpdated) {
                                val entity = com.nexus.portal.data.local.ridetracker.PerformanceRunEntity(
                                    id = r.getString("id"),
                                    vehicleId = if (r.isNull("vehicleId")) null else r.optString("vehicleId"),
                                    vehicleName = r.optString("vehicleName", "Unknown Vehicle"),
                                    vehicleType = r.optString("vehicleType", "two_wheeler"),
                                    timestamp = r.optLong("timestamp"),
                                    accel0To60Mph = if (r.isNull("accel0To60Mph")) null else r.optDouble("accel0To60Mph").toFloat(),
                                    accel0To100Kmh = if (r.isNull("accel0To100Kmh")) null else r.optDouble("accel0To100Kmh").toFloat(),
                                    accel60To130Mph = if (r.isNull("accel60To130Mph")) null else r.optDouble("accel60To130Mph").toFloat(),
                                    accel100To200Kmh = if (r.isNull("accel100To200Kmh")) null else r.optDouble("accel100To200Kmh").toFloat(),
                                    accel0To130Mph = if (r.isNull("accel0To130Mph")) null else r.optDouble("accel0To130Mph").toFloat(),
                                    accel0To300Kmh = if (r.isNull("accel0To300Kmh")) null else r.optDouble("accel0To300Kmh").toFloat(),
                                    drag1_8Time = if (r.isNull("drag1_8Time")) null else r.optDouble("drag1_8Time").toFloat(),
                                    drag1_8TrapSpeedKmh = if (r.isNull("drag1_8TrapSpeedKmh")) null else r.optDouble("drag1_8TrapSpeedKmh").toFloat(),
                                    drag1_4Time = if (r.isNull("drag1_4Time")) null else r.optDouble("drag1_4Time").toFloat(),
                                    drag1_4TrapSpeedKmh = if (r.isNull("drag1_4TrapSpeedKmh")) null else r.optDouble("drag1_4TrapSpeedKmh").toFloat(),
                                    drag1_2Time = if (r.isNull("drag1_2Time")) null else r.optDouble("drag1_2Time").toFloat(),
                                    drag1_2TrapSpeedKmh = if (r.isNull("drag1_2TrapSpeedKmh")) null else r.optDouble("drag1_2TrapSpeedKmh").toFloat(),
                                    brakingStartSpeedKmh = if (r.isNull("brakingStartSpeedKmh")) null else r.optDouble("brakingStartSpeedKmh").toFloat(),
                                    brakingTime = if (r.isNull("brakingTime")) null else r.optDouble("brakingTime").toFloat(),
                                    brakingDistanceMeters = if (r.isNull("brakingDistanceMeters")) null else r.optDouble("brakingDistanceMeters").toFloat(),
                                    peakLateralG = r.optDouble("peakLateralG", 0.0).toFloat(),
                                    peakLongitudinalG = r.optDouble("peakLongitudinalG", 0.0).toFloat(),
                                    lapTimesJson = r.optString("lapTimesJson", "[]"),
                                    videoPath = if (r.isNull("videoPath")) null else r.optString("videoPath"),
                                    telemetryJson = r.optString("telemetryJson", "[]"),
                                    isSynced = true,
                                    lastUpdated = cloudLastUpdated,
                                    isDeleted = r.optInt("isDeleted", 0) == 1,
                                    profileId = r.optString("profileId", ""),
                                    deviceId = r.optString("deviceId", "")
                                )
                                runsToUpsert.add(entity)
                            }
                        }
                        if (runsToUpsert.isNotEmpty()) {
                            db.performanceRunDao().upsertRuns(runsToUpsert)
                        }
                    }

                    // Parse location points
                    if (!json.isNull("locationPoints")) {
                        val locs = json.getJSONArray("locationPoints")
                        Log.d("RideTrackerSync", "fetchFromCloud: locationPoints json array length = ${locs.length()}")
                        val locsToInsert = mutableListOf<RideLocationPoint>()
                        
                        updateProgress(progressOffset + 0.70f * progressScale, "Processing location data...")

                        for (i in 0 until locs.length()) {
                            val l = locs.getJSONObject(i)
                            val entity = RideLocationPoint(
                                id = l.getString("id"),
                                sessionId = l.getString("sessionId"),
                                latitude = l.optDouble("latitude"),
                                longitude = l.optDouble("longitude"),
                                altitude = l.optDouble("altitude"),
                                speedMetersPerSec = l.optDouble("speedMetersPerSec").toFloat(),
                                leanAngle = l.optDouble("leanAngle").toFloat(),
                                gForce = l.optDouble("gForce").toFloat(),
                                accelLateral = l.optDouble("accelLateral").toFloat(),
                                accelLongitudinal = l.optDouble("accelLongitudinal").toFloat(),
                                timestamp = l.optLong("timestamp")
                            )
                            locsToInsert.add(entity)

                            if (i % 2000 == 0 && locs.length() > 0) {
                                val parseProgress = i.toFloat() / locs.length()
                                val clamped = parseProgress.coerceIn(0f, 1f)
                                updateProgress(
                                    progressOffset + (0.70f + clamped * 0.10f) * progressScale,
                                    "Processing location data... (${(clamped * 100).toInt()}%)"
                                )
                            }
                        }
                        
                        if (locsToInsert.isNotEmpty()) {
                            Log.d("RideTrackerSync", "fetchFromCloud: inserting ${locsToInsert.size} location points into local database")
                            
                            val chunkSize = 500
                            val totalSize = locsToInsert.size
                            var insertedCount = 0
                            
                            while (insertedCount < totalSize) {
                                val nextChunk = locsToInsert.subList(insertedCount, minOf(insertedCount + chunkSize, totalSize))
                                db.rideDao().insertLocationPoints(nextChunk)
                                insertedCount += nextChunk.size
                                
                                val insertProgress = insertedCount.toFloat() / totalSize
                                val clamped = insertProgress.coerceIn(0f, 1f)
                                updateProgress(
                                    progressOffset + (0.80f + clamped * 0.20f) * progressScale,
                                    "Saving to database... (${(clamped * 100).toInt()}%)"
                                )
                            }
                        } else {
                            Log.d("RideTrackerSync", "fetchFromCloud: locsToInsert list is empty")
                        }
                    } else {
                        Log.d("RideTrackerSync", "fetchFromCloud: locationPoints is null or missing in server JSON response")
                    }

                    return@withContext Pair(true, null)
                } else {
                    return@withContext Pair(false, json.optString("message", "API returned success=false"))
                }
            } else {
                return@withContext Pair(false, "Server returned HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RideTrackerSync", "Exception during fetch: ${e.message}")
            return@withContext Pair(false, e.message ?: "Unknown error")
        }
    }

    private suspend fun uploadLocalImage(localPath: String, vehicleId: String): String {
        if (localPath.isBlank() || localPath.startsWith("http://") || localPath.startsWith("https://") || localPath.startsWith("/uploads/")) {
            return localPath
        }
        val file = java.io.File(localPath)
        if (!file.exists()) return localPath
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${NetworkConfig.backendUrl}/api/vehicles/upload-image")
                val conn = url.openConnection() as HttpURLConnection
                val boundary = "Boundary-" + System.currentTimeMillis()
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                
                val os = java.io.DataOutputStream(conn.outputStream)
                
                os.writeBytes("--$boundary\r\n")
                os.writeBytes("Content-Disposition: form-data; name=\"vehicleId\"\r\n\r\n")
                os.writeBytes(vehicleId + "\r\n")
                
                os.writeBytes("--$boundary\r\n")
                os.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"${file.name}\"\r\n")
                os.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                
                val fis = java.io.FileInputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
                fis.close()
                os.writeBytes("\r\n")
                os.writeBytes("--$boundary--\r\n")
                os.flush()
                os.close()
                
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    conn.disconnect()
                    val json = JSONObject(response.toString())
                    if (json.optBoolean("success", false) && json.has("imageUrl")) {
                        return@withContext json.getString("imageUrl")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("RideTrackerSync", "Exception during image upload: ${e.message}")
            }
            return@withContext localPath
        }
    }
}
