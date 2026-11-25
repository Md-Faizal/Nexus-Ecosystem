package com.nexus.portal.ui.screens.hub.apps.batterymonitor

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nexus.portal.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import android.app.AlarmManager
import android.os.SystemClock
import com.nexus.portal.receivers.ServiceRestarterReceiver

class BatteryMonitorService : Service() {

    private val CHANNEL_ID = "BatteryMonitorChannel"
    private val NOTIFICATION_ID = 1002
    
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var pollJob: Job? = null
    
    private lateinit var batteryManager: BatteryManager
    private lateinit var activityManager: ActivityManager

    // Session Tracking
    private var currentSessionId: String? = null
    private var currentSessionType: SessionType? = null
    private var sessionStartTime: Long = 0
    private var sessionStartLevel: Int = 0
    private var fullChargeTime: Long? = null
    
    // Notification Builder Cache
    private var notificationBuilder: NotificationCompat.Builder? = null
    
    // Live Battery Cache
    private var lastLevel: Int = -1
    private var lastTemp: Float = 0f
    private var lastPluggedStr: String = "On Battery"
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> startPolling()
                Intent.ACTION_SCREEN_OFF -> stopPolling()
            }
        }
    }
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                
                val currentLevel = if (scale > 0) (level * 100) / scale else 0
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                
                lastLevel = currentLevel
                lastTemp = temp
                lastPluggedStr = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB Charger"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "On Battery"
                }

                // Query and log mA once on battery status change broadcast
                val mA = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
                BatteryRepository.addPoint(mA)

                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (powerManager.isInteractive) {
                    updateNotification(mA)
                }

                handleSessionTransition(isCharging, currentLevel)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        BatteryRepository.init(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing...", "Starting telemetry..."))
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, screenFilter)
        
        // Schedule AlarmManager for background revival
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val restartIntent = Intent(this, ServiceRestarterReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 15 * 60 * 1000,
            15 * 60 * 1000,
            pendingIntent
        )
        
        startPolling()
    }
    
    private fun startPolling() {
        pollJob?.cancel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) return
        
        pollJob = scope.launch {
            while (isActive) {
                // Push Live Session Data to Repository
                if (currentSessionId != null && currentSessionType != null && lastLevel != -1) {
                    val activeEndTime = fullChargeTime ?: System.currentTimeMillis()
                    BatteryRepository.updateActiveSession(
                        BatterySession(
                            id = currentSessionId!!,
                            type = currentSessionType!!,
                            startPercent = sessionStartLevel,
                            endPercent = lastLevel,
                            startTime = sessionStartTime,
                            endTime = activeEndTime,
                            pluggedState = lastPluggedStr
                        )
                    )
                } else {
                    BatteryRepository.updateActiveSession(null)
                }

                // Poll current draw
                val mA = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
                BatteryRepository.addPoint(mA)
                
                updateNotification(mA)
                
                delay(30000) // Poll every 30 seconds when screen is ON
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun handleSessionTransition(isCharging: Boolean, level: Int) {
        val newType = if (isCharging) SessionType.CHARGE else SessionType.DISCHARGE
        
        if (newType == SessionType.CHARGE && level == 100 && fullChargeTime == null) {
            fullChargeTime = System.currentTimeMillis()
        }
        
        if (currentSessionType == null) {
            // Start first session
            startNewSession(newType, level)
            if (newType == SessionType.CHARGE && level == 100) fullChargeTime = System.currentTimeMillis()
        } else if (currentSessionType != newType) {
            // End old session
            endCurrentSession(level)
            // Start new session
            startNewSession(newType, level)
            if (newType == SessionType.CHARGE && level == 100) fullChargeTime = System.currentTimeMillis()
        }
    }
    
    private fun startNewSession(type: SessionType, level: Int) {
        currentSessionId = UUID.randomUUID().toString()
        currentSessionType = type
        sessionStartTime = System.currentTimeMillis()
        sessionStartLevel = level
        fullChargeTime = null
    }
    
    private fun endCurrentSession(endLevel: Int) {
        val id = currentSessionId ?: return
        val type = currentSessionType ?: return
        val endTime = fullChargeTime ?: System.currentTimeMillis()
        
        val session = BatterySession(
            id = id,
            type = type,
            startPercent = sessionStartLevel,
            endPercent = endLevel,
            startTime = sessionStartTime,
            endTime = endTime,
            pluggedState = lastPluggedStr
        )
        BatteryRepository.addSession(this, session)
    }

    private fun updateNotification(mA: Int) {
        val sign = if (mA > 0) "+" else ""
        
        val endTime = fullChargeTime ?: System.currentTimeMillis()
        val duration = endTime - sessionStartTime
        val durationStr = formatDuration(duration)
        val levelDiff = lastLevel - sessionStartLevel
        val levelSign = if (levelDiff > 0) "+" else ""
        
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val ramGb = memoryInfo.availMem / (1024f * 1024f * 1024f)
        
        val title = "$sign$mA mA  $levelSign$levelDiff%  $durationStr"
        val text = "Battery: ${"%.1f".format(lastTemp)}°C   RAM: ${"%.2f".format(ramGb)} GB"
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure foreground persistence if mistakenly dismissed
        val isActive = notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }
        if (!isActive) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(title, text))
            } catch (e: Exception) {
                // Ignore exception if unable to start foreground
            }
        }
        
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }
    
    private fun formatDuration(millis: Long): String {
        val totalSecs = millis / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
    }

    private fun buildNotification(title: String, text: String): Notification {
        if (notificationBuilder == null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.nexus.portal.ACTION_BATTERY_MONITOR"
                putExtra("navigate_to", "battery_monitor")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            
            notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging) // Placeholder
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }
        
        return notificationBuilder!!
            .setContentTitle(title)
            .setContentText(text)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the persistent battery monitoring engine"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pollJob?.cancel()
        
        // Save current open session before dying
        endCurrentSession(lastLevel)
        
        val prefs = getSharedPreferences("battery_monitor_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_battery_monitor_active", false)
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val restartIntent = Intent(this, ServiceRestarterReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE)
        
        if (!isActive) {
            alarmManager.cancel(pendingIntent)
        } else {
            // Aggressive revival, send broadcast immediately if killed by system
            sendBroadcast(restartIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
