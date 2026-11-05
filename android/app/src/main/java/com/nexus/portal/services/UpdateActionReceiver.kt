package com.nexus.portal.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.UUID

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val downloadPrefs = context.getSharedPreferences("nexus_download_prefs", Context.MODE_PRIVATE)
        val versionName = downloadPrefs.getString("active_download_version", "Unknown") ?: "Unknown"

        val workManager = WorkManager.getInstance(context)
        val savedWorkIdStr = downloadPrefs.getString("active_work_id", null)
        val workId = try {
            if (savedWorkIdStr != null) UUID.fromString(savedWorkIdStr) else null
        } catch (e: Exception) {
            null
        }

        when (action) {
            "com.nexus.portal.action.PAUSE_DOWNLOAD" -> {
                downloadPrefs.edit()
                    .putBoolean("download_paused_$versionName", true)
                    .putString("status_$versionName", "PAUSED")
                    .apply()
                workId?.let { workManager.cancelWorkById(it) }
            }
            "com.nexus.portal.action.RESUME_DOWNLOAD" -> {
                val url = downloadPrefs.getString("active_download_url", "") ?: ""
                val notes = downloadPrefs.getString("active_download_release_notes", "") ?: ""
                if (url.isNotEmpty()) {
                    downloadPrefs.edit()
                        .putBoolean("download_paused_$versionName", false)
                        .putString("status_$versionName", "DOWNLOADING")
                        .apply()

                    val request = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
                        .setInputData(workDataOf(
                            "update_url" to url,
                            "version_name" to versionName,
                            "release_notes" to notes
                        ))
                        .build()
                    downloadPrefs.edit().putString("active_work_id", request.id.toString()).apply()
                    workManager.enqueue(request)
                }
            }
            "com.nexus.portal.action.CANCEL_DOWNLOAD" -> {
                downloadPrefs.edit()
                    .putBoolean("download_paused_$versionName", false)
                    .remove("active_download_version")
                    .remove("status_$versionName")
                    .remove("progress_$versionName")
                    .remove("downloadedMB_$versionName")
                    .remove("totalSizeMB_$versionName")
                    .remove("active_work_id")
                    .apply()

                workId?.let { workManager.cancelWorkById(it) }

                // Delete temporary file
                val downloadDir = context.externalCacheDir ?: context.cacheDir
                val safeVer = versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val tempFile = File(downloadDir, "update_${safeVer}_temp.apk")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(BackgroundUpdateWorker.NOTIFICATION_ID)
            }
        }
    }
}
