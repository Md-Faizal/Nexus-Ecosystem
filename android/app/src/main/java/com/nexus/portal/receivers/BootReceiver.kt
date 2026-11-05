package com.nexus.portal.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryMonitorService
import com.nexus.portal.services.LocationTimelineService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Enable location timeline on boot (default: always on unless user disables)
            val timelinePrefs = context.getSharedPreferences(LocationTimelineService.PREFS_NAME, Context.MODE_PRIVATE)
            if (!timelinePrefs.contains(LocationTimelineService.KEY_ENABLED)) {
                timelinePrefs.edit().putBoolean(LocationTimelineService.KEY_ENABLED, true).apply()
            }
            val isTimelineEnabled = timelinePrefs.getBoolean(LocationTimelineService.KEY_ENABLED, true)
            
            // Enable notification sniffer on boot (default: always on unless user disables)
            val secretPrefs = context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
            if (!secretPrefs.contains("notification_sniffer_enabled")) {
                secretPrefs.edit().putBoolean("notification_sniffer_enabled", true).apply()
            }
            if (!secretPrefs.contains("show_timeline_recording_notif")) {
                secretPrefs.edit().putBoolean("show_timeline_recording_notif", false).apply()
            }
            
            val hasLocationPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                  ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (isTimelineEnabled && hasLocationPerm) {
                try {
                    val timelineIntent = Intent(context, LocationTimelineService::class.java).apply {
                        action = LocationTimelineService.ACTION_START
                    }
                    ContextCompat.startForegroundService(context, timelineIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            val prefs = context.getSharedPreferences("battery_monitor_prefs", Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("is_battery_monitor_active", false)
            
            if (isActive) {
                try {
                    val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            try {
                com.nexus.portal.services.RideTrackerService.checkAndStartAutoDetectIfConnected(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
