package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.portal.ui.theme.PortalTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.Polyline
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.nexus.portal.services.RideTrackerService
import com.nexus.portal.services.VehicleTelemetryEngine
import com.nexus.portal.util.BluetoothMacUtils
import com.nexus.portal.util.VehicleConnectionResolution
import com.nexus.portal.util.collectLiveGarageMacs
import com.nexus.portal.util.resolveVehicleForConnectedMacs
import android.content.IntentFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Vehicle Mode
// ─────────────────────────────────────────────────────────────────────────────

enum class VehicleMode(val label: String) {
    CAR("Car"), BIKE("Bike"), AUTO("Auto")
}

enum class WidgetType { SPEED, DURATION, LEAN_OR_GFORCE, GFORCE_CIRCLE, ELEVATION, ADD_NEW }

// ─────────────────────────────────────────────────────────────────────────────
// Telemetry Palette
// ─────────────────────────────────────────────────────────────────────────────

val ColorVelocity = Color(0xFF00E5FF)
val ColorAccel    = Color(0xFF00E676)
val ColorBrake    = Color(0xFFFF1744)
val ColorNeutral  = Color(0xFF546E7A)
val ColorWarn     = Color(0xFFFFAB00)
val ColorBike     = Color(0xFF69F0AE)
val ColorAuto     = Color(0xFFD500F9)

// ─────────────────────────────────────────────────────────────────────────────
// Custom Marker Bitmaps  (top-down vehicle silhouettes)
// ─────────────────────────────────────────────────────────────────────────────

private fun createCarMarkerBitmap(): Bitmap {
    val s = 80
    val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bm)
    val p  = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

    // Sleek Glow halo
    p.color = android.graphics.Color.parseColor("#4400E5FF")
    cv.drawCircle(s / 2f, s / 2f, s / 2f, p)

    // Modern neon arrowhead (navigation arrow)
    p.color = android.graphics.Color.parseColor("#00E5FF")
    p.style = AndroidPaint.Style.FILL
    val ap = AndroidPath().apply {
        moveTo(s * 0.50f, s * 0.15f)
        lineTo(s * 0.20f, s * 0.80f)
        lineTo(s * 0.50f, s * 0.65f)
        lineTo(s * 0.80f, s * 0.80f)
        close()
    }
    cv.drawPath(ap, p)
    
    // Core highlight
    p.color = android.graphics.Color.WHITE
    val hp = AndroidPath().apply {
        moveTo(s * 0.50f, s * 0.20f)
        lineTo(s * 0.30f, s * 0.70f)
        lineTo(s * 0.50f, s * 0.60f)
        close()
    }
    cv.drawPath(hp, p)
    
    return bm
}

private fun createBikeMarkerBitmap(): Bitmap {
    val s = 80
    val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bm)
    val p  = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

    // Glow halo
    p.color = android.graphics.Color.parseColor("#4469F0AE")
    cv.drawCircle(s / 2f, s / 2f, s / 2f, p)

    // Slimmer, sharper neon arrowhead for bike
    p.color = android.graphics.Color.parseColor("#69F0AE")
    p.style = AndroidPaint.Style.FILL
    val ap = AndroidPath().apply {
        moveTo(s * 0.50f, s * 0.10f)
        lineTo(s * 0.30f, s * 0.85f)
        lineTo(s * 0.50f, s * 0.75f)
        lineTo(s * 0.70f, s * 0.85f)
        close()
    }
    cv.drawPath(ap, p)
    
    p.color = android.graphics.Color.WHITE
    val hp = AndroidPath().apply {
        moveTo(s * 0.50f, s * 0.15f)
        lineTo(s * 0.35f, s * 0.75f)
        lineTo(s * 0.50f, s * 0.70f)
        close()
    }
    cv.drawPath(hp, p)
    
    return bm
}

private fun createAutoMarkerBitmap(isBike: Boolean): Bitmap {
    val s = 80
    val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bm)
    val p  = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

    // Glow halo
    p.color = android.graphics.Color.parseColor("#44D500F9")
    cv.drawCircle(s / 2f, s / 2f, s / 2f, p)

    // Dashed border ring to indicate "auto" state
    p.color = android.graphics.Color.parseColor("#D500F9")
    p.style = AndroidPaint.Style.STROKE; p.strokeWidth = 3f
    cv.drawCircle(s / 2f, s / 2f, s / 2f - 3f, p)
    p.style = AndroidPaint.Style.FILL

    // Inner navigation arrow
    if (isBike) {
        val ap = AndroidPath().apply {
            moveTo(s * 0.50f, s * 0.20f)
            lineTo(s * 0.35f, s * 0.75f)
            lineTo(s * 0.50f, s * 0.65f)
            lineTo(s * 0.65f, s * 0.75f)
            close()
        }
        cv.drawPath(ap, p)
    } else {
        val ap = AndroidPath().apply {
            moveTo(s * 0.50f, s * 0.25f)
            lineTo(s * 0.25f, s * 0.70f)
            lineTo(s * 0.50f, s * 0.60f)
            lineTo(s * 0.75f, s * 0.70f)
            close()
        }
        cv.drawPath(ap, p)
    }
    
    p.color = android.graphics.Color.WHITE
    val hp = AndroidPath().apply {
        moveTo(s * 0.50f, s * (if(isBike) 0.25f else 0.30f))
        lineTo(s * (if(isBike) 0.40f else 0.30f), s * (if(isBike) 0.65f else 0.60f))
        lineTo(s * 0.50f, s * (if(isBike) 0.60f else 0.55f))
        close()
    }
    cv.drawPath(hp, p)

    return bm
}



// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RideTrackerScreen(
    onBackClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGarage: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    context: Context
) {
    val trackingState by RideTrackerService.trackingState.collectAsState()
    val liveLocation by RideTrackerService.liveLocation.collectAsState()
    
    val settingsManager = remember { com.nexus.portal.managers.RideSettingsManager.getInstance(context) }
    val gForceSensitivity by settingsManager.gForceSensitivity.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLocationEnabled by remember { mutableStateOf(true) }
    var hasOverlayPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                isLocationEnabled = locationManager?.let {
                    it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                } ?: false
                hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        isLocationEnabled = locationManager?.let {
            it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } ?: false
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        RideTrackerService.checkAndStartAutoDetectIfConnected(context)
        RideTrackerService.autoPauseEventFlow.collect { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    }

    var vehicleMode   by remember { mutableStateOf(VehicleMode.AUTO) }
    val vehicleDao = remember { com.nexus.portal.data.local.ridetracker.RideDatabase.getDatabase(context).vehicleDao() }
    val vehiclesList by vehicleDao.getAllVehicles().collectAsState(initial = emptyList())
    var selectedVehicle by remember { mutableStateOf<com.nexus.portal.data.local.ridetracker.VehicleEntity?>(null) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }



    LaunchedEffect(trackingState.connectedVehicleId, vehiclesList) {
        val autoId = trackingState.connectedVehicleId
        if (autoId != null) {
            val autoVehicle = vehiclesList.find { it.id == autoId }
            if (autoVehicle != null) {
                selectedVehicle = autoVehicle
            }
        }
    }

    var autoCenter    by remember { mutableStateOf(true) }
    var recenterTrigger by remember { mutableStateOf(0L) }
    var selectedSegment by remember { mutableStateOf<com.nexus.portal.data.local.ridetracker.RideLocationPoint?>(null) }
    var is3DMode       by remember { mutableStateOf(false) }

    // ── Passive IMU Telemetry (G-Force & Lean Angle preview when not tracking) ──
    var passiveLatG by remember { mutableStateOf(0f) }
    var passiveLongG by remember { mutableStateOf(0f) }
    var passiveLean by remember { mutableStateOf(0f) }

    val currentLatG by remember {
        derivedStateOf { if (trackingState.isTracking) trackingState.accelLateral else passiveLatG }
    }
    val currentLongG by remember {
        derivedStateOf { if (trackingState.isTracking) trackingState.accelLongitudinal else passiveLongG }
    }
    val currentLeanAngle by remember {
        derivedStateOf { if (trackingState.isTracking) trackingState.leanAngle else passiveLean }
    }

    var widgetOrder by remember { mutableStateOf(listOf(
        WidgetType.SPEED, WidgetType.DURATION, WidgetType.LEAN_OR_GFORCE, WidgetType.GFORCE_CIRCLE, WidgetType.ELEVATION, WidgetType.ADD_NEW
    )) }
    var draggedWidgetIdx by remember { mutableStateOf(-1) }
    var hoverTargetIdx by remember { mutableStateOf(-1) }
    var dragCurrentOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var gridWidthPx by remember { mutableStateOf(1f) }

    var lastInteractionMs by remember { mutableStateOf(0L) }
    val onUserInteract: () -> Unit = {
        autoCenter = false
        lastInteractionMs = System.currentTimeMillis()
    }
    val onForceRecenter: () -> Unit = {
        autoCenter = true
        lastInteractionMs = 0L          // Cancel pending timer
        recenterTrigger = System.currentTimeMillis()
    }

    // ── Inactivity-based auto-recenter ──────────────────────────────────────
    // Every time the user interacts, lastInteractionMs is bumped.
    // LaunchedEffect re-launches, waits 6 s, then re-enables autoCenter.
    LaunchedEffect(lastInteractionMs) {
        if (lastInteractionMs > 0L) {
            delay(6_000)
            onForceRecenter() // This sets autoCenter=true AND triggers recenter!
        }
    }

    // ── Auto center when tracking starts ────────────────────────────────────
    LaunchedEffect(trackingState.isTracking) {
        if (trackingState.isTracking) {
            onForceRecenter()
        }
    }

    // ── Auto sync on open ───────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        com.nexus.portal.managers.RideTrackerSyncManager.fetchFromCloud(context)
        com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
    }

    // ── Permissions ─────────────────────────────────────────────────────────
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        val ok = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionsGranted = ok
        if (!ok) permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // ── Passive location (even when not tracking) ────────────────────────────
    var passiveLocation by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(permissionsGranted) {
        if (!permissionsGranted) return@DisposableEffect onDispose {}
        val client = LocationServices.getFusedLocationProviderClient(context)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(2f)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                passiveLocation = r.lastLocation
            }
        }
        try { client.requestLocationUpdates(req, cb, Looper.getMainLooper()) }
        catch (_: SecurityException) {}
        onDispose { client.removeLocationUpdates(cb) }
    }

    // Merge: prefer service location during tracking (same GPS data, but integrated with state)
    val effectiveLocation: Location? by remember {
        derivedStateOf {
            if (trackingState.isTracking && trackingState.currentLat != 0.0) {
                Location("service").apply {
                    latitude  = trackingState.currentLat
                    longitude = trackingState.currentLng
                }
            } else passiveLocation
        }
    }

    // Auto-detect vehicle type for AUTO mode with hysteresis smoothing to prevent rapid flickering
    var detectedAsBike by remember { mutableStateOf(false) }

    LaunchedEffect(trackingState.leanAngle) {
        val targetBikeState = abs(trackingState.leanAngle) > 12f
        if (targetBikeState != detectedAsBike) {
            // Wait for 1.5 seconds of persistent tilt to confirm vehicle type change
            delay(1500)
            if ((abs(trackingState.leanAngle) > 12f) == targetBikeState) {
                detectedAsBike = targetBikeState
            }
        }
    }

    DisposableEffect(trackingState.isTracking, permissionsGranted, vehicleMode) {
        if (trackingState.isTracking || !permissionsGranted) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val accel = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
        
        val engine = VehicleTelemetryEngine()
        val gyroValues = FloatArray(3)
        var hasGyro = false

        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                event ?: return
                when (event.sensor.type) {
                    android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                        val gyroBuf = if (hasGyro) gyroValues else null
                        val speedMps = (passiveLocation?.speed ?: 0f)
                        val isTwoWheeler = vehicleMode == VehicleMode.BIKE || (vehicleMode == VehicleMode.AUTO && abs(passiveLean) > 12f)
                        
                        val telemetry = engine.processSensors(
                            accelValues = event.values,
                            gyroValues = gyroBuf,
                            speedMps = speedMps,
                            isTwoWheeler = isTwoWheeler
                        )
                        passiveLatG = telemetry.lateralG
                        passiveLongG = telemetry.longitudinalG
                        passiveLean = telemetry.leanAngle
                    }
                    android.hardware.Sensor.TYPE_GYROSCOPE -> {
                        System.arraycopy(event.values, 0, gyroValues, 0, 3)
                        hasGyro = true
                    }
                }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }

        accel?.let { sensorManager.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_GAME) }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // ── Layout ───────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black).padding(paddingValues)) {
            val screenHeight = maxHeight
            val density = LocalDensity.current
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }

        // Draggable panel with snap states
        var panelHeightDp by remember { mutableStateOf(356.dp) }
        val animPanelHeight by animateDpAsState(
            targetValue = panelHeightDp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "panelHeight"
        )

        // ── Map: full-screen background ──────────────────────────────────────
        val pipFraction by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (panelHeightDp > 400.dp) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "pipFraction"
        )

        var pipDragOffsetX by remember { mutableStateOf(0f) }
        var pipDragOffsetY by remember { mutableStateOf(0f) }

        if (permissionsGranted) {
            val pipWidthPx = screenWidthPx * 0.35f
            val pipHeightPx = screenHeightPx * 0.35f
            val defaultTargetX = (screenWidthPx - pipWidthPx) / 2f - with(density) { 16.dp.toPx() }
            val defaultTargetY = (screenHeightPx - pipHeightPx) / 2f - with(density) { 110.dp.toPx() }
            
            val pipFinalX = defaultTargetX + pipDragOffsetX
            val pipFinalY = defaultTargetY + pipDragOffsetY

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (pipFraction > 0.5f) 10f else 0f)
            ) {
                MapplsVectorMapView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (pipFraction > 0f) {
                                val scale = 1f - (0.65f * pipFraction)
                                scaleX = scale
                                scaleY = scale
                                
                                translationX = pipFinalX * pipFraction
                                translationY = pipFinalY * pipFraction
                                
                                clip = true
                                shape = RoundedCornerShape(16.dp * pipFraction)
                            }
                        },
                    bottomPaddingDp  = if (pipFraction > 0f) 0.dp else animPanelHeight,
                    trackingState    = trackingState,
                    currentLocation  = effectiveLocation,
                    vehicleMode      = vehicleMode,
                    detectedAsBike   = detectedAsBike,
                    autoCenter       = autoCenter,
                    recenterTrigger  = recenterTrigger,
                    is3D             = is3DMode,
                    onUserInteract   = onUserInteract,
                    onSegmentClick   = { selectedSegment = it }
                )

                if (selectedSegment != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 100.dp, start = 16.dp, end = 16.dp)
                            .zIndex(20f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        SegmentDetailsCard(
                            point = selectedSegment!!,
                            onClose = { selectedSegment = null }
                        )
                    }
                }

                if (pipFraction == 1f) {
                    val pipTopLeftX = screenWidthPx / 2f - pipWidthPx / 2f + pipFinalX
                    val pipTopLeftY = screenHeightPx / 2f - pipHeightPx / 2f + pipFinalY

                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(pipTopLeftX.toInt(), pipTopLeftY.toInt()) }
                            .size(with(density) { pipWidthPx.toDp() }, with(density) { pipHeightPx.toDp() })
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    pipDragOffsetX += dragAmount.x
                                    pipDragOffsetY += dragAmount.y
                                }
                            }
                    ) {
                        IconButton(
                            onClick = onForceRecenter,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .background(
                                    if (autoCenter) ColorVelocity.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.65f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.GpsFixed, "Center",
                                tint = if (autoCenter) ColorVelocity else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            VehicleSelector(
                                selected = vehicleMode,
                                isTracking = trackingState.isTracking,
                                onSelect = { 
                                    vehicleMode = it
                                    if (trackingState.isPreRecording || trackingState.isTracking) {
                                        val updateIntent = Intent(context, RideTrackerService::class.java).apply {
                                            action = RideTrackerService.ACTION_UPDATE_VEHICLE_MODE
                                            putExtra("VEHICLE_MODE", it.name)
                                        }
                                        context.startService(updateIntent)
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (trackingState.isTracking) {
                                // Pause/Resume Button
                                FloatingActionButton(
                                    onClick = {
                                        val intent = Intent(context, RideTrackerService::class.java).apply {
                                            action = if (trackingState.isPaused) RideTrackerService.ACTION_RESUME
                                                     else RideTrackerService.ACTION_PAUSE
                                        }
                                        context.startService(intent)
                                    },
                                    modifier = Modifier.size(40.dp),
                                    containerColor = if (trackingState.isPaused) ColorAccel else ColorVelocity,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                                ) {
                                    Icon(
                                        if (trackingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (trackingState.isPaused) "Resume" else "Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Stop Button
                                FloatingActionButton(
                                    onClick = {
                                        val intent = Intent(context, RideTrackerService::class.java).apply {
                                            action = RideTrackerService.ACTION_STOP
                                        }
                                        context.startService(intent)
                                    },
                                    modifier = Modifier.size(40.dp),
                                    containerColor = ColorBrake,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        "Stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                if (trackingState.isPreRecording) {
                                    FloatingActionButton(
                                        onClick = {
                                            val stopIntent = Intent(context, RideTrackerService::class.java).apply {
                                                action = RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                            }
                                            context.startService(stopIntent)
                                        },
                                        modifier = Modifier.size(48.dp),
                                        containerColor = ColorBrake,
                                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            "Cancel Auto-Detect",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                
                                // Start Button
                                FloatingActionButton(
                                    onClick = {
                                        if (!android.provider.Settings.canDrawOverlays(context)) {
                                            val intent = Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                            return@FloatingActionButton
                                        }
                                        
                                        val intent = Intent(context, RideTrackerService::class.java).apply {
                                            action = RideTrackerService.ACTION_START
                                             putExtra("VEHICLE_MODE", vehicleMode.name)
                                             selectedVehicle?.id?.let { putExtra("VEHICLE_ID", it) }
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = ColorAccel,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        "Start",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Location Permission Required", color = Color.White)
            }
        }

        if (!hasOverlayPermission) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .zIndex(10f)
                    .background(Color(0xCCFF3B30), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .clickable {
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Auto-Pause overlays require 'Display over other apps' permission. Tap to grant.",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        "(On Android 13+, first allow 'Restricted Settings' in App Info)",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ── Top overlay bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            VehicleSelector(
                selected = vehicleMode,
                isTracking = trackingState.isTracking,
                onSelect = { 
                    vehicleMode = it
                    if (trackingState.isPreRecording || trackingState.isTracking) {
                        val updateIntent = Intent(context, RideTrackerService::class.java).apply {
                            action = RideTrackerService.ACTION_UPDATE_VEHICLE_MODE
                            putExtra("VEHICLE_MODE", it.name)
                        }
                        context.startService(updateIntent)
                    }
                }
            )

            IconButton(
                onClick = onNavigateToGarage,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.DirectionsBike, "Garage", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(Icons.Default.Assessment, "History", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            
            // ── Floating 2D/3D Map Toggle (Placed beside the History button)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (is3DMode) ColorVelocity.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, if (is3DMode) ColorVelocity else Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .height(30.dp)
                    .width(40.dp)
                    .clickable { is3DMode = !is3DMode }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (is3DMode) "3D" else "2D",
                        color = if (is3DMode) ColorVelocity else Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (pipFraction < 1f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 80.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !(trackingState.isTracking && selectedVehicle != null)) { vehicleDropdownExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayName = selectedVehicle?.let { 
                                if (it.make.isBlank() && it.model.isBlank()) it.name else "${it.make} ${it.model} (${it.name})"
                            } ?: "No Vehicle Selected"
                            
                            val icon = if (selectedVehicle?.type?.lowercase() == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike
                            val iconColor = if (trackingState.isTracking) Color(0xFF00E5FF) else ColorVelocity
                            
                            Icon(
                                imageVector = icon,
                                contentDescription = "Vehicle Type",
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select Vehicle",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Anchored DropdownMenu
                        DropdownMenu(
                            expanded = vehicleDropdownExpanded,
                            onDismissRequest = { vehicleDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E))
                        ) {
                            DropdownMenuItem(
                                text = { Text("No Vehicle Selected", color = Color.White) },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null, tint = Color.Gray) },
                                onClick = {
                                    selectedVehicle = null
                                    vehicleDropdownExpanded = false
                                }
                            )
                            vehiclesList.forEach { vehicle ->
                                val itemIcon = if (vehicle.type.lowercase() == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike
                                val itemColor = if (vehicle.isOccasional) Color(0xFF00E5FF) else ColorVelocity
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (vehicle.make.isBlank() && vehicle.model.isBlank()) vehicle.name else "${vehicle.make} ${vehicle.model} (${vehicle.name})",
                                                color = Color.White
                                            )
                                            if (vehicle.isOccasional) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .border(0.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Occasional", color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(itemIcon, contentDescription = null, tint = itemColor) },
                                    onClick = {
                                        selectedVehicle = vehicle
                                        val newMode = if (vehicle.type.lowercase() == "car") VehicleMode.CAR else VehicleMode.BIKE
                                        vehicleMode = newMode
                                        vehicleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Vertical Divider line between Left and Right halves - Always Visible!
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.08f))
                    )

                    // Right Half: Bluetooth Auto-Ride & Alerts overlay
                    val effectiveVehicleName = trackingState.connectedDeviceName
                    val effectiveConnectionLabel = trackingState.connectionLabel
                    val hasDevice = effectiveVehicleName != null
                    if (hasDevice) {
                        val isDeviceDisconnected = trackingState.disconnectTimeMs != null
                        val themeColor = if (trackingState.isManualOverride) Color(0xFFFFB300) else if (isDeviceDisconnected) Color(0xFFFF5252) else Color(0xFF00E5FF)
                        val statusText = remember(
                            trackingState.connectedDevices,
                            effectiveVehicleName,
                            trackingState.isPreRecording,
                            isDeviceDisconnected,
                            trackingState.autoStopRemainingSeconds,
                            trackingState.isManualOverride,
                            effectiveConnectionLabel
                        ) {
                            val activeName = effectiveVehicleName ?: "device"
                            val remaining = trackingState.autoStopRemainingSeconds
                            val countdownStr = if (remaining != null && remaining <= 30) " (Stopping in ${remaining}s)" else ""
                            
                            val labelPrefix = when (effectiveConnectionLabel) {
                                "Dash Only" -> "🎛 Dash Only"
                                "Dash + Helmet" -> "🎛 Dash + Helmet"
                                "Helmet Only" -> "🪖 Helmet Only"
                                else -> effectiveConnectionLabel
                            }
                            
                            if (trackingState.isManualOverride) {
                                "Manual Mode"
                            } else if (isDeviceDisconnected) {
                                if (labelPrefix != null) {
                                    "Lost$countdownStr | $labelPrefix"
                                } else {
                                    "Lost$countdownStr | $activeName"
                                }
                            } else if (trackingState.isPreRecording) {
                                if (labelPrefix != null) {
                                    "$labelPrefix | Waiting"
                                } else {
                                    "Waiting Movement"
                                }
                            } else {
                                labelPrefix ?: "Auto-Track"
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth Status",
                                tint = themeColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = statusText,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (isDeviceDisconnected && !trackingState.isManualOverride) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    modifier = Modifier
                                        .clickable {
                                            val keepIntent = Intent(context, RideTrackerService::class.java).apply {
                                                action = RideTrackerService.ACTION_DISMISS_DISCONNECT_WARNING
                                            }
                                            context.startService(keepIntent)
                                        }
                                        .wrapContentWidth()
                                ) {
                                    Text(
                                        text = "Keep Active",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (trackingState.isPreRecording && !isDeviceDisconnected) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Cancel Auto-Detect",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            val stopIntent = Intent(context, RideTrackerService::class.java).apply {
                                                action = RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                            }
                                            context.startService(stopIntent)
                                        }
                                )
                            }
                        }
                    } else {
                        // Bluetooth Offline / Disconnected State
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth Offline",
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "BT: Disconnected",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Floating Re-center button (Target/Gps) - standard map style, does not overlap compass
        if (pipFraction < 1f) {
            IconButton(
                onClick = onForceRecenter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = animPanelHeight + 82.dp) // Higher than Red/Green buttons
                    .size(48.dp)
                    .background(
                        if (autoCenter) ColorVelocity.copy(alpha = 0.25f)
                        else Color.Black.copy(alpha = 0.65f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.GpsFixed, "Center",
                    tint = if (autoCenter) ColorVelocity else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (pipFraction < 1f) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = animPanelHeight + 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trackingState.isTracking) {
                    // Pause/Resume Button
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(context, RideTrackerService::class.java).apply {
                                action = if (trackingState.isPaused) RideTrackerService.ACTION_RESUME
                                         else RideTrackerService.ACTION_PAUSE
                            }
                            context.startService(intent)
                        },
                        containerColor = if (trackingState.isPaused) ColorAccel else ColorVelocity,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            if (trackingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (trackingState.isPaused) "Resume Tracking" else "Pause Tracking",
                            tint = Color.White
                        )
                    }

                    // Stop Button
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(context, RideTrackerService::class.java).apply {
                                action = RideTrackerService.ACTION_STOP
                            }
                            context.startService(intent)
                        },
                        containerColor = ColorBrake,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            "Stop Tracking",
                            tint = Color.White
                        )
                    }
                } else {
                    if (trackingState.isPreRecording) {
                        FloatingActionButton(
                            onClick = {
                                val stopIntent = Intent(context, RideTrackerService::class.java).apply {
                                    action = RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                }
                                context.startService(stopIntent)
                            },
                            containerColor = ColorBrake,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Cancel Auto-Detect",
                                tint = Color.White
                            )
                        }
                    }
                    
                    // Start Button
                    FloatingActionButton(
                        onClick = {
                            if (!permissionsGranted) {
                                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                        return@FloatingActionButton
                                    }
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                        return@FloatingActionButton
                                    }
                                    
                                    val intent = Intent(context, RideTrackerService::class.java).apply {
                                        action = RideTrackerService.ACTION_START
                                        putExtra("VEHICLE_MODE", vehicleMode.name)
                                        selectedVehicle?.id?.let { putExtra("VEHICLE_ID", it) }
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                        },
                        containerColor = ColorAccel,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "Start Tracking",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Performance Page button (Speedometer icon) - bottom left corner of the map
        if (pipFraction < 1f) {
            IconButton(
                onClick = onNavigateToPerformance,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = animPanelHeight + 14.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Performance Mode",
                    tint = ColorVelocity,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ── Draggable bottom telemetry panel ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animPanelHeight)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color(0xFF0D0D0D))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Drag handle ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val h = panelHeightDp.value
                                    val maxH = screenHeightPx / density.density
                                    panelHeightDp = when {
                                        h < 112f -> 28.dp
                                        h < 273f -> 190.dp
                                        h < 500f -> 356.dp
                                        else -> maxH.dp
                                    }
                                    onForceRecenter()
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / density.density
                                val maxH = screenHeightPx / density.density
                                panelHeightDp = (panelHeightDp + delta.dp).coerceIn(28.dp, maxH.dp)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .background(Color(0xFF3D3D3D), RoundedCornerShape(2.dp))
                    )
                }

                // ── Bento telemetry grid ─────────────────────────────────────
                val rowHeightPx = with(density) { 166.dp.toPx() } // 160.dp card + 6.dp spacing
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        .onSizeChanged { gridWidthPx = maxOf(1f, it.width.toFloat()) }
                        .pointerInput(widgetOrder) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset: androidx.compose.ui.geometry.Offset ->
                                    val col = (offset.x / (gridWidthPx / 2f)).toInt().coerceIn(0, 1)
                                    val row = (offset.y / rowHeightPx).toInt()
                                    val idx = row * 2 + col
                                    if (idx in widgetOrder.indices) {
                                        draggedWidgetIdx = idx
                                        hoverTargetIdx = idx
                                        dragCurrentOffset = offset.copy(
                                            x = offset.x - (gridWidthPx / 4f),
                                            y = offset.y - (rowHeightPx / 2f)
                                        )
                                    }
                                },
                                onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                    if (draggedWidgetIdx != -1) {
                                        dragCurrentOffset += dragAmount
                                        val col = (change.position.x / (gridWidthPx / 2f)).toInt().coerceIn(0, 1)
                                        val row = (change.position.y / rowHeightPx).toInt()
                                        val targetIdx = (row * 2 + col).coerceIn(0, widgetOrder.size - 1)
                                        
                                        val targetW = widgetOrder[targetIdx]
                                        val draggedW = widgetOrder[draggedWidgetIdx]
                                        val isValid = if (targetW == WidgetType.ADD_NEW || draggedW == WidgetType.ADD_NEW) {
                                            targetIdx >= widgetOrder.size - 2 && draggedWidgetIdx >= widgetOrder.size - 2
                                        } else true
                                        
                                        if (isValid) {
                                            hoverTargetIdx = targetIdx
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val isTargetValid = if (draggedWidgetIdx != -1 && hoverTargetIdx != -1) {
                                        val targetW = widgetOrder[hoverTargetIdx]
                                        val draggedW = widgetOrder[draggedWidgetIdx]
                                        if (targetW == WidgetType.ADD_NEW || draggedW == WidgetType.ADD_NEW) {
                                            hoverTargetIdx >= widgetOrder.size - 2 && draggedWidgetIdx >= widgetOrder.size - 2
                                        } else true
                                    } else false

                                    if (isTargetValid && draggedWidgetIdx != hoverTargetIdx) {
                                        val list = widgetOrder.toMutableList()
                                        val item = list.removeAt(draggedWidgetIdx)
                                        list.add(hoverTargetIdx, item)
                                        widgetOrder = list
                                    }
                                    draggedWidgetIdx = -1
                                    hoverTargetIdx = -1
                                },
                                onDragCancel = {
                                    draggedWidgetIdx = -1
                                    hoverTargetIdx = -1
                                }
                            )
                        }
                ) {
                    // Draw widgets in grid based on widgetOrder
                    val cardWidthPx = (gridWidthPx - with(density) { 6.dp.toPx() }) / 2f
                    val spacingPx = with(density) { 6.dp.toPx() }
                    
                    Box(modifier = Modifier.fillMaxWidth().height(with(density) { (rowHeightPx * 3).toDp() })) {
                        widgetOrder.forEachIndexed { index, widget ->
                            val isDragged = index == draggedWidgetIdx
                            
                            val targetCol = index % 2
                            val targetRow = index / 2
                            val targetX = targetCol * (cardWidthPx + spacingPx)
                            val targetY = targetRow * rowHeightPx
                            
                            val animatedOffset by animateOffsetAsState(
                                targetValue = if (isDragged) dragCurrentOffset else androidx.compose.ui.geometry.Offset(targetX, targetY),
                                animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                                label = "offsetAnim"
                            )
                            
                            val cardModifier = Modifier
                                .width(with(density) { cardWidthPx.toDp() })
                                .height(160.dp)
                                .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    val isHovered = index == hoverTargetIdx && draggedWidgetIdx != -1 && !isDragged
                                    scaleX = if (isDragged) 1.05f else if (isHovered) 0.95f else 1f
                                    scaleY = if (isDragged) 1.05f else if (isHovered) 0.95f else 1f
                                    shadowElevation = if (isDragged) 16.dp.toPx() else 0f
                                    alpha = if (isDragged) 0.85f else 1f
                                }
                                .drawWithContent {
                                    drawContent()
                                    if (index == hoverTargetIdx && draggedWidgetIdx != -1 && !isDragged) {
                                        drawRect(androidx.compose.ui.graphics.Color(0xFF9C27B0).copy(alpha = 0.3f), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                                    }
                                }

                            when (widget) {
                                WidgetType.SPEED -> {
                                    BentoCard(title = "A  Speedometer", modifier = cardModifier) {
                                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    "${trackingState.currentSpeedKmh.toInt()}",
                                                    color = Color.White,
                                                    fontSize = 44.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Column(modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                                                    Text("KM/H", color = ColorVelocity, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    val avgSpd = if (trackingState.points.isNotEmpty()) trackingState.points.map { it.speedMetersPerSec }.average() * 3.6 else 0.0
                                                    Text("Avg: ${avgSpd.toInt()}", color = Color.Gray, fontSize = 10.sp)
                                                }
                                            }
                                            SpeedLiveGraph(history = trackingState.speedHistory)
                                        }
                                    }
                                }
                                WidgetType.DURATION -> {
                                    BentoCard(title = "B  Duration & Distance", modifier = cardModifier) {
                                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                            val h = trackingState.durationSeconds / 3600
                                            val m = (trackingState.durationSeconds % 3600) / 60
                                            val s = trackingState.durationSeconds % 60
                                            val durationStr = if (h > 0) {
                                                String.format("%d hr %02d min", h, m)
                                            } else {
                                                String.format("%02d:%02d", m, s)
                                            }
                                            Text("Duration", color = Color.Gray, fontSize = 10.sp)
                                            Text(
                                                durationStr,
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Text("Distance", color = Color.Gray, fontSize = 10.sp)
                                            Text(
                                                String.format("%.2f km", trackingState.totalDistanceMeters / 1000f),
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                WidgetType.LEAN_OR_GFORCE -> {
                                    val showLeanModule = vehicleMode == VehicleMode.BIKE || (vehicleMode == VehicleMode.AUTO && detectedAsBike)
                                    if (showLeanModule) {
                                        BentoCard(title = "C  2W Module — Lean Angle", modifier = cardModifier) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BentoLeanAngleVisualizer(leanAngle = currentLeanAngle) }
                                        }
                                    } else {
                                        BentoCard(title = "C  4W Module — Lateral G", modifier = cardModifier) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BentoLateralGBar(lateralG = currentLatG) }
                                        }
                                    }
                                }
                                WidgetType.GFORCE_CIRCLE -> {
                                    BentoCard(title = "D  G-Force Circle", modifier = cardModifier) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            BentoGForceMeter(
                                                lateralG = currentLatG,
                                                longitudinalG = currentLongG,
                                                sensitivity = gForceSensitivity
                                            )
                                        }
                                    }
                                }
                                WidgetType.ELEVATION -> {
                                    BentoCard(title = "E  Elevation", modifier = cardModifier) {
                                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                val currentEl = trackingState.points.lastOrNull()?.altitude ?: liveLocation?.altitude ?: 0.0
                                                Text(
                                                    "${currentEl.toInt()}",
                                                    color = Color.White,
                                                    fontSize = 32.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                                                    Text("M", color = Color(0xFF9C27B0), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    val avgEl = if (trackingState.points.isNotEmpty()) trackingState.points.map { it.altitude }.average() else currentEl
                                                    Text("Avg: ${avgEl.toInt()}", color = Color.Gray, fontSize = 10.sp)
                                                }
                                            }
                                            ElevationLiveGraph(points = trackingState.points, fallbackAltitude = liveLocation?.altitude)
                                        }
                                    }
                                }
                                WidgetType.ADD_NEW -> {
                                    Box(
                                        modifier = cardModifier
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = Color.Gray.copy(alpha = 0.5f),
                                                    style = Stroke(
                                                        width = 2.dp.toPx(),
                                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                                                    ),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx())
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Add, "Add Widget", tint = Color.Gray, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!isLocationEnabled) {
        Dialog(
            onDismissRequest = { /* Force action, do not dismiss */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.95f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .border(1.dp, PortalTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        tint = Color(0xFFFF3366),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Location Services Disabled",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To locate your cockpit node, center geographical maps, and download live weather telemetry, please enable device location services.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PortalTheme.colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Enable Location", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// Mappls Map View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MapplsVectorMapView(
    modifier:        Modifier = Modifier,
    bottomPaddingDp: androidx.compose.ui.unit.Dp = 0.dp,
    trackingState:   RideTrackerService.Companion.TrackingState,
    currentLocation: Location?,
    vehicleMode:     VehicleMode,
    detectedAsBike:  Boolean,
    autoCenter:      Boolean,
    recenterTrigger: Long,
    is3D:            Boolean = false,
    onUserInteract:  () -> Unit,
    onSegmentClick:  (com.nexus.portal.data.local.ridetracker.RideLocationPoint?) -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView        = remember {
        try {
            MapView(context)
        } catch (t: Throwable) {
            android.util.Log.e("RideTrackerScreen", "Failed to initialize MapView due to credentials mismatch", t)
            null
        }
    }

    // Map state
    var mapplsMap     by remember { mutableStateOf<MapplsMap?>(null) }
    val drawnPolylines = remember { mutableListOf<Polyline>() }
    val drawnKmMarkers = remember { mutableListOf<Marker>() }
    val polylineMap    = remember { mutableMapOf<Polyline, com.nexus.portal.data.local.ridetracker.RideLocationPoint>() }
    var drawnPointsCount by remember { mutableStateOf(0) }
    var lastKmMarked   by remember { mutableStateOf(0) }
    var locationMarker  by remember { mutableStateOf<Marker?>(null) }
 
    LaunchedEffect(is3D, mapplsMap) {
        val map = mapplsMap ?: return@LaunchedEffect
        try {
            val currentCamera = map.cameraPosition
            val targetTilt = if (is3D) 55.0 else 0.0
            val cameraPos = com.mappls.sdk.maps.camera.CameraPosition.Builder(currentCamera)
                .tilt(targetTilt)
                .build()
            map.easeCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(cameraPos), 400, false)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Track which mode the current marker was created for → only recreate on change
    var markerVehicleMode by remember { mutableStateOf<VehicleMode?>(null) }
    var markerIsBike      by remember { mutableStateOf(false) }

    // For threshold-based camera: skip animation if barely moved (avoid jitter)
    var lastCenteredLat by remember { mutableStateOf(0.0) }
    var lastCenteredLng by remember { mutableStateOf(0.0) }
    var lastCenteredHeading by remember { mutableStateOf(0f) }
    var lastCameraUpdateTime by remember { mutableStateOf(0L) }
    var lastHandledTrigger by remember { mutableStateOf(0L) }

    // Pre-build icons once to avoid per-frame bitmap allocations
    val carIcon  = remember { IconFactory.getInstance(context).fromBitmap(createCarMarkerBitmap()) }
    val bikeIcon = remember { IconFactory.getInstance(context).fromBitmap(createBikeMarkerBitmap()) }
    val autoCarIcon  = remember { IconFactory.getInstance(context).fromBitmap(createAutoMarkerBitmap(false)) }
    val autoBikeIcon = remember { IconFactory.getInstance(context).fromBitmap(createAutoMarkerBitmap(true)) }

    if (mapView != null) {
        // Lifecycle wiring
        DisposableEffect(lifecycleOwner, mapView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE  -> mapView.onCreate(Bundle())
                    Lifecycle.Event.ON_START   -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                    Lifecycle.Event.ON_STOP    -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        AndroidView(
            factory = { ctx ->
                mapView.apply {
                    getMapAsync(object : OnMapReadyCallback {
                        override fun onMapReady(map: MapplsMap) {
                            mapplsMap = map
                            map.uiSettings?.isLogoEnabled        = false
                            map.uiSettings?.isAttributionEnabled = false

                            // Gesture → disable autoCenter
                            map.addOnCameraMoveStartedListener { reason ->
                                if (reason == MapplsMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                    onUserInteract()
                                }
                            }

                            map.setOnPolylineClickListener { polyline ->
                                val pt = polylineMap[polyline]
                                onSegmentClick(pt)
                            }
                            
                            map.addOnMapClickListener {
                                onSegmentClick(null)
                                false
                            }

                            // Initial center: use last-known location immediately
                            try {
                                LocationServices.getFusedLocationProviderClient(ctx)
                                    .lastLocation
                                    .addOnSuccessListener { loc ->
                                        if (loc != null) {
                                            map.moveCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(loc.latitude, loc.longitude), 17.0
                                                )
                                            )
                                        }
                                    }
                            } catch (_: SecurityException) {}
                        }
                        override fun onMapError(p0: Int, p1: String?) {}
                    })
                }
            },
            modifier = modifier.fillMaxSize(),
            update = { _ ->
                val map = mapplsMap ?: return@AndroidView
                val paddingPx = with(context.resources.displayMetrics.density) { bottomPaddingDp.value * this }.toInt()
                // Add top padding to account for the top bar and console overlays, lowering the auto-center target visually
                val topPaddingPx = with(context.resources.displayMetrics.density) { 130.dp.value * this }.toInt()
                map.setPadding(0, topPaddingPx, 0, paddingPx)

                // ── Determine correct icon ────────────────────────────────────────
                val icon = when (vehicleMode) {
                    VehicleMode.CAR  -> carIcon
                    VehicleMode.BIKE -> bikeIcon
                    VehicleMode.AUTO -> if (detectedAsBike) autoBikeIcon else autoCarIcon
                }
                val needsNewMarker = locationMarker == null ||
                        markerVehicleMode != vehicleMode ||
                        (vehicleMode == VehicleMode.AUTO && markerIsBike != detectedAsBike)

                // ── Current location marker ──────────────────────────────────────
                val loc = currentLocation
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)

                    if (needsNewMarker) {
                        // Recreate marker with correct icon — only happens on mode change
                        try {
                            locationMarker?.remove()
                            locationMarker = map.addMarker(
                                MarkerOptions().position(latLng).icon(icon)
                            )
                            markerVehicleMode = vehicleMode
                            markerIsBike      = detectedAsBike
                        } catch (e: Exception) { e.printStackTrace() }
                    } else {
                        // Update position in-place → NO flickering
                        try {
                            locationMarker?.setPosition(latLng)
                        } catch (e: Exception) {
                            // Fallback if setPosition unavailable
                            try {
                                locationMarker?.remove()
                                locationMarker = map.addMarker(MarkerOptions().position(latLng).icon(icon))
                            } catch (e2: Exception) { e2.printStackTrace() }
                        }
                    }

                    // ── Camera auto-center ────────────────────────────────────────
                    if (autoCenter) {
                        val speed = trackingState.currentSpeedKmh
                        val zoom  = when {
                            speed < 15f -> 17.5
                            speed < 40f -> 16.5
                            speed < 80f -> 15.5
                            else        -> 14.5
                        }

                        val forceNow  = recenterTrigger != lastHandledTrigger
                        val dLat      = loc.latitude  - lastCenteredLat
                        val dLng      = loc.longitude - lastCenteredLng
                        val movedEnough = dLat * dLat + dLng * dLng > 0.000000002 // ~5 m²

                        val heading = trackingState.compassHeading
                        var dHeading = heading - (lastCenteredHeading % 360f)
                        if (dHeading > 180f) dHeading -= 360f
                        if (dHeading < -180f) dHeading += 360f

                        val headingChanged = Math.abs(dHeading) > 1.0f

                        val now = System.currentTimeMillis()
                        val timeOk = (now - lastCameraUpdateTime) > 250L // 4 fps smooth interpolation

                        if (forceNow || ((movedEnough || headingChanged) && timeOk)) {
                            try {
                                val duration = if (forceNow) 500 else 250
                                val targetHeading = lastCenteredHeading + dHeading

                                val cameraPos = com.mappls.sdk.maps.camera.CameraPosition.Builder()
                                    .target(latLng)
                                    .zoom(zoom)
                                    .bearing(targetHeading.toDouble())
                                    .tilt(if (is3D) 55.0 else 0.0)
                                    .build()
                                
                                // easeCamera allows smooth linear interpolation without the jarring cancel-jump
                                map.easeCamera(
                                    CameraUpdateFactory.newCameraPosition(cameraPos), duration, false
                                )
                                lastCenteredLat      = loc.latitude
                                lastCenteredLng      = loc.longitude
                                lastCenteredHeading  = targetHeading
                                lastHandledTrigger   = recenterTrigger
                                lastCameraUpdateTime = now
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }

                // ── Route polyline (only while tracking) ─────────────────────────
                if (trackingState.isTracking) {
                    val pts = trackingState.points
                    if (pts.size > drawnPointsCount) {
                        try {
                            if (drawnPointsCount == 0 && pts.isNotEmpty()) {
                                val startPt = pts.first()
                                val startIcon = com.mappls.sdk.maps.annotations.IconFactory.getInstance(context).fromBitmap(createStartMarkerBitmap())
                                map.addMarker(com.mappls.sdk.maps.annotations.MarkerOptions().position(LatLng(startPt.latitude, startPt.longitude)).icon(startIcon))
                            }
                            for (i in maxOf(1, drawnPointsCount) until pts.size) {
                                val pt1 = pts[i - 1]
                                val pt2 = pts[i]
                                val latLng1 = LatLng(pt1.latitude, pt1.longitude)
                                val latLng2 = LatLng(pt2.latitude, pt2.longitude)
                                
                                val speedKmh = pt2.speedMetersPerSec * 3.6f
                                val color = when {
                                    speedKmh < 15f -> Color(0xFF2196F3) // Blue
                                    speedKmh < 30f -> Color(0xFF4CAF50) // Green
                                    speedKmh < 50f -> Color(0xFFFFEB3B) // Yellow
                                    speedKmh < 70f -> Color(0xFFFF9800) // Orange
                                    else -> Color(0xFFF44336) // Red
                                }
                                
                                val polyline = map.addPolyline(
                                    PolylineOptions()
                                        .add(latLng1, latLng2)
                                        .color(color.toArgb())
                                        .width(5f)
                                )
                                if (polyline != null) {
                                    drawnPolylines.add(polyline)
                                    polylineMap[polyline] = pt2
                                }
                            }
                            drawnPointsCount = pts.size
                            
                            // Check for km markers
                            val currentKm = (trackingState.totalDistanceMeters / 1000).toInt()
                            if (currentKm > lastKmMarked) {
                                val loc = pts.last()
                                val markerBm = createKmMarkerBitmap(currentKm)
                                val icon = com.mappls.sdk.maps.annotations.IconFactory.getInstance(context).fromBitmap(markerBm)
                                val marker = map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(loc.latitude, loc.longitude))
                                        .icon(icon)
                                        .title("${currentKm} km")
                                )
                                if (marker != null) drawnKmMarkers.add(marker)
                                lastKmMarked = currentKm
                            }
                            
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                } else if (!trackingState.isTracking && drawnPointsCount > 0) {
                    try {
                        drawnPolylines.forEach { map.removePolyline(it) }
                        drawnKmMarkers.forEach { map.removeMarker(it) }
                    } catch (_: Exception) {}
                    drawnPolylines.clear()
                    drawnKmMarkers.clear()
                    polylineMap.clear()
                    drawnPointsCount = 0
                    lastKmMarked = 0
                }
            }
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(PortalTheme.colors.surfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Map Error",
                    tint = PortalTheme.colors.textSecondary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Map integration offline (API Keys Mismatch)",
                    color = PortalTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vehicle Selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VehicleSelector(
    selected:  VehicleMode,
    isTracking: Boolean,
    onSelect:  (VehicleMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selected)
    val activeTint = when (selected) {
        VehicleMode.CAR  -> ColorVelocity
        VehicleMode.BIKE -> ColorBike
        VehicleMode.AUTO -> ColorAuto
    }

    Box {
        IconButton(
            onClick = { if (!isTracking) expanded = true },
            modifier = Modifier
                .size(42.dp)
                .pointerInput(isTracking) {
                    if (!isTracking) {
                        var totalDrag = 0f
                        var changedInThisSwipe = false
                        detectVerticalDragGestures(
                            onDragEnd = { 
                                totalDrag = 0f 
                                changedInThisSwipe = false
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (changedInThisSwipe) return@detectVerticalDragGestures
                            totalDrag += dragAmount
                            if (totalDrag < -30f) { // Swiped up
                                val next = when(currentSelected) {
                                    VehicleMode.CAR -> VehicleMode.AUTO
                                    VehicleMode.BIKE -> VehicleMode.CAR
                                    VehicleMode.AUTO -> VehicleMode.BIKE
                                }
                                onSelect(next)
                                changedInThisSwipe = true
                            } else if (totalDrag > 30f) { // Swiped down
                                val next = when(currentSelected) {
                                    VehicleMode.CAR -> VehicleMode.BIKE
                                    VehicleMode.BIKE -> VehicleMode.AUTO
                                    VehicleMode.AUTO -> VehicleMode.CAR
                                }
                                onSelect(next)
                                changedInThisSwipe = true
                            }
                        }
                    }
                }
                .background(
                    if (isTracking) Color.DarkGray.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.65f), 
                    CircleShape
                )
                .border(
                    BorderStroke(1.dp, if (isTracking) Color.Gray else activeTint.copy(alpha=0.5f)),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = when (selected) {
                    VehicleMode.CAR  -> Icons.Default.DirectionsCar
                    VehicleMode.BIKE -> Icons.Default.DirectionsBike
                    VehicleMode.AUTO -> Icons.Default.GpsFixed
                },
                contentDescription = selected.label,
                tint = if (isTracking) Color.Gray else activeTint,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            VehicleMode.entries.forEach { mode ->
                val tint = when (mode) {
                    VehicleMode.CAR  -> ColorVelocity
                    VehicleMode.BIKE -> ColorBike
                    VehicleMode.AUTO -> ColorAuto
                }
                DropdownMenuItem(
                    text = { Text(mode.label, color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mode) {
                                VehicleMode.CAR  -> Icons.Default.DirectionsCar
                                VehicleMode.BIKE -> Icons.Default.DirectionsBike
                                VehicleMode.AUTO -> Icons.Default.GpsFixed
                            },
                            contentDescription = mode.label,
                            tint = tint
                        )
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bento Card Shell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BentoCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                title,
                color = Color(0xFF606060),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Speed Live Graph
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpeedLiveGraph(history: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
        if (history.size < 2) {
            drawLine(
                color = ColorVelocity.copy(alpha = 0.25f),
                start = Offset(0f, size.height),
                end   = Offset(size.width, size.height),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }
        val maxVal = history.max().coerceAtLeast(1f)
        val stepX  = size.width / (history.size - 1).toFloat()
        val linePath = Path()
        val fillPath = Path()

        history.forEachIndexed { i, speed ->
            val x  = i * stepX
            val y  = size.height - (speed / maxVal * size.height * 0.88f)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                val px   = (i - 1) * stepX
                val py   = size.height - (history[i - 1] / maxVal * size.height * 0.88f)
                val cpX  = (px + x) / 2f
                linePath.cubicTo(cpX, py, cpX, y, x, y)
                fillPath.cubicTo(cpX, py, cpX, y, x, y)
            }
        }
        fillPath.lineTo((history.size - 1) * stepX, size.height)
        fillPath.close()

        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(ColorVelocity.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f, endY = size.height
            )
        )
        drawPath(linePath, color = ColorVelocity, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

        val lx = (history.size - 1) * stepX
        val ly = size.height - (history.last() / maxVal * size.height * 0.88f)
        drawCircle(ColorVelocity.copy(alpha = 0.3f), 8.dp.toPx(), Offset(lx, ly))
        drawCircle(ColorVelocity, 4.dp.toPx(), Offset(lx, ly))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lean Angle (2W) — orientation-independent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BentoLeanAngleVisualizer(leanAngle: Float) {
    val clamped = leanAngle.coerceIn(-90f, 90f)
    val tint    = if (abs(clamped) > 40f) ColorBrake else ColorBike

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(78.dp)) {
            val cx     = size.width / 2f
            val cy     = size.height * 0.84f
            val radius = size.width * 0.41f

            // Track arc
            drawArc(
                color      = Color.White.copy(alpha = 0.07f),
                startAngle = 180f, sweepAngle = 180f,
                useCenter  = false,
                topLeft    = Offset(cx - radius, cy - radius),
                size       = Size(radius * 2, radius * 2),
                style      = Stroke(6.dp.toPx())
            )

            // Colored active arc
            val sweep = (clamped / 90f) * 90f
            if (abs(sweep) > 0.5f) {
                val start = if (sweep >= 0f) 270f else 270f + sweep
                drawArc(
                    color      = tint,
                    startAngle = start,
                    sweepAngle = abs(sweep),
                    useCenter  = false,
                    topLeft    = Offset(cx - radius, cy - radius),
                    size       = Size(radius * 2, radius * 2),
                    style      = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Needle
            val needleRad = Math.toRadians(270.0 + clamped)
            val nx = cx + radius * cos(needleRad).toFloat()
            val ny = cy + radius * sin(needleRad).toFloat()
            drawLine(tint, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = 0.4f), 4.dp.toPx(), Offset(cx, cy))
        }
        val l = if (clamped < 0f) "${abs(clamped.toInt())}°" else "—"
        val r = if (clamped > 0f) "${abs(clamped.toInt())}°" else "—"
        Text("L: $l  |  R: $r", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lateral G Bar (4W)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BentoLateralGBar(lateralG: Float) {
    val maxG    = 1.5f
    val clamped = lateralG.coerceIn(-maxG, maxG)
    val frac    = (clamped / maxG).coerceIn(-1f, 1f)
    val tint    = if (abs(clamped) > 0.5f) ColorWarn else ColorVelocity

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lateral", color = Color.Gray, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            val cx = size.width / 2f
            val r  = size.height / 2f

            // Track
            drawRoundRect(
                color       = Color.White.copy(alpha = 0.07f),
                topLeft     = Offset(0f, r / 2f),
                size        = Size(size.width, r),
                cornerRadius = CornerRadius(r / 2f)
            )
            // Filled portion — from center to side
            val filledW = abs(frac) * cx
            val startX  = if (frac >= 0f) cx else cx - filledW
            drawRoundRect(
                color       = tint,
                topLeft     = Offset(startX, r / 2f),
                size        = Size(filledW, r),
                cornerRadius = CornerRadius(r / 2f)
            )
            // Center tick
            drawLine(Color.White.copy(0.4f), Offset(cx, 0f), Offset(cx, size.height), 1.5f)
        }

        Spacer(Modifier.height(6.dp))
        val dir = when {
            frac > 0.15f  -> "RIGHT"
            frac < -0.15f -> "LEFT"
            else          -> "CENTER"
        }
        Text(
            String.format("%.2fG  $dir", abs(clamped)),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// G-Force Circle (4-direction)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BentoGForceMeter(lateralG: Float, longitudinalG: Float, sensitivity: Float = 1.0f) {
    val maxG    = 1.5f
    
    // Scale G-Forces for visual representation based on sensitivity setting
    val visualLat = lateralG * sensitivity
    val visualLon = longitudinalG * sensitivity
    val visualTotalG = sqrt(visualLat * visualLat + visualLon * visualLon)

    val dotColor = when {
        visualLon > 0.25f && abs(visualLat) < 0.3f  -> ColorAccel
        visualLon < -0.25f && abs(visualLat) < 0.3f -> ColorBrake
        abs(visualLat) >= 0.3f                      -> ColorWarn
        else                                        -> ColorNeutral
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(78.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 4.dp.toPx()

            drawCircle(Color.White.copy(0.07f), radius, style = Stroke(1.5f))
            drawCircle(Color.White.copy(0.05f), radius * 0.5f, style = Stroke(1f))
            drawLine(Color.White.copy(0.07f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
            drawLine(Color.White.copy(0.07f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))

            val scaledRatio = if (visualTotalG > 0f) {
                val ratio = (visualTotalG / maxG).coerceIn(0f, 1f)
                sqrt(ratio)
            } else {
                0f
            }

            val dotX = if (visualTotalG > 0f) center.x + (visualLat / visualTotalG) * scaledRatio * radius else center.x
            val dotY = if (visualTotalG > 0f) center.y - (visualLon / visualTotalG) * scaledRatio * radius else center.y

            drawCircle(dotColor.copy(alpha = 0.28f), 11.dp.toPx(), Offset(dotX, dotY))
            drawCircle(dotColor, 5.dp.toPx(), Offset(dotX, dotY))
        }
        Text(
            "Lat: ${String.format("%.2f", lateralG)} | Lon: ${String.format("%.2f", longitudinalG)}",
            color = Color.White.copy(0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun SegmentDetailsCard(point: com.nexus.portal.data.local.ridetracker.RideLocationPoint, onClose: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp).width(200.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Segment Telemetry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryItem("Speed", "${(point.speedMetersPerSec * 3.6).toInt()} km/h")
                TelemetryItem("Elevation", "${point.altitude.toInt()} m")
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryItem("G-Force", String.format("%.2f G", point.gForce))
                TelemetryItem("Lean", "${point.leanAngle.toInt()}°")
            }
        }
    }
}

@Composable
fun TelemetryItem(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ElevationLiveGraph(points: List<com.nexus.portal.data.local.ridetracker.RideLocationPoint>, fallbackAltitude: Double? = null) {
    if (points.size < 2) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 16.dp)) {
            val y = size.height / 2f
            drawLine(
                color = Color(0xFF9C27B0).copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
            )
        }
        return
    }
    
    val elevations = points.map { it.altitude.toFloat() }.takeLast(40)
    val minE = elevations.minOrNull() ?: 0f
    val maxE = elevations.maxOrNull() ?: 100f
    val range = maxOf(maxE - minE, 10f)
    
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 16.dp)) {
        val path = Path()
        val stepX = size.width / maxOf(1, elevations.size - 1)
        
        elevations.forEachIndexed { i, e ->
            val x = i * stepX
            val y = size.height - ((e - minE) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = Color(0xFF9C27B0),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
