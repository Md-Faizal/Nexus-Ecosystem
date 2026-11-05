package com.nexus.portal.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var batteryTextView: TextView
    private lateinit var ramTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSystemStats()
            handler.postDelayed(this, 3000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = (level.toFloat() / scale.toFloat() * 100).toInt()
            batteryTextView.text = "BAT: $batteryPct%"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        startForeground(2002, createForegroundNotification())
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Dynamically build clean overlay pill rather than uninflated layout XML files
        overlayView = View(this).apply {
            setBackgroundColor(0xCC12121A.toInt())
        }
        
        val inflater = LayoutInflater.from(this)
        // Set up overlay view programmatically for absolute safety
        val displayLayout = inflater.inflate(android.R.layout.simple_list_item_2, null)
        overlayView = displayLayout
        
        batteryTextView = displayLayout.findViewById(android.R.id.text1)
        ramTextView = displayLayout.findViewById(android.R.id.text2)
        
        batteryTextView.setTextColor(0xFF00FFCC.toInt())
        ramTextView.setTextColor(0xFF9933FF.toInt())
        batteryTextView.text = "BAT: --%"
        ramTextView.text = "RAM: --MB"
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
        
        // Custom touch draggable mechanics
        overlayView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })
        
        windowManager.addView(overlayView, layoutParams)
        
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.post(updateRunnable)
    }

    private fun updateSystemStats() {
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        val availMegs = memoryInfo.availMem / (1024L * 1024L)
        val totalMegs = memoryInfo.totalMem / (1024L * 1024L)
        val usedMegs = totalMegs - availMegs
        ramTextView.text = "RAM: $usedMegs / ${totalMegs}MB"
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Overlay HUD Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Overlay Active")
            .setContentText("Overlay telemetry service running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(batteryReceiver)
        windowManager.removeView(overlayView)
    }
}
