package com.nexus.portal.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Displays floating status overlays for auto-pause / auto-resume / connection events.
 *
 * NOTIF-Bug #3 fix: changed from START_NOT_STICKY → START_STICKY so the service stays
 * alive between messages. Each new message via onStartCommand simply updates the existing
 * overlayView text and resets the 4-second dismiss timer, eliminating the destroy →
 * re-create flicker that occurred when two messages arrived within 4 seconds.
 *
 * The service self-terminates 4 seconds after the last message via a Handler runnable.
 */
class AutoPauseOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val removeOverlayRunnable = Runnable {
        removeOverlay()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "auto_pause_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Pause Overlay",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ride Tracker")
            .setContentText("Displaying pause/resume status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2003, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2003, notification)
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("MESSAGE") ?: "Ride Paused"
        showOrUpdateOverlay(message)

        // Reset the auto-dismiss timer on every new message — prevents flicker from
        // destroy/re-create when two messages arrive within the 4-second window.
        handler.removeCallbacks(removeOverlayRunnable)
        handler.postDelayed(removeOverlayRunnable, 4000)

        // START_STICKY keeps the service alive so subsequent messages update the existing
        // overlayView rather than forcing a destroy → onCreate → re-create cycle.
        return START_STICKY
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun showOrUpdateOverlay(message: String) {
        val formattedMessage = when {
            message.contains("Paused", ignoreCase = true) -> "⏸  $message"
            message.contains("Resumed", ignoreCase = true) -> "⚡  $message"
            message.contains("restored", ignoreCase = true) || message.contains("Connected", ignoreCase = true) -> "📶  $message"
            message.contains("lost", ignoreCase = true) || message.contains("Disconnected", ignoreCase = true) -> "⚠️  $message"
            message.contains("Completed", ignoreCase = true) || message.contains("Saved", ignoreCase = true) -> "🏆  $message"
            message.contains("Cancelled", ignoreCase = true) || message.contains("Stopped", ignoreCase = true) -> "🛑  $message"
            message.contains("Manual", ignoreCase = true) -> "👤  $message"
            else -> "ℹ️  $message"
        }

        if (overlayView == null) {
            // First message — create the view and add it to the window manager.
            val padHorizontal = dpToPx(24f).toInt()
            val padVertical = dpToPx(14f).toInt()

            overlayView = TextView(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(24f)
                    setColor(Color.parseColor("#EE0D0D0F")) // Sleek glassmorphic dark slate
                    setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#4D00E5FF")) // Elegant cyan border glow
                }
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 150
                windowAnimations = android.R.style.Animation_Toast
            }

            try {
                windowManager.addView(overlayView, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Subsequent messages — just update text in-place; no flicker.
        overlayView?.text = formattedMessage
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(removeOverlayRunnable)
        removeOverlay()
    }
}
