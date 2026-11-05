package com.nexus.portal.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nexus.portal.services.LocationTimelineService
import com.nexus.portal.managers.TimelineSyncManager
import kotlinx.coroutines.launch
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.components.LiquidSwitch
import com.nexus.portal.ui.components.ScrollContainerContent
import com.nexus.portal.ui.theme.PortalTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.nexus.portal.ui.theme.LocalBackgroundBackdrop
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretSettingsScreen(
    activity: FragmentActivity,
    onBack: () -> Unit
) {
    val colors = PortalTheme.colors
    val primaryColor = colors.primary
    val scrollState = rememberScrollState()

    // Get shared preferences
    val prefs = activity.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
    val timelinePrefs = remember {
        activity.getSharedPreferences(LocationTimelineService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val coroutineScope = rememberCoroutineScope()
    
    var notificationSnifferEnabled by remember { 
        mutableStateOf(prefs.getBoolean("notification_sniffer_enabled", true))
    }
    
    var timelineEnabled by remember {
        mutableStateOf(timelinePrefs.getBoolean(LocationTimelineService.KEY_ENABLED, true))
    }
    
    var showTimelineRecordingNotif by remember {
        mutableStateOf(prefs.getBoolean("show_timeline_recording_notif", false))
    }

    var showDebugBorder by remember {
        mutableStateOf(prefs.getBoolean("show_debug_border", false))
    }

    val secretBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .layerBackdrop(secretBackdrop)
        ) {
            TechGridBackground()
        }

        CompositionLocalProvider(LocalBackgroundBackdrop provides secretBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
            // Header with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.nexus.portal.ui.components.GlassmorphicSurface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onBack() },
                    shape = RoundedCornerShape(10.dp),
                    cornerRadius = 10.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "SECRET DEVELOPER SETTINGS",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // System Notifications Sniffer Toggle
            Text(
                text = "SYSTEM TELEMETRY CAPTURE",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ScrollContainerContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Sniffer [ENABLED BY DEFAULT]",
                            color = Color(0xFF4FFF4F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "System notification capture runs in background. Toggle OFF to disable (will persist until manually re-enabled here)",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    LiquidSwitch(
                        checked = notificationSnifferEnabled,
                        onCheckedChange = { newValue ->
                            notificationSnifferEnabled = newValue
                            prefs.edit().putBoolean("notification_sniffer_enabled", newValue).apply()
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timeline Recording Toggles
            Text(
                text = "LOCATION TIMELINE TELEMETRY",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Toggle 1: Timeline Recording
            ScrollContainerContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Timeline Recording [ENABLED BY DEFAULT]",
                            color = Color(0xFF4FFF4F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Location tracking runs persistently in the background. If it crashes or is killed, it automatically restarts. Toggle OFF to completely disable.",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    LiquidSwitch(
                        checked = timelineEnabled,
                        onCheckedChange = { newValue ->
                            timelineEnabled = newValue
                            val now = System.currentTimeMillis()
                            val editor = timelinePrefs.edit().putBoolean(LocationTimelineService.KEY_ENABLED, newValue)
                            if (newValue) {
                                editor.putLong("last_turned_on", now)
                            } else {
                                editor.putLong("last_turned_off", now)
                            }
                            editor.apply()

                            // Start or stop service based on newValue
                            val intent = Intent(activity, LocationTimelineService::class.java).apply {
                                action = if (newValue) LocationTimelineService.ACTION_START else LocationTimelineService.ACTION_STOP
                            }
                            try {
                                if (newValue) {
                                    ContextCompat.startForegroundService(activity, intent)
                                } else {
                                    activity.stopService(intent)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // Sync setting change with the cloud backend
                            coroutineScope.launch {
                                val lastOn = timelinePrefs.getLong("last_turned_on", 0L)
                                val lastOff = timelinePrefs.getLong("last_turned_off", 0L)
                                TimelineSyncManager.syncPreferenceWithCloud(newValue, lastOn, lastOff)
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle 2: Timeline Service Notification
            ScrollContainerContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service Notification [DISABLED BY DEFAULT]",
                            color = primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Display a persistent notification in the status bar while the location timeline service is active. Toggle OFF to open Android settings where you can fully disable the notification channel.",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    LiquidSwitch(
                        checked = showTimelineRecordingNotif,
                        onCheckedChange = { newValue ->
                            showTimelineRecordingNotif = newValue
                            prefs.edit().putBoolean("show_timeline_recording_notif", newValue).apply()

                            // Restart the service (if enabled/running) to update notification status
                            if (timelineEnabled) {
                                try {
                                    val intent = Intent(activity, LocationTimelineService::class.java).apply {
                                        action = LocationTimelineService.ACTION_START
                                    }
                                    ContextCompat.startForegroundService(activity, intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // When turning OFF: open Android notification channel settings
                            // so user can fully disable the silent channel at OS level
                            if (!newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, "location_timeline_silent_channel")
                                    }
                                    activity.startActivity(channelIntent)
                                } catch (_: Exception) { }
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Visual Content Debug Border Toggle
            Text(
                text = "VISUAL INTERFACE BOUNDS",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ScrollContainerContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Visual Debug Borders",
                            color = primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Draws a crimson debug border outlining the page content container bounds to debug layouts and scroll offsets.",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    LiquidSwitch(
                        checked = showDebugBorder,
                        onCheckedChange = { newValue ->
                            showDebugBorder = newValue
                            prefs.edit().putBoolean("show_debug_border", newValue).apply()
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Info card
            ScrollContainerContent(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚠️ Developer Only - Always-On Background Services",
                        color = Color.Yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Notification sniffer and timeline tracking are ENABLED by default and run persistently in the background. If they crash or are killed by Android OS, they automatically restart. These services cannot be controlled from regular settings - only from this secret menu. Disabling them here will persist across app restarts and device reboots unless re-enabled manually.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        lineHeight = 16.sp
                    )
            }
        }
    }
}
}
}
