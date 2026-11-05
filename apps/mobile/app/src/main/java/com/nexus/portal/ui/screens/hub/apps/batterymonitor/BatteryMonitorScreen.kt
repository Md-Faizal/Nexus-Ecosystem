package com.nexus.portal.ui.screens.hub.apps.batterymonitor

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryPoint
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryRepository
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatterySession
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.SessionType
import com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryMonitorService
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryMonitorScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var isHistoryView by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E24))
    ) {
        TechGridBackground()
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (isHistoryView) isHistoryView = false else onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHistoryView) "History" else "Battery Monitor",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                
                Row {
                    if (!isHistoryView) {
                        val prefs = remember { context.getSharedPreferences("battery_monitor_prefs", Context.MODE_PRIVATE) }
                        val isActuallyRunning = remember {
                            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            @Suppress("DEPRECATION")
                            manager.getRunningServices(Integer.MAX_VALUE).any {
                                BatteryMonitorService::class.java.name == it.service.className
                            }
                        }
                        var serviceRunning by remember { mutableStateOf(isActuallyRunning) }

                        IconButton(onClick = {
                            val intent = Intent(context, BatteryMonitorService::class.java)
                            if (serviceRunning) {
                                intent.action = "STOP"
                                context.startService(intent)
                                prefs.edit().putBoolean("is_battery_monitor_active", false).apply()
                                serviceRunning = false
                            } else {
                                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                    try {
                                        val optIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        optIntent.data = Uri.parse("package:${context.packageName}")
                                        context.startActivity(optIntent)
                                    } catch (e: Exception) {}
                                }
                                context.startForegroundService(intent)
                                prefs.edit().putBoolean("is_battery_monitor_active", true).apply()
                                serviceRunning = true
                            }
                        }) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Toggle Service", tint = if (serviceRunning) Color(0xFF00FF00) else Color.White)
                        }
                    }
                    IconButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                        } catch (e: Exception) {
                            // Fallback if not supported
                        }
                    }) {
                        Icon(Icons.Default.BatteryStd, contentDescription = "Device Settings", tint = Color.White)
                    }
                    IconButton(onClick = { isHistoryView = !isHistoryView }) {
                        Icon(if (isHistoryView) Icons.Default.ShowChart else Icons.Default.History, contentDescription = "Toggle View", tint = Color.White)
                    }
                }
            }
            
            if (isHistoryView) {
                HistoryView()
            } else {
                LiveMonitorView(context)
            }
        }
    }
}

