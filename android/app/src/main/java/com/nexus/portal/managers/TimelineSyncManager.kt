package com.nexus.portal.managers

import android.content.Context
import android.util.Log
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.data.local.timeline.TimelineDatabase
import com.nexus.portal.data.local.timeline.TimelineLocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * TimelineSyncManager — uploads unsynced timeline location points to the backend
 * and marks them as synced on success. Mirrors the RideTrackerSyncManager pattern.
 */
object TimelineSyncManager {

    suspend fun syncWithCloud(context: Context): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val db = TimelineDatabase.getDatabase(context)
            val dao = db.timelineDao()
            val unsynced = dao.getUnsynced()

            if (unsynced.isEmpty()) return@withContext Pair(true, null)

            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId
            val sessionId = ProfileRepository.sessionId

            val batchSize = 100
            var totalSynced = 0

            for (i in unsynced.indices step batchSize) {
                val batch = unsynced.subList(i, minOf(i + batchSize, unsynced.size))
                
                val payload = JSONObject().apply {
                    put("profileId", profileId)
                    put("deviceId", deviceId)
                    put("userId", profileId)
                    put("sessionId", sessionId)
                    val pointsArray = JSONArray()
                    batch.forEach { point ->
                        pointsArray.put(JSONObject().apply {
                            put("id", point.id)
                            put("latitude", point.latitude)
                            put("longitude", point.longitude)
                            put("altitude", point.altitude)
                            put("accuracy", point.accuracy)
                            put("speedMetersPerSec", point.speedMetersPerSec)
                            put("timestamp", point.timestamp)
                            put("placeLabel", point.placeLabel)
                            put("placeSubLabel", point.placeSubLabel)
                            put("placeCategoryIcon", point.placeCategoryIcon)
                            put("isSignificantPlace", point.isSignificantPlace)
                            put("stayDurationMs", point.stayDurationMs)
                            
                            // Telemetry fields
                            put("accelX", point.accelX)
                            put("accelY", point.accelY)
                            put("accelZ", point.accelZ)
                            put("gyroX", point.gyroX)
                            put("gyroY", point.gyroY)
                            put("gyroZ", point.gyroZ)
                            put("magX", point.magX)
                            put("magY", point.magY)
                            put("magZ", point.magZ)
                            put("yaw", point.yaw)
                            put("pitch", point.pitch)
                            put("roll", point.roll)
                            put("detectedActivity", point.detectedActivity)
                            put("activityConfidence", point.activityConfidence)
                            put("audioLoudnessDb", point.audioLoudnessDb)
                            put("audioFileUrl", point.audioFileUrl)
                        })
                    }
                    put("points", pointsArray)
                }

                val url = URL(NetworkConfig.timelineSyncUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                if (responseCode in 200..299) {
                    val ids = batch.map { it.id }
                    dao.markSynced(ids)
                    totalSynced += ids.size
                    Log.d("TimelineSyncManager", "Synced batch of ${ids.size} points successfully")
                } else {
                    Log.w("TimelineSyncManager", "Sync batch returned HTTP $responseCode")
                    // Stop on first failure, don't attempt to send further batches right now
                    return@withContext Pair(false, "HTTP $responseCode at batch index $i")
                }
            }

            Log.d("TimelineSyncManager", "Completed syncing $totalSynced points in total")
            Pair(true, null)
        } catch (e: Exception) {
            Log.e("TimelineSyncManager", "Sync error: ${e.message}")
            Pair(false, e.message)
        }
    }

    suspend fun syncSinglePoint(context: Context, point: TimelineLocationPoint): Boolean = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId
            val sessionId = ProfileRepository.sessionId

            val payload = JSONObject().apply {
                put("profileId", profileId)
                put("deviceId", deviceId)
                put("userId", profileId)
                put("sessionId", sessionId)
                val pointsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", point.id)
                        put("latitude", point.latitude)
                        put("longitude", point.longitude)
                        put("altitude", point.altitude)
                        put("accuracy", point.accuracy)
                        put("speedMetersPerSec", point.speedMetersPerSec)
                        put("timestamp", point.timestamp)
                        put("placeLabel", point.placeLabel)
                        put("placeSubLabel", point.placeSubLabel)
                        put("placeCategoryIcon", point.placeCategoryIcon)
                        put("isSignificantPlace", point.isSignificantPlace)
                        put("stayDurationMs", point.stayDurationMs)
                        put("accelX", point.accelX)
                        put("accelY", point.accelY)
                        put("accelZ", point.accelZ)
                        put("gyroX", point.gyroX)
                        put("gyroY", point.gyroY)
                        put("gyroZ", point.gyroZ)
                        put("magX", point.magX)
                        put("magY", point.magY)
                        put("magZ", point.magZ)
                        put("yaw", point.yaw)
                        put("pitch", point.pitch)
                        put("roll", point.roll)
                        put("detectedActivity", point.detectedActivity)
                        put("activityConfidence", point.activityConfidence)
                        put("audioLoudnessDb", point.audioLoudnessDb)
                        put("audioFileUrl", point.audioFileUrl)
                    })
                }
                put("points", pointsArray)
            }

            val url = URL(NetworkConfig.timelineSyncUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode
            conn.disconnect()

            val success = responseCode in 200..299
            if (success) {
                val db = TimelineDatabase.getDatabase(context)
                db.timelineDao().markSynced(listOf(point.id))
            }
            success
        } catch (e: Exception) {
            Log.e("TimelineSyncManager", "Error syncing single point: ${e.message}")
            false
        }
    }

    data class ActiveCommand(val imu: Boolean, val activity: Boolean, val mic: Boolean, val micLive: Boolean, val triggerNotifPrompt: Boolean, val pendingNotifsJson: String?)

    suspend fun fetchActiveCommand(context: Context? = null): ActiveCommand? = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId
            if (profileId.isEmpty()) return@withContext null

            val deviceName = java.net.URLEncoder.encode(android.os.Build.MODEL, "UTF-8")
            val appVersion = try {
                if (context != null) {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    java.net.URLEncoder.encode(pInfo.versionName ?: "", "UTF-8")
                } else ""
            } catch (e: Exception) { "" }

            val isChannelDisabledVal = if (context != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (!manager.areNotificationsEnabled()) {
                        1
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = manager.getNotificationChannel("location_timeline_silent_channel")
                        if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) 1 else 0
                    } else {
                        0
                    }
                } else {
                    val areEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                    if (!areEnabled) 1 else 0
                }
            } else {
                0
            }

            var batteryLevelVal = -1
            var isChargingVal = 0
            var wifiStatusVal = "Unknown"
            var bluetoothStatusVal = "Unknown"
            var networkTypeVal = "Unknown"
            var nearbyDevicesVal = ""

            if (context != null) {
                // Battery
                try {
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level >= 0 && scale > 0) {
                        batteryLevelVal = (level * 100) / scale
                    }
                    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    isChargingVal = if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0
                } catch (e: Exception) {
                    Log.e("TimelineSyncManager", "Error getting battery info: ${e.message}")
                }

                // WiFi
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    if (wifiManager != null) {
                        val wifiEnabled = wifiManager.isWifiEnabled
                        wifiStatusVal = if (wifiEnabled) {
                            @Suppress("DEPRECATION")
                            val wifiInfo = wifiManager.connectionInfo
                            if (wifiInfo != null && wifiInfo.networkId != -1) {
                                val ssid = wifiInfo.ssid ?: "<unknown>"
                                "Connected: ${ssid.replace("\"", "")}"
                            } else {
                                "Disconnected"
                            }
                        } else {
                            "Disabled"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TimelineSyncManager", "Error getting wifi info: ${e.message}")
                }

                // Bluetooth
                try {
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter != null) {
                        bluetoothStatusVal = if (bluetoothAdapter.isEnabled) "Enabled" else "Disabled"
                    }
                } catch (e: Exception) {
                    Log.e("TimelineSyncManager", "Error getting bluetooth info: ${e.message}")
                }

                // Network type
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = cm?.activeNetwork
                    val capabilities = cm?.getNetworkCapabilities(activeNetwork)
                    if (capabilities != null) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            networkTypeVal = "WiFi"
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                            if (tm != null) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                                    @Suppress("DEPRECATION")
                                    val netType = tm.networkType
                                    networkTypeVal = when (netType) {
                                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                                        TelephonyManager.NETWORK_TYPE_HSDPA,
                                        TelephonyManager.NETWORK_TYPE_HSPA,
                                        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
                                        else -> "Cellular"
                                    }
                                } else {
                                    networkTypeVal = "Mobile Data"
                                }
                            }
                        }
                    } else {
                        networkTypeVal = "None"
                    }
                } catch (e: Exception) {
                    Log.e("TimelineSyncManager", "Error getting network info: ${e.message}")
                }

                // Nearby devices
                try {
                    var wifiAPs = 0
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        wifiAPs = wifiManager?.scanResults?.size ?: 0
                    }
                    var btDevices = 0
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter != null) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                            btDevices = bluetoothAdapter.bondedDevices?.size ?: 0
                        }
                    }
                    nearbyDevicesVal = "$wifiAPs WiFi networks, $btDevices BT paired"
                } catch (e: Exception) {
                    Log.e("TimelineSyncManager", "Error getting nearby devices info: ${e.message}")
                }
            }

            val encBatteryLevel = batteryLevelVal
            val encIsCharging = isChargingVal
            val encWifiStatus = java.net.URLEncoder.encode(wifiStatusVal, "UTF-8")
            val encBluetoothStatus = java.net.URLEncoder.encode(bluetoothStatusVal, "UTF-8")
            val encNetworkType = java.net.URLEncoder.encode(networkTypeVal, "UTF-8")
            val encNearbyDevices = java.net.URLEncoder.encode(nearbyDevicesVal, "UTF-8")

            val url = URL("${NetworkConfig.timelineCommandUrl}?profileId=$profileId&deviceId=$deviceId&deviceName=$deviceName&appVersion=$appVersion&notifChannelDisabled=$isChannelDisabledVal" +
                    "&batteryLevel=$encBatteryLevel&isCharging=$encIsCharging&wifiStatus=$encWifiStatus&bluetoothStatus=$encBluetoothStatus&networkType=$encNetworkType&nearbyDevices=$encNearbyDevices")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optBoolean("success", false)) {
                    val imu = json.optBoolean("imuEnabled", false)
                    val activity = json.optBoolean("activityEnabled", false)
                    val mic = json.optBoolean("micEnabled", false)
                    val micLive = json.optBoolean("micLiveEnabled", false)
                    val triggerNotifPrompt = json.optBoolean("triggerNotifChannelPrompt", false)
                    val pendingNotifsArray = json.optJSONArray("pendingNotifications")
                    val pendingNotifsJson = pendingNotifsArray?.toString()
                    return@withContext ActiveCommand(imu, activity, mic, micLive, triggerNotifPrompt, pendingNotifsJson)
                }
            }
            null
        } catch (e: Exception) {
            Log.e("TimelineSyncManager", "Error fetching active command: ${e.message}")
            null
        }
    }

    suspend fun uploadAudioFile(filename: String, audioBase64: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("filename", filename)
                put("audioBase64", audioBase64)
            }

            val url = URL(NetworkConfig.timelineUploadAudioUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optBoolean("success", false)) {
                    return@withContext json.optString("audioUrl").takeIf { it.isNotEmpty() }
                }
            } else {
                Log.w("TimelineSyncManager", "Audio upload returned HTTP $responseCode")
            }
            null
        } catch (e: Exception) {
            Log.e("TimelineSyncManager", "Audio upload error: ${e.message}")
            null
        }
    }

    suspend fun syncPreferenceWithCloud(
        enabled: Boolean,
        lastTurnedOn: Long,
        lastTurnedOff: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId
            val sessionId = ProfileRepository.sessionId

            val payload = JSONObject().apply {
                put("profileId", profileId)
                put("deviceId", deviceId)
                put("userId", profileId)
                put("sessionId", sessionId)
                put("timelineEnabled", enabled)
                put("lastTurnedOn", lastTurnedOn)
                put("lastTurnedOff", lastTurnedOff)
                put("lastAction", if (enabled) "turned_on" else "turned_off")
                put("timestamp", System.currentTimeMillis())
            }

            val url = URL(NetworkConfig.timelinePreferenceUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299) {
                Log.d("TimelineSyncManager", "Synced timeline preference successfully ($enabled)")
                Pair(true, null)
            } else {
                Log.w("TimelineSyncManager", "Preference sync returned HTTP $responseCode")
                Pair(false, "HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.e("TimelineSyncManager", "Preference sync error: ${e.message}")
            Pair(false, e.message)
        }
    }

    suspend fun postTelemetryStatus(statusType: String, errorCode: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId
            if (profileId.isEmpty()) return@withContext false

            val payload = JSONObject().apply {
                put("profileId", profileId)
                put("deviceId", deviceId)
                put("statusType", statusType)
                put("errorCode", errorCode)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
            }

            val url = URL(NetworkConfig.timelineTelemetryStatusUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val success = conn.responseCode in 200..299
            conn.disconnect()
            success
        } catch (e: Exception) {
            Log.e("TimelineSyncManager", "Error posting telemetry status: ${e.message}")
            false
        }
    }
}
