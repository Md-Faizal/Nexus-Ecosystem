package com.nexus.portal.ui.screens.hub.apps.sensormanager

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexus.portal.ui.components.GlassmorphicCard
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.components.ScrollContainerContent
import com.nexus.portal.ui.theme.PortalTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SensorManagerScreen(onBackClick: () -> Unit, context: Context) {
    val primaryColor = PortalTheme.colors.primary
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val deviceSensors = remember { sensorManager.getSensorList(Sensor.TYPE_ALL) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        TechGridBackground()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "SENSOR DASHBOARDS",
                        color = PortalTheme.colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 1. Battery Dashboard
            item { BatteryDashboard(context) }
            
            // 2. GPS Location
            item { LocationDashboard(context) }

            // 3. Digital Compass
            item { CompassDashboard(sensorManager) }

            // 4. Orientation / Artificial Horizon
            item { OrientationDashboard(sensorManager) }

            // 5. Ambient Light
            item { LightSensorDashboard(sensorManager) }

            // 6. Proximity Sensor
            item { ProximityDashboard(sensorManager) }

            // 7. Pedometer
            item { PedometerDashboard(sensorManager, context) }

            // 8. Raw Hardware Debugger
            item { AdvancedHardwareDebugger(deviceSensors, sensorManager, primaryColor) }
        }
    }
}

// ==========================================
// REUSABLE RAW DATA COMPONENT
// ==========================================
@Composable
fun RawDataExpander(showRaw: Boolean, onToggle: (Boolean) -> Unit, rawContent: @Composable () -> Unit) {
    val primaryColor = PortalTheme.colors.primary
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle(!showRaw) }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RAW DATA", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Icon(if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = primaryColor, modifier = Modifier.size(16.dp))
        }
        AnimatedVisibility(visible = showRaw) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PortalTheme.colors.background)
                    .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                rawContent()
            }
        }
    }
}

// ==========================================
// INDIVIDUAL DASHBOARD COMPOSABLES
// ==========================================

