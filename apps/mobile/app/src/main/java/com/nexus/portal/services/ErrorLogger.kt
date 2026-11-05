package com.nexus.portal.services

import android.content.Context
import android.provider.Settings
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object ErrorLogger {

    val sessionId: String = UUID.randomUUID().toString()

    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        } catch (e: Exception) {
            "unknown_device"
        }
    }

    fun getUserId(): String {
        return try {
            ProfileRepository.activeProfile.value.id
        } catch (e: Exception) {
            "guest_isolated"
        }
    }

    fun register(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Intercept crash synchronously
            logErrorSync(context, throwable, isFatal = true)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        // Asynchronously upload any cached crashes from previous runs
        sendPendingLogs(context)
    }

    private var lastLoggedTime = 0L
    private var lastErrorMessage: String? = null
    private var recentErrorCount = 0

    fun logError(context: Context, throwable: Throwable, isFatal: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            logErrorSync(context, throwable, isFatal)
        }
    }

    private fun logErrorSync(context: Context, throwable: Throwable, isFatal: Boolean) {
        try {
            val msg = throwable.message ?: throwable.toString()
            val now = System.currentTimeMillis()

            // Throttling: Skip identical error messages within 2 seconds
            if (msg == lastErrorMessage && now - lastLoggedTime < 2000) {
                return
            }

            // Rate limit: Max 20 errors per minute to avoid log flooding/OOM
            if (now - lastLoggedTime > 60000) {
                recentErrorCount = 0
            }
            if (recentErrorCount >= 20) {
                android.util.Log.w("ErrorLogger", "Error logging throttled (limit reached): $msg")
                return
            }

            recentErrorCount++
            lastLoggedTime = now
            lastErrorMessage = msg

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTraceStr = sw.toString()

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val timestamp = sdf.format(Date())

            val payload = JSONObject().apply {
                put("deviceId", getDeviceId(context))
                put("sessionId", sessionId)
                put("userId", getUserId())
                put("errorMessage", throwable.message ?: throwable.toString())
                put("stackTrace", stackTraceStr)
                put("isFatal", isFatal)
                put("timestamp", timestamp)
            }

            // Cache log file first
            val cacheDir = File(context.cacheDir, "error_logs").apply { mkdirs() }
            val logFile = File(cacheDir, "crash_${System.currentTimeMillis()}.json")
            FileOutputStream(logFile).use { fos ->
                fos.write(payload.toString().toByteArray())
            }

            // Attempt dynamic upload
            val success = uploadLogPayload(payload.toString())
            if (success) {
                logFile.delete() // Clean cache on success
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadLogPayload(jsonStr: String): Boolean {
        return try {
            val url = URL(NetworkConfig.errorsUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonStr)
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun sendPendingLogs(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheDir = File(context.cacheDir, "error_logs")
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    val files = cacheDir.listFiles() ?: return@launch
                    for (file in files) {
                        if (file.isFile && file.name.endsWith(".json")) {
                            val jsonStr = file.readText()
                            val success = uploadLogPayload(jsonStr)
                            if (success) {
                                file.delete()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