@Composable
fun LiveMonitorView(context: Context) {
    val liveData by BatteryRepository.liveData.collectAsState()
    val currentMa by BatteryRepository.currentMa.collectAsState()
    
    // Scale Selection: 1min (60s), 10min (600s), 1hr (3600s), 2hr (7200s)
    var timeScaleSeconds by remember { mutableStateOf(60L) }
    
    // Live Battery Intent Stats
    var level by remember { mutableStateOf(0) }
    var temp by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Unknown") }
    var plugged by remember { mutableStateOf("Unknown") }
    var health by remember { mutableStateOf("Unknown") }
    var voltage by remember { mutableStateOf(0f) }
    var technology by remember { mutableStateOf("Unknown") }
    var capacityMah by remember { mutableStateOf(0) }
    
    DisposableEffect(Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let {
                    val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    level = if (scale > 0) (l * 100) / scale else 0
                    temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                    voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
                    technology = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
                    
                    val s = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    status = when (s) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        else -> "Unknown"
                    }
                    
                    val p = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    plugged = when (p) {
                        BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB Charger"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        else -> "On Battery"
                    }
                    
                    val h = it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                    health = when (h) {
                        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                        else -> "Unknown"
                    }
                    
                    val rawUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    val currentMah = if (rawUah > 0) rawUah / 1000 else 0
                    
                    try {
                        val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                        val mPowerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
                        val batteryCapacity = powerProfileClass.getMethod("getBatteryCapacity").invoke(mPowerProfile) as Double
                        capacityMah = batteryCapacity.toInt()
                    } catch (e: Exception) {
                        // Calculate true maximum capacity based on current charge and percentage
                        capacityMah = if (level > 0 && currentMah > 0) {
                            // Rounding to nearest 100 for cleaner display (e.g. 5498 -> 5500)
                            val exactCapacity = (currentMah * 100f) / level
                            ((exactCapacity + 50) / 100).toInt() * 100
                        } else {
                            currentMah
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    
    // Memory Status
    var ramGb by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        while(true) {
            am.getMemoryInfo(mi)
            ramGb = mi.availMem / (1024f * 1024f * 1024f)
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Time Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                TimeScaleButton("1 min", timeScaleSeconds == 60L) { timeScaleSeconds = 60L }
                TimeScaleButton("10 min", timeScaleSeconds == 600L) { timeScaleSeconds = 600L }
                TimeScaleButton("1 hour", timeScaleSeconds == 3600L) { timeScaleSeconds = 3600L }
                TimeScaleButton("2 hours", timeScaleSeconds == 7200L) { timeScaleSeconds = 7200L }
            }
            Text("mA", color = Color(0xFF1E88E5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Custom Canvas Chart
        LineChartComponent(liveData, timeScaleSeconds)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Current mA
        val minMa = liveData.minByOrNull { it.mA }?.mA ?: 0
        val maxMa = liveData.maxByOrNull { it.mA }?.mA ?: 0
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Min: $minMa mA", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${currentMa} mA", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Max: $maxMa", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        
        val activeSession by BatteryRepository.activeSession.collectAsState()

        Spacer(modifier = Modifier.height(32.dp))
        
        // Active Session
        activeSession?.let { session ->
            val isCharge = session.type == SessionType.CHARGE
            val action = if (isCharge) "Charged" else "Used"
            val diff = Math.abs(session.endPercent - session.startPercent)
            
            val durationMillis = session.endTime - session.startTime
            val totalSecs = durationMillis / 1000
            val h = totalSecs / 3600
            val m = (totalSecs % 3600) / 60
            val s = totalSecs % 60
            val timeStr = if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C34)).padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("$action $diff% in $timeStr", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Detailed Stats
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            StatRow("Status", status)
            StatRow("Level", "$level%")
            StatRow("Plugged", plugged)
            StatRow("Health", health)
            StatRow("Voltage", "${"%.3f".format(voltage)} V")
            StatRow("Battery", "${"%.1f".format(temp)} °C")
            StatRow("Technology", technology)
            StatRow("Battery Capacity", "$capacityMah mAh")
            StatRow("Available Free RAM", "${"%.2f".format(ramGb)} GB")
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, color = Color.LightGray, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Spacer(modifier = Modifier.width(16.dp))
        Text(value, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun TimeScaleButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) Color(0xFF1E88E5) else Color.Transparent
    val textColor = if (isSelected) Color.White else Color(0xFF1E88E5)
    
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(bgColor)
            .border(1.dp, Color(0xFF1E88E5))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = textColor, fontSize = 12.sp)
    }
}

@Composable
fun LineChartComponent(data: List<BatteryPoint>, timeScaleSeconds: Long) {
    val now = System.currentTimeMillis()
    val cutoff = now - (timeScaleSeconds * 1000)
    val visibleData = data.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }
    
    Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw Grid Lines (Y-Axis)
            val maxAbs = Math.max(
                Math.abs(visibleData.maxByOrNull { it.mA }?.mA ?: 2400),
                Math.abs(visibleData.minByOrNull { it.mA }?.mA ?: -2400)
            )
            // Round maxAbs up to nearest 500 for clean scales
            val yRange = ((maxAbs / 500) + 1) * 500
            
            val yZeroLine = height / 2f
            
            // Draw Grid Lines & Labels
            val textPaint = NativePaint().apply {
                color = android.graphics.Color.GRAY
                textSize = 30f
                typeface = Typeface.DEFAULT_BOLD
            }
            
            val gridSpacingY = yRange / 4f
            for (i in -4..4) {
                if (i == 0) continue
                val y = yZeroLine - (i * gridSpacingY / yRange * (height / 2f))
                drawLine(color = Color.DarkGray.copy(alpha = 0.5f), start = Offset(0f, y.toFloat()), end = Offset(width, y.toFloat()), strokeWidth = 1f)
                val label = "${(i * gridSpacingY).toInt()}"
                drawContext.canvas.nativeCanvas.drawText(label, width - 120f, y.toFloat() - 10f, textPaint)
            }
            
            // Draw 0 Line
            drawLine(color = Color.Gray, start = Offset(0f, yZeroLine), end = Offset(width, yZeroLine), strokeWidth = 2f)
            drawContext.canvas.nativeCanvas.drawText("0", width - 50f, yZeroLine - 10f, textPaint)
            
            // X-Axis grid (Time)
            for (i in 0..5) {
                val x = width * (1f - (i / 5f))
                drawLine(color = Color.DarkGray.copy(alpha = 0.3f), start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
                
                val timeVal = (timeScaleSeconds * (i / 5f)).toInt()
                val timeLabel = if (timeVal == 0) "0" else {
                    if (timeVal >= 3600 && timeVal % 3600 == 0) {
                        "${timeVal / 3600}h"
                    } else if (timeVal >= 60) {
                        "${timeVal / 60}m"
                    } else {
                        "${timeVal}s"
                    }
                }
                drawContext.canvas.nativeCanvas.drawText(timeLabel, x - 15f, height - 10f, textPaint)
            }
            
            // X-Axis bounds
            val xRangeMs = timeScaleSeconds * 1000f
            
            if (visibleData.isNotEmpty()) {
                val path = Path()
                var isFirst = true
                
                // Color configuration
                // Blue = Charging (Positive Y relative to 0 line)
                // Red = Discharging (Negative Y relative to 0 line)
                
                val currentDrawList = visibleData.map { point ->
                    val x = width - ((now - point.timestamp) / xRangeMs * width)
                    val y = yZeroLine - ((point.mA.toFloat() / yRange) * (height / 2f))
                    Offset(x.toFloat(), y)
                }
                
                for (i in 0 until currentDrawList.size - 1) {
                    val p1 = currentDrawList[i]
                    val p2 = currentDrawList[i + 1]
                    
                    val color = if (p1.y < yZeroLine) Color(0xFF1E88E5) else Color(0xFFE53935)
                    
                    drawLine(
                        color = color,
                        start = p1,
                        end = p2,
                        strokeWidth = 4f
                    )
                    
                    // Shading
                    val shadePath = Path().apply {
                        moveTo(p1.x, yZeroLine)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p2.x, yZeroLine)
                        close()
                    }
                    drawPath(
                        path = shadePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                            startY = if (p1.y < yZeroLine) p1.y else yZeroLine,
                            endY = if (p1.y < yZeroLine) yZeroLine else p1.y
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryView() {
    val sessions by BatteryRepository.sessions.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(sessions) { session ->
            HistoryCard(session)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun HistoryCard(session: BatterySession) {
    var expanded by remember { mutableStateOf(false) }
    
    val isCharge = session.type == SessionType.CHARGE
    val color = if (isCharge) Color(0xFF1E88E5) else Color(0xFFE53935)
    val icon = if (isCharge) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert
    
    val durationMillis = session.endTime - session.startTime
    val totalSecs = durationMillis / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    val durationStr = if (h > 0) "${h}h ${m}m" else "${m} min ${s} sec"
    
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeSdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    val dateStr = sdf.format(Date(session.startTime))
    val timeRangeStr = "${timeSdf.format(Date(session.startTime))} to ${timeSdf.format(Date(session.endTime))}"
    val percentDiff = session.endPercent - session.startPercent
    val percentSign = if (percentDiff > 0) "+" else ""

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C34)).padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.SpaceBetween, 
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("${percentSign}${percentDiff}%", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(durationStr, color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Color.Gray)
            }
        }
        
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(dateStr, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isCharge) "Charged:" else "Used:", color = Color.White, fontSize = 14.sp)
                    Text("${percentSign}${percentDiff}%", color = Color.White, fontSize = 14.sp)
                    Text(durationStr, color = Color.White, fontSize = 14.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${session.startPercent}% to ${session.endPercent}%", color = Color.White, fontSize = 14.sp)
                    Text(timeRangeStr, color = Color.White, fontSize = 14.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Plugged:", color = Color.White, fontSize = 14.sp)
                    Text(session.pluggedState, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