@Composable
fun BatteryDashboard(context: Context) {
    var batteryLevel by remember { mutableStateOf(0f) }
    var voltage by remember { mutableStateOf(0f) }
    var temperature by remember { mutableStateOf(0f) }
    var isCharging by remember { mutableStateOf(false) }
    var technology by remember { mutableStateOf("Unknown") }
    var health by remember { mutableStateOf(-1) }
    var plugged by remember { mutableStateOf(-1) }
    var present by remember { mutableStateOf(false) }
    var capacityMah by remember { mutableStateOf(0) }
    
    var showRaw by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    batteryLevel = level * 100 / scale.toFloat()
                    voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
                    temperature = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    
                    technology = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
                    health = it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                    plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    present = it.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
                    
                    val rawUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    capacityMah = if (rawUah > 0) rawUah / 1000 else 0
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    val primaryColor = PortalTheme.colors.primary

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = "Battery", tint = primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("POWER METRICS", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Gauge
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress = { batteryLevel / 100f },
                        modifier = Modifier.size(100.dp),
                        color = if (isCharging) Color.Green else primaryColor,
                        strokeWidth = 8.dp,
                        trackColor = PortalTheme.colors.border
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${batteryLevel.toInt()}%", color = PortalTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (isCharging) {
                            Text("CHARGING", color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Stats
                Column(horizontalAlignment = Alignment.End) {
                    Text("Voltage", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    Text("${"%.2f".format(voltage)} V", color = PortalTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Core Temp", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    Text("${"%.1f".format(temperature)} °C", color = PortalTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Remaining Cap", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    val mahDisplay = if (capacityMah > 0) "$capacityMah mAh" else "N/A"
                    Text(mahDisplay, color = PortalTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            RawDataExpander(showRaw, { showRaw = it }) {
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED FAILURE"
                    BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
                    else -> "UNKNOWN"
                }
                val pluggedStr = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                    else -> "UNPLUGGED"
                }
                Text("Present: $present", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Technology: $technology", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Health: $healthStr", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Plugged: $pluggedStr", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Level: ${batteryLevel.toInt()}%", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Capacity: $capacityMah mAh", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Voltage: ${voltage}V", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Temperature: ${temperature}°C", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun LocationDashboard(context: Context) {
    var lat by remember { mutableStateOf(0.0) }
    var lon by remember { mutableStateOf(0.0) }
    var speed by remember { mutableStateOf(0f) }
    var alt by remember { mutableStateOf(0.0) }
    var accuracy by remember { mutableStateOf(0f) }
    var bearing by remember { mutableStateOf(0f) }
    var provider by remember { mutableStateOf("") }
    
    var hasPermission by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val primaryColor = PortalTheme.colors.primary

    DisposableEffect(isActive) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasPermission = perm
        
        var listener: LocationListener? = null
        if (isActive && perm) {
            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lat = location.latitude
                    lon = location.longitude
                    speed = location.speed * 3.6f // m/s to km/h
                    alt = location.altitude
                    accuracy = location.accuracy
                    bearing = location.bearing
                    provider = location.provider ?: "unknown"
                }
                override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
            } catch (e: SecurityException) {
                hasPermission = false
            }
        }
        onDispose {
            listener?.let { locationManager.removeUpdates(it) }
        }
    }

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsFixed, contentDescription = "GPS", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GLOBAL POSITIONING", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission && isActive) {
                Text("Location Permission Denied.", color = Color(0xFFFF3366), fontSize = 12.sp)
            } else if (isActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val radarRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart)
                    )
                    
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(primaryColor.copy(alpha=0.1f)).border(1.dp, primaryColor, CircleShape), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize().rotate(radarRotation)) {
                            drawArc(
                                brush = Brush.sweepGradient(listOf(Color.Transparent, primaryColor)),
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                        }
                        Icon(Icons.Default.MyLocation, contentDescription = "Radar Center", tint = primaryColor, modifier = Modifier.size(24.dp))
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text("LAT: ${"%.5f".format(lat)}", color = PortalTheme.colors.textPrimary, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("LON: ${"%.5f".format(lon)}", color = PortalTheme.colors.textPrimary, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("SPEED", color = PortalTheme.colors.textSecondary, fontSize = 10.sp)
                        Text("${"%.1f".format(speed)} km/h", color = PortalTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("ALT: ${"%.1f".format(alt)}m", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                RawDataExpander(showRaw, { showRaw = it }) {
                    Text("Provider: $provider", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Latitude: $lat", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Longitude: $lon", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Altitude: $alt m", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Speed: ${speed / 3.6f} m/s", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Bearing: $bearing degrees", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Accuracy: $accuracy m", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                Text("Telemetry Offline. Enable to connect to satellites.", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CompassDashboard(sensorManager: SensorManager) {
    var azimuth by remember { mutableStateOf(0f) }
    var accelRaw by remember { mutableStateOf(FloatArray(3)) }
    var magRaw by remember { mutableStateOf(FloatArray(3)) }
    
    var isActive by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val primaryColor = PortalTheme.colors.primary

    DisposableEffect(isActive) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        var accelValues = FloatArray(3)
        var magValues = FloatArray(3)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    accelValues = event.values.clone()
                    accelRaw = accelValues
                }
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    magValues = event.values.clone()
                    magRaw = magValues
                }
                
                val R = FloatArray(9)
                val I = FloatArray(9)
                if (SensorManager.getRotationMatrix(R, I, accelValues, magValues)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(R, orientation)
                    azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (azimuth < 0) azimuth += 360f
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        
        if (isActive && accel != null && mag != null) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val animatedAzimuth by animateFloatAsState(targetValue = azimuth, animationSpec = tween(300, easing = LinearOutSlowInEasing))

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Explore, contentDescription = "Compass", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DIGITAL COMPASS", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, primaryColor, CircleShape), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize().rotate(-animatedAzimuth)) {
                            drawPath(
                                Path().apply { moveTo(size.width / 2, 10f); lineTo(size.width / 2 - 15f, size.height / 2); lineTo(size.width / 2 + 15f, size.height / 2); close() },
                                color = Color(0xFFFF3366)
                            )
                            drawPath(
                                Path().apply { moveTo(size.width / 2, size.height - 10f); lineTo(size.width / 2 - 15f, size.height / 2); lineTo(size.width / 2 + 15f, size.height / 2); close() },
                                color = Color.White
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("HEADING", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                        Text("${"%.0f".format(azimuth)}°", color = PortalTheme.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        val direction = when (azimuth) {
                            in 337.5..360.0, in 0.0..22.5 -> "N"
                            in 22.5..67.5 -> "NE"
                            in 67.5..112.5 -> "E"
                            in 112.5..157.5 -> "SE"
                            in 157.5..202.5 -> "S"
                            in 202.5..247.5 -> "SW"
                            in 247.5..292.5 -> "W"
                            in 292.5..337.5 -> "NW"
                            else -> "-"
                        }
                        Text(direction, color = primaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                RawDataExpander(showRaw, { showRaw = it }) {
                    Text("Calculated Azimuth: $azimuth", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("TYPE_MAGNETIC_FIELD:", color = primaryColor, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" X: ${magRaw.getOrElse(0){0f}} μT", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" Y: ${magRaw.getOrElse(1){0f}} μT", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" Z: ${magRaw.getOrElse(2){0f}} μT", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("TYPE_ACCELEROMETER:", color = primaryColor, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" X: ${accelRaw.getOrElse(0){0f}} m/s²", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" Y: ${accelRaw.getOrElse(1){0f}} m/s²", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" Z: ${accelRaw.getOrElse(2){0f}} m/s²", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                Text("Sensor Offline.", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OrientationDashboard(sensorManager: SensorManager) {
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }
    var rotRaw by remember { mutableStateOf(FloatArray(5)) }
    
    var isActive by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    
    val primaryColor = PortalTheme.colors.primary

    DisposableEffect(isActive) {
        val rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                rotRaw = event.values.clone()
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, orientation)
                pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (isActive && rotVector != null) {
            sensorManager.registerListener(listener, rotVector, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val animPitch by animateFloatAsState(targetValue = pitch, animationSpec = tween(100))
    val animRoll by animateFloatAsState(targetValue = roll, animationSpec = tween(100))

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Gyro", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ARTIFICIAL HORIZON", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, PortalTheme.colors.border, CircleShape)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cy = size.height / 2 + (animPitch * 2f)
                            rotate(animRoll, pivot = Offset(size.width / 2, size.height / 2)) {
                                drawRect(color = Color(0xFF1E88E5), topLeft = Offset(-500f, -500f), size = Size(1000f, cy + 500f))
                                drawRect(color = Color(0xFF8D6E63), topLeft = Offset(-500f, cy), size = Size(1000f, 1000f))
                                drawLine(color = Color.White, start = Offset(-500f, cy), end = Offset(1000f, cy), strokeWidth = 2.dp.toPx())
                            }
                            drawLine(color = Color.Yellow, start = Offset(20f, size.height / 2), end = Offset(size.width / 2 - 10f, size.height / 2), strokeWidth = 3.dp.toPx())
                            drawLine(color = Color.Yellow, start = Offset(size.width / 2 + 10f, size.height / 2), end = Offset(size.width - 20f, size.height / 2), strokeWidth = 3.dp.toPx())
                            drawCircle(color = Color.Yellow, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PITCH: ${"%.1f".format(pitch)}°", color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ROLL: ${"%.1f".format(roll)}°", color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                RawDataExpander(showRaw, { showRaw = it }) {
                    Text("TYPE_ROTATION_VECTOR:", color = primaryColor, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" x*sin(θ/2): ${rotRaw.getOrElse(0){0f}}", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" y*sin(θ/2): ${rotRaw.getOrElse(1){0f}}", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" z*sin(θ/2): ${rotRaw.getOrElse(2){0f}}", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" cos(θ/2): ${rotRaw.getOrElse(3){0f}}", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" estimated heading Accuracy: ${rotRaw.getOrElse(4){0f}}", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                Text("Sensor Offline.", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LightSensorDashboard(sensorManager: SensorManager) {
    var lux by remember { mutableStateOf(0f) }
    var rawValues by remember { mutableStateOf(FloatArray(1)) }
    
    var isActive by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    
    val primaryColor = PortalTheme.colors.primary

    DisposableEffect(isActive) {
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    lux = event.values[0]
                    rawValues = event.values.clone()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (isActive && lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = "Light", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PHOTOMETER", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val maxLux = 1000f
                    val scale = (lux / maxLux).coerceIn(0.1f, 1f)
                    val glowAlpha by animateFloatAsState(targetValue = scale, animationSpec = tween(500))
                    
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(100.dp * glowAlpha).clip(CircleShape).background(Color(0xFFFFC107).copy(alpha = 0.5f)))
                        Box(modifier = Modifier.size(60.dp * glowAlpha).clip(CircleShape).background(Color(0xFFFFC107).copy(alpha = 0.8f)))
                        Icon(Icons.Default.Lightbulb, contentDescription = "Bulb", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ILLUMINANCE", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                        Text("${"%.1f".format(lux)} lx", color = PortalTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                RawDataExpander(showRaw, { showRaw = it }) {
                    Text("TYPE_LIGHT:", color = primaryColor, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" values[0]: ${rawValues.getOrElse(0){0f}} lx", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                Text("Sensor Offline.", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ProximityDashboard(sensorManager: SensorManager) {
    var distance by remember { mutableStateOf(0f) }
    var rawValues by remember { mutableStateOf(FloatArray(1)) }
    var maxRange by remember { mutableStateOf(5f) }
    
    var isActive by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    
    val primaryColor = PortalTheme.colors.primary

    DisposableEffect(isActive) {
        val prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (prox != null) maxRange = prox.maximumRange
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    distance = event.values[0]
                    rawValues = event.values.clone()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (isActive && prox != null) {
            sensorManager.registerListener(listener, prox, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Radar, contentDescription = "Proximity", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PROXIMITY SONAR", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isActive) {
                val isNear = distance < maxRange
                val infiniteTransition = rememberInfiniteTransition()
                val sonarScale by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = if (isNear) 0.8f else 1.5f,
                    animationSpec = infiniteRepeatable(animation = tween(if (isNear) 200 else 1500), repeatMode = RepeatMode.Restart)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(80.dp * sonarScale).clip(CircleShape).border(2.dp, if (isNear) Color(0xFFFF3366) else primaryColor, CircleShape))
                        Icon(if (isNear) Icons.Default.Warning else Icons.Default.WifiTethering, contentDescription = "Sonar", tint = if (isNear) Color(0xFFFF3366) else primaryColor)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (isNear) "OBJECT DETECTED" else "CLEAR", color = if (isNear) Color(0xFFFF3366) else primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${"%.1f".format(distance)} cm", color = PortalTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                RawDataExpander(showRaw, { showRaw = it }) {
                    Text("TYPE_PROXIMITY:", color = primaryColor, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" values[0]: ${rawValues.getOrElse(0){0f}} cm", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" Maximum Range: $maxRange cm", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                Text("Sensor Offline.", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PedometerDashboard(sensorManager: SensorManager, context: Context) {
    var steps by remember { mutableStateOf(0) }
    var rawValues by remember { mutableStateOf(FloatArray(1)) }
    
    var isActive by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    val primaryColor = PortalTheme.colors.primary

    DisposableEffect(isActive) {
        val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasPermission = perm

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    steps = event.values[0].toInt()
                    rawValues = event.values.clone()
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        
        if (isActive && hasPermission && stepSensor != null) {
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    ScrollContainerContent {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = "Steps", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ACTIVITY TRACKER", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission && isActive) {
                Text("Activity Permission Denied.", color = Color(0xFFFF3366), fontSize = 12.sp)
            } else if (isActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val goal = 10000f
                    val progress = (steps / goal).coerceIn(0f, 1f)
                    
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(100.dp), color = primaryColor, strokeWidth = 8.dp, trackColor = PortalTheme.colors.border)
                        Icon(Icons.Default.DirectionsRun, contentDescription = "Run", tint = PortalTheme.colors.textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("STEPS TAKEN", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                        Text("$steps", color = PortalTheme.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                RawDataExpander(showRaw, { showRaw = it }) {
                    Text("TYPE_STEP_COUNTER:", color = primaryColor, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(" values[0]: ${rawValues.getOrElse(0){0f}} steps since last reboot", color = PortalTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                Text("Sensor Offline.", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AdvancedHardwareDebugger(deviceSensors: List<Sensor>, sensorManager: SensorManager, primaryColor: Color) {
    var showList by remember { mutableStateOf(false) }
    
    ScrollContainerContent {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showList = !showList },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = "Debug", tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADVANCED HARDWARE DEBUGGER", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Icon(if (showList) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expand", tint = PortalTheme.colors.textSecondary)
            }
            
            AnimatedVisibility(visible = showList) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    deviceSensors.forEach { sensor ->
                        DynamicSensorCard(sensor, sensorManager, primaryColor)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicSensorCard(sensor: Sensor, sensorManager: SensorManager, primaryColor: Color) {
    var isExpanded by remember { mutableStateOf(false) }
    var sensorValues by remember { mutableStateOf<FloatArray?>(null) }
    
    DisposableEffect(isExpanded) {
        if (isExpanded) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let { sensorValues = it.values.clone() }
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose {}
        }
    }

    val sensorTypeName = getSensorTypeName(sensor.type)

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PortalTheme.colors.background).border(1.dp, PortalTheme.colors.border, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, contentDescription = "Sensor", tint = primaryColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sensor.name, color = PortalTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "$sensorTypeName • ${sensor.vendor}", color = PortalTheme.colors.textSecondary, fontSize = 10.sp)
                }
                Switch(checked = isExpanded, onCheckedChange = { isExpanded = it })
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    val values = sensorValues
                    if (values != null && values.isNotEmpty()) {
                        values.forEachIndexed { index, value ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "val[$index]", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                                Text(text = "%.4f".format(value), color = PortalTheme.colors.textPrimary, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    } else {
                        Text(text = "Waiting for stream...", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

fun getSensorTypeName(type: Int): String {
    return when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic Rotation Vector"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (Uncalibrated)"
        Sensor.TYPE_HEART_RATE -> "Heart Rate"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic Field"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetic Field (Uncalibrated)"
        Sensor.TYPE_PRESSURE -> "Pressure"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
        Sensor.TYPE_STEP_COUNTER -> "Step Counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
        Sensor.TYPE_STATIONARY_DETECT -> "Stationary Detect"
        Sensor.TYPE_MOTION_DETECT -> "Motion Detect"
        else -> "Custom / OEM Sensor ($type)"
    }
}
