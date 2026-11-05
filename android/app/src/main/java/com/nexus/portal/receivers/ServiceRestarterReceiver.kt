package com.nexus.portal.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryMonitorService

class ServiceRestarterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
    }
}
