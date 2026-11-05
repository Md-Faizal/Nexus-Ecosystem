package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleEntity
import com.nexus.portal.services.PerformanceTelemetryTracker
import com.nexus.portal.services.VehicleTelemetryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceRecordingScreen(
    onBackClick: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Telemetry Engine & Tracker
    val telemetryEngine = remember { VehicleTelemetryEngine() }
    val tracker = remember { PerformanceTelemetryTracker() }
    val trackerState by tracker.state.collectAsState()
    
    // Mappls Map State & Lifecycle wiring
    val mapView = remember {
        try {
            MapView(context)
        } catch (t: Throwable) {
            android.util.Log.e("PerformanceRecordingScreen", "Failed to initialize MapView due to credentials mismatch", t)
            null
        }
    }
    var mapplsMap by remember { mutableStateOf<MapplsMap?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var gateMarker by remember { mutableStateOf<Marker?>(null) }
    
    var isImperial by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    if (mapView != null) {
        DisposableEffect(lifecycleOwner, mapView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    // Vehicle Selection
    val db = remember { RideDatabase.getDatabase(context) }
    var vehicles by remember { mutableStateOf<List<VehicleEntity>>(emptyList()) }
    var selectedVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var showVehicleDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            db.vehicleDao().getAllVehicles().collect { list ->
                vehicles = list
                if (selectedVehicle == null && list.isNotEmpty()) {
                    selectedVehicle = list.first()
                }
            }
        }
    }

    // Permission Launcher
    var hasPermissions by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermissions = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLocation == PackageManager.PERMISSION_GRANTED) {
            hasPermissions = true
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Active Sensor & Location Listener while screen is recording
    var passiveLocation by remember { mutableStateOf<Location?>(null) }
    var currentLatG by remember { mutableStateOf(0f) }
    var currentLongG by remember { mutableStateOf(0f) }

    DisposableEffect(trackerState.isRecording, hasPermissions) {
        if (!hasPermissions) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val gyroValues = FloatArray(3)
        var hasGyro = false

        // Location updates
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L) // 2Hz updates
            .setMinUpdateDistanceMeters(0.2f)
            .build()
            
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                passiveLocation = loc
                if (trackerState.isRecording) {
                    tracker.processTelemetry(loc, currentLatG, currentLongG)
                }
            }
        }

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val isTwoWheeler = selectedVehicle?.type?.lowercase() != "car"
                        val telemetry = telemetryEngine.processSensors(
                            accelValues = event.values,
                            gyroValues = if (hasGyro) gyroValues else null,
                            speedMps = passiveLocation?.speed ?: 0f,
                            isTwoWheeler = isTwoWheeler
                        )
                        currentLatG = telemetry.lateralG
                        currentLongG = telemetry.longitudinalG
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        System.arraycopy(event.values, 0, gyroValues, 0, 3)
                        hasGyro = true
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

        // Request updates if recording
        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            accel?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
            gyro?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
            sensorManager.unregisterListener(sensorListener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexus Performance", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { isImperial = !isImperial }) {
                        Text(if (isImperial) "MPH" else "KM/H", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "Run History", tint = Color.White)
                    }
                    IconButton(onClick = onNavigateToLeaderboard) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = "Leaderboard", tint = Color(0xFF00E5FF))
                    }
                    IconButton(onClick = onNavigateToCamera) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Overlay", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0C))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0C))
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode selection chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerformanceTelemetryTracker.TestMode.values().forEach { mode ->
                    val isSelected = trackerState.activeMode == mode
                    val label = when (mode) {
                        PerformanceTelemetryTracker.TestMode.ACCELERATION -> "Accel"
                        PerformanceTelemetryTracker.TestMode.DRAG_DISTANCE -> "Drag"
                        PerformanceTelemetryTracker.TestMode.BRAKING -> "Braking"
                        PerformanceTelemetryTracker.TestMode.G_FORCE -> "G-Force"
                        PerformanceTelemetryTracker.TestMode.LAP_TIMER -> "Laps"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E24))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                tracker.setMode(mode)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Vehicle dropdown selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141418))
                    .clickable { if (!trackerState.isRecording) showVehicleDropdown = true }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = if (selectedVehicle?.type?.lowercase() == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike
                    Icon(icon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val displayName = selectedVehicle?.let { 
                            if (it.make.isBlank() && it.model.isBlank()) it.name else "${it.make} ${it.model}"
                        } ?: "Select Vehicle"
                        val subName = selectedVehicle?.let {
                            if (it.make.isBlank() && it.model.isBlank()) "Occasional Vehicle" else it.name
                        } ?: "No custom name"
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subName,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    if (!trackerState.isRecording) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                    }
                }
 
                DropdownMenu(
                    expanded = showVehicleDropdown,
                    onDismissRequest = { showVehicleDropdown = false },
                    modifier = Modifier.background(Color(0xFF1E1E24))
                ) {
                    vehicles.forEach { vehicle ->
                        val itemIcon = if (vehicle.type.lowercase() == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike
                        DropdownMenuItem(
                            text = { 
                                val displayName = if (vehicle.make.isBlank() && vehicle.model.isBlank()) vehicle.name else "${vehicle.make} ${vehicle.model} (${vehicle.name})"
                                Text(displayName, color = Color.White) 
                            },
                            leadingIcon = { Icon(itemIcon, contentDescription = null, tint = Color(0xFF00E5FF)) },
                            onClick = {
                                selectedVehicle = vehicle
                                showVehicleDropdown = false
                            }
                        )
                    }
                }
            }

            // Speed gauge and numeric speed
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Circular Speedometer Canvas
                val speed = if (isImperial) trackerState.currentSpeedKmh * 0.621371f else trackerState.currentSpeedKmh
                val maxSpeedVal = if (isImperial) 160f else 240f
                Canvas(modifier = Modifier.size(220.dp)) {
                    // Draw dial track (270 degrees arc from 135 deg to 45 deg)
                    drawArc(
                        color = Color(0xFF222228),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )

                    // Active speed sweep
                    val sweep = (speed / maxSpeedVal) * 270f
                    drawArc(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF00E676))),
                        startAngle = 135f,
                        sweepAngle = sweep.coerceIn(0f, 270f),
                        useCenter = false,
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f", speed),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isImperial) "mph" else "km/h",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Ready / Go glowing banners
                    if (trackerState.isRecording) {
                        val statusText = if (trackerState.isReady) "READY (Speed up to Start)" else "RECORDING"
                        val statusColor = if (trackerState.isReady) Color(0xFFFFAB00) else Color(0xFF00E676)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Real-time stats display depending on selected Mode
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141418))
                    .border(1.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                when (trackerState.activeMode) {
                    PerformanceTelemetryTracker.TestMode.ACCELERATION -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Acceleration Records", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Divider(color = Color(0xFF222228))
                            
                            if (isImperial) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("0 to 60 mph", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = trackerState.live0To60Time?.let { String.format("%.2f s", it) } ?: "-- s",
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("60 to 130 mph", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = trackerState.live60To130MphTime?.let { String.format("%.2f s", it) } ?: "-- s",
                                        color = Color(0xFFFFAB00),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("0 to 130 mph", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = trackerState.live0To130MphTime?.let { String.format("%.2f s", it) } ?: "-- s",
                                        color = Color(0xFFFFAB00),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("0 to 100 km/h", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = trackerState.live0To100KmhTime?.let { String.format("%.2f s", it) } ?: "-- s",
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("100 to 200 km/h", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = trackerState.live100To200KmhTime?.let { String.format("%.2f s", it) } ?: "-- s",
                                        color = Color(0xFFFFAB00),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    PerformanceTelemetryTracker.TestMode.DRAG_DISTANCE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Drag Distance", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                val distStr = if (isImperial) String.format("%.1f ft", trackerState.dragDistanceMeters * 3.28084f) else String.format("%.1f m", trackerState.dragDistanceMeters)
                                Text(distStr, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            }
                            Divider(color = Color(0xFF222228))
                            
                            val speedUnit = if (isImperial) "mph" else "km/h"
                            val speedMultiplier = if (isImperial) 0.621371f else 1f

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("1/8 Mile Time & Speed", color = Color.White)
                                Text(
                                    text = if (trackerState.live1_8Time != null) String.format("%.2f s @ %.1f %s", trackerState.live1_8Time, trackerState.live1_8Speed!! * speedMultiplier, speedUnit) else "--",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("1/4 Mile Time & Speed", color = Color.White)
                                Text(
                                    text = if (trackerState.live1_4Time != null) String.format("%.2f s @ %.1f %s", trackerState.live1_4Time, trackerState.live1_4Speed!! * speedMultiplier, speedUnit) else "--",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("1/2 Mile Time & Speed", color = Color.White)
                                Text(
                                    text = if (trackerState.live1_2Time != null) String.format("%.2f s @ %.1f %s", trackerState.live1_2Time, trackerState.live1_2Speed!! * speedMultiplier, speedUnit) else "--",
                                    color = Color(0xFFFFAB00),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    PerformanceTelemetryTracker.TestMode.BRAKING -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Braking Analytics", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Divider(color = Color(0xFF222228))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Braking Distance", color = Color.White)
                                val brDistStr = trackerState.brakingDistance?.let { if (isImperial) String.format("%.1f ft", it * 3.28084f) else String.format("%.2f m", it) } ?: "--"
                                Text(
                                    text = brDistStr,
                                    color = Color(0xFFFF1744),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Braking Duration", color = Color.White)
                                Text(
                                    text = trackerState.brakingTime?.let { String.format("%.2f s", it) } ?: "-- s",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Target Initial Speed", color = Color.White)
                                val initSpeedStr = if (trackerState.brakingStartSpeedKmh > 0) {
                                    if (isImperial) String.format("%.1f mph", trackerState.brakingStartSpeedKmh * 0.621371f) else String.format("%.1f km/h", trackerState.brakingStartSpeedKmh)
                                } else {
                                    "Waiting (≥60km/h)"
                                }
                                Text(
                                    text = initSpeedStr,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    PerformanceTelemetryTracker.TestMode.G_FORCE -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left side: Scatter coordinates list
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Live G-Forces", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Divider(color = Color(0xFF222228))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Lateral G:", color = Color.White)
                                    Text(String.format("%.2f G", currentLatG), color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Longitudinal G:", color = Color.White)
                                    Text(String.format("%.2f G", currentLongG), color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Peak Limits", color = Color.Gray, fontSize = 11.sp)
                                Text(String.format("Max Lat: %.2f G", trackerState.peakLatG), color = Color.White, fontSize = 12.sp)
                                Text(String.format("Max Long: %.2f G", trackerState.peakLongG), color = Color.White, fontSize = 12.sp)
                            }
                            
                            // Right side: Scatter grid visual
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0xFF222228), RoundedCornerShape(8.dp))
                                    .align(Alignment.CenterVertically),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val radius = size.width / 2f
                                    
                                    // Circular rings for 0.5G and 1.0G
                                    drawCircle(color = Color(0xFF1E1E24), radius = radius * 0.5f, style = Stroke(1f))
                                    drawCircle(color = Color(0xFF2E2E38), radius = radius * 1.0f, style = Stroke(1f))
                                    
                                    // Crosshairs
                                    drawLine(Color(0xFF1E1E24), Offset(0f, center.y), Offset(size.width, center.y), 1f)
                                    drawLine(Color(0xFF1E1E24), Offset(center.x, 0f), Offset(center.x, size.height), 1f)
                                    
                                    // Dynamic G location (scale: 1.0G matches radius)
                                    // Lateral G moves on X axis, Longitudinal G moves on Y axis (negative longitudinal is deceleration/upward)
                                    val ptX = center.x + (currentLatG / 1.0f) * radius
                                    val ptY = center.y - (currentLongG / 1.0f) * radius
                                    
                                    drawCircle(
                                        color = Color(0xFF00E5FF),
                                        radius = 6f,
                                        center = Offset(ptX.coerceIn(0f, size.width), ptY.coerceIn(0f, size.height))
                                    )
                                }
                            }
                        }
                    }
                    PerformanceTelemetryTracker.TestMode.LAP_TIMER -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Circuit Lap Timer", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (trackerState.isLapGateSet) {
                                    Text("Gate Locked", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("Gate Not Set", color = Color(0xFFFF1744), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Divider(color = Color(0xFF222228))
                            
                            // MapView Container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.5f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0xFF222228), RoundedCornerShape(8.dp))
                            ) {
                                if (mapView != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            mapView.apply {
                                                getMapAsync(object : OnMapReadyCallback {
                                                    override fun onMapReady(map: MapplsMap) {
                                                        mapplsMap = map
                                                        map.uiSettings?.isLogoEnabled = false
                                                        map.uiSettings?.isAttributionEnabled = false
                                                        
                                                        // Center camera on current location if available
                                                        passiveLocation?.let { loc ->
                                                            map.moveCamera(
                                                                CameraUpdateFactory.newLatLngZoom(
                                                                    LatLng(loc.latitude, loc.longitude), 16.0
                                                                )
                                                            )
                                                        }
                                                    }
                                                    override fun onMapError(p0: Int, p1: String?) {}
                                                })
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = { _ ->
                                            val map = mapplsMap ?: return@AndroidView
                                            
                                            // Update Location Marker
                                            passiveLocation?.let { loc ->
                                                val currentLatLng = LatLng(loc.latitude, loc.longitude)
                                                if (locationMarker == null) {
                                                    locationMarker = map.addMarker(
                                                        MarkerOptions().position(currentLatLng).title("My Location")
                                                    )
                                                } else {
                                                    locationMarker?.position = currentLatLng
                                                }
                                            }
                                            
                                            // Update Gate Marker
                                            if (trackerState.isLapGateSet) {
                                                val gateLat = trackerState.lapGateLat
                                                val gateLng = trackerState.lapGateLng
                                                if (gateLat != null && gateLng != null) {
                                                    val gateLatLng = LatLng(gateLat, gateLng)
                                                    if (gateMarker == null) {
                                                        gateMarker = map.addMarker(
                                                            MarkerOptions().position(gateLatLng).title("Start/Finish Gate")
                                                        )
                                                    } else {
                                                        gateMarker?.position = gateLatLng
                                                    }
                                                }
                                            } else {
                                                gateMarker?.remove()
                                                gateMarker = null
                                            }
                                        }
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = "Map Error",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Map integration offline (API Keys Mismatch)",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            }
                            
                            if (!trackerState.isLapGateSet) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF222228))
                                        .clickable {
                                            passiveLocation?.let { loc ->
                                                tracker.setLapGate(loc.latitude, loc.longitude)
                                                Toast.makeText(context, "Start/Finish Gate locked at current position!", Toast.LENGTH_SHORT).show()
                                            } ?: Toast.makeText(context, "Waiting for GPS signal...", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = Color(0xFF00E5FF))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Set Start/Finish Gate Here", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Current Lap:", color = Color.White, fontSize = 12.sp)
                                    val min = (trackerState.currentLapDurationMs / 60000) % 60
                                    val sec = (trackerState.currentLapDurationMs / 1000) % 60
                                    val ms = (trackerState.currentLapDurationMs % 1000) / 10
                                    Text(
                                        text = String.format("%02d:%02d.%02d", min, sec, ms),
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                    
                                    Button(
                                        onClick = {
                                            tracker.resetLapGate()
                                            Toast.makeText(context, "Gate reset!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Reset Gate", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                Text("Lap Splits", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    if (trackerState.lapsList.isEmpty()) {
                                        Text("Complete laps to view times.", color = Color.DarkGray, fontSize = 12.sp)
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            items(trackerState.lapsList.reversed()) { lapMs ->
                                                val min = (lapMs / 60000) % 60
                                                val sec = (lapMs / 1000) % 60
                                                val ms = (lapMs % 1000) / 10
                                                Text(
                                                    text = String.format("Lap: %02d:%02d.%02d", min, sec, ms),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Start / Stop controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!trackerState.isRecording) {
                    Button(
                        onClick = {
                            if (!hasPermissions) {
                                Toast.makeText(context, "GPS permission required!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            tracker.startRecording()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START RECORDING", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val run = withContext(Dispatchers.IO) {
                                    tracker.stopRecording(
                                        vehicleId = selectedVehicle?.id,
                                        vehicleName = selectedVehicle?.let { "${it.make} ${it.model}" } ?: "Manual Run",
                                        vehicleType = selectedVehicle?.type ?: "two_wheeler"
                                    )
                                }
                                if (run != null) {
                                    val activeProfileId = com.nexus.portal.data.repository.ProfileRepository.activeProfile.value.id
                                    val currentDeviceId = com.nexus.portal.data.repository.ProfileRepository.deviceId
                                    val runToSave = run.copy(
                                        profileId = activeProfileId,
                                        deviceId = currentDeviceId
                                    )
                                    withContext(Dispatchers.IO) {
                                        db.performanceRunDao().insertRun(runToSave)
                                    }
                                    Toast.makeText(context, "Run saved successfully!", Toast.LENGTH_SHORT).show()
                                    com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                    onNavigateToDetail(runToSave.id)
                                } else {
                                    Toast.makeText(context, "Run too short to save", Toast.LENGTH_SHORT).show()
                                    tracker.resetTracking()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOP & SAVE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
