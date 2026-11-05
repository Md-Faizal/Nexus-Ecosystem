package com.nexus.portal.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexus.portal.MainActivity
import com.nexus.portal.core.NetworkConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BackgroundUpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHECK_CHANNEL_ID = "update_check_channel"
        const val CHECK_NOTIFICATION_ID = 2002
    }

    override suspend fun doWork(): Result {
        try {
            val url = URL(NetworkConfig.updateCheckUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseData = JSONObject(response.toString())
                val sn = responseData.optString("versionName", "1.0.0")
                val notes = responseData.optString("releaseNotes", "")

                val currentVersionName = try {
                    val pInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                    pInfo.versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }

                if (isVersionNewer(currentVersionName, sn)) {
                    val prefs = applicationContext.getSharedPreferences("nexus_update_prefs", Context.MODE_PRIVATE)
                    val lastNotified = prefs.getString("last_notified_version", "")
                    val isScreenActive = try {
                        com.nexus.portal.ui.screens.isUpdateScreenActive
                    } catch (e: Exception) {
                        false
                    }

                    if (lastNotified != sn && !isScreenActive) {
                        postUpdateAvailableNotification(sn, notes)
                        prefs.edit().putString("last_notified_version", sn).apply()
                    }
                }
            }
            return Result.success()
        } catch (e: Exception) {
            ErrorLogger.logError(applicationContext, e)
            return Result.retry()
        }
    }

    private fun postUpdateAvailableNotification(versionName: String, notes: String) {
        createNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("navigate_to", "update")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1001,
            intent,
            flag
        )

        val notification = NotificationCompat.Builder(applicationContext, CHECK_CHANNEL_ID)
            .setContentTitle("New System Update Available")
            .setContentText("v$versionName is ready. Tap to update.")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Version v$versionName is available for deployment.\nRelease Notes: $notes"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(CHECK_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHECK_CHANNEL_ID,
                "System Update Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a new system update is available"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}

fun isVersionNewer(currentName: String, serverName: String): Boolean {
    try {
        val cleanCurrent = currentName.trim().lowercase().removePrefix("v").replace(Regex("[^0-9.]"), "")
        val cleanServer = serverName.trim().lowercase().removePrefix("v").replace(Regex("[^0-9.]"), "")
        
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val serverParts = cleanServer.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLen = maxOf(currentParts.size, serverParts.size)
        for (i in 0 until maxLen) {
            val currVal = currentParts.getOrNull(i) ?: 0
            val servVal = serverParts.getOrNull(i) ?: 0
            if (servVal > currVal) return true
            if (servVal < currVal) return false
        }
    } catch (e: Exception) {
    }
    
    return false
}
