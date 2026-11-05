package com.nexus.portal.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class BackgroundUpdateWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "update_channel"
        const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val downloadUrlStr = inputData.getString("update_url") ?: return Result.failure()
        val versionName = inputData.getString("version_name") ?: "Unknown"
        val releaseNotes = inputData.getString("release_notes") ?: "No release notes"
        
        val downloadDir = applicationContext.externalCacheDir ?: applicationContext.cacheDir
        val safeVersionName = versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val cacheFile = File(downloadDir, "update_${safeVersionName}_temp.apk")
        
        try {
            createNotificationChannel()
            
            // Promote to true foreground service to ensure it survives app closure
            setForeground(createForegroundInfo(0f, 0f, 0f, 0f, 0, 0))

            val busterUrl = if (downloadUrlStr.contains("?")) {
                "$downloadUrlStr&t=${System.currentTimeMillis()}"
            } else {
                "$downloadUrlStr?t=${System.currentTimeMillis()}"
            }
            val url = URL(busterUrl)
            
            val existingLength = if (cacheFile.exists()) cacheFile.length() else 0L
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (existingLength > 0L) {
                connection.setRequestProperty("Range", "bytes=$existingLength-")
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                notificationManager.cancel(NOTIFICATION_ID)
                return Result.failure()
            }

            val isPartial = (responseCode == HttpURLConnection.HTTP_PARTIAL)
            val fileLength = if (isPartial) {
                connection.contentLength + existingLength
            } else {
                connection.contentLength.toLong()
            }
            val totalSizeMB = if (fileLength > 0) fileLength.toFloat() / (1024 * 1024) else 0f

            val input = connection.inputStream
            val output = FileOutputStream(cacheFile, isPartial)

            val data = ByteArray(4096)
            var total: Long = if (isPartial) existingLength else 0L
            var count: Int
            
            var lastTime = System.currentTimeMillis()
            val startTime = System.currentTimeMillis()
            var lastTotal: Long = total
            var speedMBps = 0f
            var etaSecs = 0
            
            val downloadPrefs = applicationContext.getSharedPreferences("nexus_download_prefs", Context.MODE_PRIVATE)

            while (input.read(data).also { count = it } != -1) {
                if (isStopped) {
                    output.flush()
                    output.close()
                    input.close()
                    
                    val isPaused = downloadPrefs.getBoolean("download_paused_${versionName}", false)
                    if (isPaused) {
                        downloadPrefs.edit().putString("status_${versionName}", "PAUSED").apply()
                        val progressVal = total.toFloat() / fileLength
                        val downloadedMBVal = total.toFloat() / (1024 * 1024)
                        postPausedNotification(versionName, progressVal, downloadedMBVal, totalSizeMB)
                    } else {
                        cacheFile.delete()
                        downloadPrefs.edit()
                            .remove("active_download_version")
                            .remove("status_${versionName}")
                            .remove("progress_${versionName}")
                            .remove("downloadedMB_${versionName}")
                            .remove("totalSizeMB_${versionName}")
                            .remove("active_work_id")
                            .apply()
                        notificationManager.cancel(NOTIFICATION_ID)
                    }
                    return Result.failure()
                }

                total += count
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastTime

                if (timeDiff >= 1000) {
                    val bytesPerSec = (total - lastTotal) * 1000f / timeDiff
                    speedMBps = bytesPerSec / (1024 * 1024)
                    val remainingBytes = fileLength - total
                    etaSecs = if (bytesPerSec > 0) (remainingBytes / bytesPerSec).toInt() else 0
                    
                    lastTime = currentTime
                    lastTotal = total
                }

                if (fileLength > 0) {
                    val progress = total.toFloat() / fileLength
                    val downloadedMB = total.toFloat() / (1024 * 1024)
                    val elapsedSecs = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    setProgress(workDataOf(
                        "progress" to progress, 
                        "totalSizeMB" to totalSizeMB, 
                        "downloadedMB" to downloadedMB,
                        "speedMBps" to speedMBps,
                        "etaSecs" to etaSecs,
                        "elapsedSecs" to elapsedSecs
                    ))
                    if (timeDiff >= 1000 || total == fileLength) {
                        notificationManager.notify(NOTIFICATION_ID, createProgressNotification(progress, downloadedMB, totalSizeMB, speedMBps, etaSecs, elapsedSecs))
                    }
                    
                    downloadPrefs.edit()
                        .putFloat("progress_${versionName}", progress)
                        .putFloat("downloadedMB_${versionName}", downloadedMB)
                        .putFloat("totalSizeMB_${versionName}", totalSizeMB)
                        .putString("status_${versionName}", "DOWNLOADING")
                        .apply()
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            // Rename the temporary file to a unique final filename
            val finalFile = File(downloadDir, "update_${safeVersionName}_${System.currentTimeMillis()}.apk")
            if (cacheFile.exists()) {
                cacheFile.renameTo(finalFile)
            }

            // Cancel dynamic progress bar notification
            notificationManager.cancel(NOTIFICATION_ID)

            // Save APK version and update note metadata
            val metaPrefs = applicationContext.getSharedPreferences("nexus_apk_metadata", Context.MODE_PRIVATE)
            val metadataObj = JSONObject().apply {
                put("version", versionName)
                put("releaseNotes", releaseNotes)
            }
            metaPrefs.edit().putString(finalFile.name, metadataObj.toString()).apply()

            // Clean active download reference and set status to completed
            downloadPrefs.edit()
                .putString("status_${versionName}", "COMPLETED")
                .remove("active_download_version")
                .apply()

            // Download completed: Post "Tap to Install" notification
            postInstallationNotification(finalFile)

            // Download completed, launch user installation (foreground safe launch)
            launchInstaller(finalFile)
            
            return Result.success()

        } catch (e: Exception) {
            notificationManager.cancel(NOTIFICATION_ID)
            ErrorLogger.logError(applicationContext, e)
            val downloadPrefs = applicationContext.getSharedPreferences("nexus_download_prefs", Context.MODE_PRIVATE)
            downloadPrefs.edit().putString("status_${versionName}", "FAILED").apply()
            postFailedNotification(versionName)
            return Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Float, downloadedMB: Float, totalSizeMB: Float, speedMBps: Float, etaSecs: Int, elapsedSecs: Int): ForegroundInfo {
        val notification = createProgressNotification(progress, downloadedMB, totalSizeMB, speedMBps, etaSecs, elapsedSecs)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createProgressNotification(progress: Float, downloadedMB: Float, totalSizeMB: Float, speedMBps: Float, etaSecs: Int, elapsedSecs: Int): android.app.Notification {
        val progressPercent = (progress * 100).toInt()
        val text = if (totalSizeMB > 0) {
            String.format("Status: %d%% (%.2f / %.2f MB) @ %.2f MB/s - %ds elapsed, %ds left", progressPercent, downloadedMB, totalSizeMB, speedMBps, elapsedSecs, etaSecs)
        } else {
            "Status: $progressPercent% completed"
        }
        
        val pauseIntent = Intent(applicationContext, UpdateActionReceiver::class.java).apply {
            action = "com.nexus.portal.action.PAUSE_DOWNLOAD"
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            3001,
            pauseIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val cancelIntent = Intent(applicationContext, UpdateActionReceiver::class.java).apply {
            action = "com.nexus.portal.action.CANCEL_DOWNLOAD"
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            3002,
            cancelIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading System Update")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun postPausedNotification(versionName: String, progress: Float, downloadedMB: Float, totalSizeMB: Float) {
        val progressPercent = (progress * 100).toInt()
        val text = String.format("Paused: %d%% (%.2f / %.2f MB)", progressPercent, downloadedMB, totalSizeMB)
        
        val resumeIntent = Intent(applicationContext, UpdateActionReceiver::class.java).apply {
            action = "com.nexus.portal.action.RESUME_DOWNLOAD"
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            3003,
            resumeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val cancelIntent = Intent(applicationContext, UpdateActionReceiver::class.java).apply {
            action = "com.nexus.portal.action.CANCEL_DOWNLOAD"
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            3002,
            cancelIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Update v$versionName Paused")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setProgress(100, progressPercent, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun postFailedNotification(versionName: String) {
        val downloadPrefs = applicationContext.getSharedPreferences("nexus_download_prefs", Context.MODE_PRIVATE)
        val progress = downloadPrefs.getFloat("progress_$versionName", 0f)
        val downloadedMB = downloadPrefs.getFloat("downloadedMB_$versionName", 0f)
        val totalSizeMB = downloadPrefs.getFloat("totalSizeMB_$versionName", 0f)
        val progressPercent = (progress * 100).toInt()
        
        val resumeIntent = Intent(applicationContext, UpdateActionReceiver::class.java).apply {
            action = "com.nexus.portal.action.RESUME_DOWNLOAD"
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            3003,
            resumeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val cancelIntent = Intent(applicationContext, UpdateActionReceiver::class.java).apply {
            action = "com.nexus.portal.action.CANCEL_DOWNLOAD"
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            3002,
            cancelIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val text = if (totalSizeMB > 0f) {
            String.format("Connection lost: %.1f / %.1f MB. Tap Resume.", downloadedMB, totalSizeMB)
        } else {
            "Connection lost. Tap Resume to retry."
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Update v$versionName Failed")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setProgress(100, progressPercent, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background System Updates",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun postInstallationNotification(file: File) {
        val context = applicationContext
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "com.nexus.portal.fileprovider",
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            flag
        )

        val completionNotif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("System Update Ready")
            .setContentText("Download completed. Tap to install.")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, completionNotif)
    }

    private fun launchInstaller(file: File) {
        try {
            val context = applicationContext
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "com.nexus.portal.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            ErrorLogger.logError(applicationContext, e)
        }
    }
}
