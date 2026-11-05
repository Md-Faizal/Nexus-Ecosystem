package com.nexus.portal.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexus.portal.managers.RideTrackerSyncManager

class RideTrackerSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        Log.d("RideTrackerSyncWorker", "Periodic background sync started")
        return try {
            val (success, error) = RideTrackerSyncManager.performFullSync(applicationContext)
            if (success) {
                Log.d("RideTrackerSyncWorker", "Periodic background sync succeeded")
                Result.success()
            } else {
                Log.e("RideTrackerSyncWorker", "Periodic background sync failed: $error")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("RideTrackerSyncWorker", "Periodic background sync threw exception", e)
            Result.failure()
        }
    }
}
