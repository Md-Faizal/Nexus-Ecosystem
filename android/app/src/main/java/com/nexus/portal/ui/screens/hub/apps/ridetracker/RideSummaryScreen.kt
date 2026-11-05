package com.nexus.portal.ui.screens.hub.apps.ridetracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.viewinterop.AndroidView
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import com.nexus.portal.data.local.ridetracker.RideSession
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// Colors from Mockup
val ColorSpeed = Color(0xFF00E5FF)
val ColorLean = Color(0xFFFFB300) // Amber for lean
val ColorBrakeGraph = Color(0xFFFF1744)
val ColorFlow = Color(0xFF00E676)

enum class SummaryWidgetType(val span: Int) { 
    SCORE(2), STATS(2), TIMELINE(2), SECTORS(2), ALTITUDE(2), SPEED(2), LEAN(1), GFORCE(1) 
}

@Composable
fun RideSummaryScreen(onBackClick: () -> Unit, sessionId: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ride_summary_prefs", android.content.Context.MODE_PRIVATE) }
    var session by remember { mutableStateOf<com.nexus.portal.data.local.ridetracker.RideSession?>(null) }
    var isOwnRide by remember { mutableStateOf(true) }
    var sessionVehicle by remember { mutableStateOf<com.nexus.portal.data.local.ridetracker.VehicleEntity?>(null) }
    val vehicleDao = remember { com.nexus.portal.data.local.ridetracker.RideDatabase.getDatabase(context).vehicleDao() }
    val vehiclesList by vehicleDao.getAllVehicles().collectAsState(initial = emptyList())
    var showLinkVehicleDialog by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf<List<RideLocationPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val viewModel: RideSummaryViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState) {
        session = uiState.session
        isOwnRide = uiState.isOwnRide
        sessionVehicle = uiState.sessionVehicle
        points = uiState.points
        isLoading = uiState.isLoading
        error = uiState.error
    }
    
    var widgetOrder by remember { mutableStateOf(
        prefs.getString("widget_order", null)?.let { savedOrder ->
            try {
                savedOrder.split(",").map { SummaryWidgetType.valueOf(it) }
            } catch (e: Exception) { null }
        } ?: listOf(
            SummaryWidgetType.LEAN, SummaryWidgetType.GFORCE,
            SummaryWidgetType.SCORE, SummaryWidgetType.STATS, SummaryWidgetType.TIMELINE,
            SummaryWidgetType.SECTORS, SummaryWidgetType.ALTITUDE, SummaryWidgetType.SPEED
        )
    ) }
    
    var draggedWidgetIdx by remember { mutableStateOf(-1) }
    var hoverTargetIdx by remember { mutableStateOf(-1) }
    var dragCurrentOffset by remember { mutableStateOf(Offset.Zero) }
    
    var expandedWidget by remember { mutableStateOf<SummaryWidgetType?>(null) }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var mapplsMapRef by remember { mutableStateOf<MapplsMap?>(null) }
    var scrubbedPoint by remember { mutableStateOf<RideLocationPoint?>(null) }
    var scrubbedPixel by remember { mutableStateOf<Offset?>(null) }
    
    var isAutoScrubbing by remember { mutableStateOf(false) }
    var is3DMode by remember { mutableStateOf(false) }
    var autoScrubIndex by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    
    var isSyncingForShare by remember { mutableStateOf(false) }
    
    LaunchedEffect(isSyncingForShare) {
        if (isSyncingForShare && session != null) {
            android.widget.Toast.makeText(context, "Syncing ride to cloud...", android.widget.Toast.LENGTH_SHORT).show()
            com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
            isSyncingForShare = false
            
            val url = "${com.nexus.portal.core.NetworkConfig.backendUrl}/shared-ride.html?id=${session!!.id}"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "Check out my ride on Nexus Portal: $url")
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Ride Link"))
        }
    }

    val settingsManager = remember { com.nexus.portal.managers.RideSettingsManager.getInstance(context) }
    val globalShowKmMarkers by settingsManager.showKilometerMarkers.collectAsState()
    var localShowKmMarkers by remember(globalShowKmMarkers) { mutableStateOf(globalShowKmMarkers) }
    
    LaunchedEffect(localShowKmMarkers, points, mapplsMapRef) {
        mapplsMapRef?.let { map ->
            if (points.isNotEmpty()) {
                map.clear()
                drawRouteOnMap(context, map, points, localShowKmMarkers)
            }
        }
    }
    
    LaunchedEffect(isAutoScrubbing, points, playbackSpeed, is3DMode) {
        if (isAutoScrubbing && points.isNotEmpty()) {
            while (true) {
                if (autoScrubIndex >= points.size) autoScrubIndex = 0
                val pt = points[autoScrubIndex]
                scrubbedPoint = pt
                val latLng = LatLng(pt.latitude, pt.longitude)
                try {
                    mapplsMapRef?.let { map ->
                        val px = map.projection.toScreenLocation(latLng)
                        scrubbedPixel = Offset(px.x, px.y)
                        
                        val speedKmh = pt.speedMetersPerSec * 3.6f
                        val targetZoom = if (speedKmh <= 15f) {
                            17.0
                        } else if (speedKmh >= 90f) {
                            12.0
                        } else {
                            17.0 - ((speedKmh - 15f) / 75f) * 5.0
                        }
                        
                        var targetBearing = map.cameraPosition.bearing
                        if (autoScrubIndex > 0) {
                            val prevPt = points[autoScrubIndex - 1]
                            if (pt.latitude != prevPt.latitude || pt.longitude != prevPt.longitude) {
                                val loc1 = android.location.Location("").apply { latitude = prevPt.latitude; longitude = prevPt.longitude }
                                val loc2 = android.location.Location("").apply { latitude = pt.latitude; longitude = pt.longitude }
                                targetBearing = loc1.bearingTo(loc2).toDouble()
                            }
                        }
                        
                        val cameraUpdate = com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(
                            com.mappls.sdk.maps.camera.CameraPosition.Builder()
                                .target(latLng)
                                .zoom(targetZoom)
                                .bearing(targetBearing)
                                .tilt(if (is3DMode) 55.0 else 0.0)
                                .build()
                        )
                        map.animateCamera(cameraUpdate, (32L / playbackSpeed).toInt())
                    }
                } catch (e: Exception) {
                    // Ignore map interaction exceptions when navigating back
                }
                autoScrubIndex++
                delay((32L / playbackSpeed).toLong())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        // Map Area Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorFlow)
                }
            } else if (session == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Session details not found.", color = Color.Gray)
                }
            } else if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (session!!.vehicleType == "four_wheeler") "🚗" else "🏍️",
                            fontSize = 48.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text("No GPS route map recorded.", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Stats and details are available below.", color = Color.DarkGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                val s = session!!
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val mapView = remember {
                    try {
                        MapView(context)
                    } catch (t: Throwable) {
                        android.util.Log.e("RideSummaryScreen", "Failed to initialize MapView due to credentials mismatch", t)
                        null
                    }
                }
                
                if (mapView != null) {
                    DisposableEffect(lifecycleOwner, mapView) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_CREATE  -> mapView.onCreate(android.os.Bundle())
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
                        factory = { _ ->
                            mapView.apply {
                                getMapAsync(object : OnMapReadyCallback {
                                        override fun onMapReady(mapplsMap: MapplsMap) {
                                            mapplsMapRef = mapplsMap
                                            mapplsMap.uiSettings?.setAllGesturesEnabled(true)
                                            
                                            val latLngs = points.map { LatLng(it.latitude, it.longitude) }
                                            if (latLngs.isNotEmpty()) {
                                                val boundsBuilder = com.mappls.sdk.maps.geometry.LatLngBounds.Builder()
                                                latLngs.forEach { boundsBuilder.include(it) }
                                                mapplsMap.setPadding(0, 800, 0, 500)
                                                mapplsMap.moveCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 200))
                                            }
                                            
                                            mapplsMap.addOnCameraMoveListener {
                                                val pt = scrubbedPoint
                                                if (pt != null) {
                                                    val px = mapplsMap.projection.toScreenLocation(LatLng(pt.latitude, pt.longitude))
                                                    scrubbedPixel = Offset(px.x, px.y)
                                                }
                                            }
                                        }
                                        
                                        override fun onMapError(p0: Int, p1: String?) {}
                                    })
                                }
                            },
                            update = { view ->
                                // Managed by LaunchedEffect to prevent playback flickering
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(points, mapplsMapRef) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val map = mapplsMapRef ?: return@detectDragGesturesAfterLongPress
                                            val touchLatLng = map.projection.fromScreenLocation(android.graphics.PointF(offset.x, offset.y))
                                            coroutineScope.launch {
                                                MapMathHelper.findClosestPoint(touchLatLng, points)?.let { pt ->
                                                    scrubbedPoint = pt
                                                    val px = map.projection.toScreenLocation(LatLng(pt.latitude, pt.longitude))
                                                    scrubbedPixel = Offset(px.x, px.y)
                                                }
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            val map = mapplsMapRef ?: return@detectDragGesturesAfterLongPress
                                            val touchLatLng = map.projection.fromScreenLocation(android.graphics.PointF(change.position.x, change.position.y))
                                            coroutineScope.launch {
                                                MapMathHelper.findClosestPoint(touchLatLng, points)?.let { pt ->
                                                    scrubbedPoint = pt
                                                    val px = map.projection.toScreenLocation(LatLng(pt.latitude, pt.longitude))
                                                    scrubbedPixel = Offset(px.x, px.y)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            scrubbedPoint = null
                                            scrubbedPixel = null
                                        },
                                        onDragCancel = {
                                            scrubbedPoint = null
                                            scrubbedPixel = null
                                        }
                                    )
                                }
                        )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF141418)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Map Error",
                                tint = Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Map integration offline (API Keys Mismatch)",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }

                    // 3D Map View Toggle
                    LaunchedEffect(is3DMode, mapplsMapRef) {
                        val map = mapplsMapRef ?: return@LaunchedEffect
                        try {
                            val currentCamera = map.cameraPosition
                            val targetTilt = if (is3DMode) 55.0 else 0.0
                            val cameraPos = com.mappls.sdk.maps.camera.CameraPosition.Builder(currentCamera)
                                .tilt(targetTilt)
                                .build()
                            map.easeCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(cameraPos), 400, false)
                        } catch (e: Exception) { e.printStackTrace() }
                    }

                    // Bottom Fade Effect
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF050505))
                                )
                            )
                    )
                    
                    // Floating Scrubber Badge
                    scrubbedPoint?.let { pt ->
                        scrubbedPixel?.let { px ->
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val badgeWidth = 150.dp
                            val badgeHeight = 75.dp
                            Box(
                                modifier = Modifier
                                    .absoluteOffset {
                                        IntOffset(
                                            x = px.x.toInt() - with(density) { 75.dp.toPx().toInt() },
                                            y = px.y.toInt() - with(density) { 80.dp.toPx().toInt() }
                                        )
                                    }
                                    .width(badgeWidth)
                                    .height(badgeHeight)
                                    .background(Color.Black.copy(alpha=0.8f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("${(pt.speedMetersPerSec * 3.6f).toInt()} km/h", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Lean: ${pt.leanAngle.toInt()}° | G: ${String.format(Locale.getDefault(), "%.1f", pt.gForce)}", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            
                            // Highlight Point
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(Color.White, radius = 16f, center = px)
                                drawCircle(ColorFlow, radius = 10f, center = px)
                            }
                        }
                    }
            } // Close else

            // Floating Header Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            ) {                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Session Summary", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        if (isLoading) {
                            Text("Loading...", color = Color.Gray, fontSize = 12.sp)
                        } else if (session != null) {
                            val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                            val dateString = sdf.format(Date(session!!.startTime))
                            Text(
                                text = "$dateString",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        offset = Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                )
                            )
                        }
                    }

                    if (!isLoading && session != null) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            val isCustomVehicle = session?.vehicleId?.startsWith("custom:") == true
                            val customVehicleName = remember(session?.vehicleId) {
                                session?.vehicleId?.removePrefix("custom:")
                            }
                            val isRecording = session?.endTime == null
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (sessionVehicle != null) Color(0xCC1B5E20) else if (isCustomVehicle) Color(0xCC006064) else Color(0xCCE65100),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .clickable(enabled = isOwnRide && !isRecording) { showLinkVehicleDialog = true }
                                    .wrapContentWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    val iconEmoji = when (sessionVehicle?.type?.lowercase()) {
                                        "car" -> "🚗"
                                        "scooter" -> "🛵"
                                        "bike" -> "🏍️"
                                        else -> null
                                    }
                                    if (iconEmoji != null) {
                                        Text(iconEmoji, fontSize = 9.sp)
                                    } else {
                                        Text(if (isCustomVehicle) "🚲" else "🔗", fontSize = 9.sp, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = sessionVehicle?.let { 
                                            if (it.make.isBlank() && it.model.isBlank()) it.name else "${it.make} ${it.model}"
                                        } ?: customVehicleName ?: "Link Vehicle",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (session!!.isAutoRecorded) Color(0xCC00363A) else Color(0xCC2E2E2E),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (session!!.isAutoRecorded) "⚡ Auto-Tracked" else "👤 Manual",
                                        color = if (session!!.isAutoRecorded) Color(0xFF00E5FF) else Color.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            

                            // Connection mode chip (Dash Only / Dash+Helmet / Helmet Only)
                            session!!.connectionLabel?.let { label ->
                                val isDash = label.contains("Dash", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDash) Color(0xCC1A1200) else Color(0xCC0A1A0A),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text(
                                        text = if (isDash) "🎛 $label" else "🪖 $label",
                                        color = if (isDash) Color(0xFFFFD54F) else Color(0xFF81C784),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                    }
                }
            }
        }
            
            // Bottom controls overlay (3D / KM Toggle Buttons)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp)
            ) {
                // 3D Map View Toggle Button (Bottom Left)
                androidx.compose.material3.FloatingActionButton(
                    onClick = { is3DMode = !is3DMode },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(48.dp),
                    containerColor = if (is3DMode) Color(0xFF00E5FF) else Color(0xFF141414),
                    contentColor = if (is3DMode) Color.Black else Color.Gray,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Text(if (is3DMode) "3D" else "2D", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // KM Markers Toggle Button (Bottom Right)
                androidx.compose.material3.FloatingActionButton(
                    onClick = { 
                        localShowKmMarkers = !localShowKmMarkers
                        settingsManager.setShowKilometerMarkers(localShowKmMarkers)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp),
                    containerColor = if (localShowKmMarkers) Color(0xFF00E5FF) else Color(0xFF141414),
                    contentColor = if (localShowKmMarkers) Color.Black else Color.Gray,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Text("KM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } // End of Map Box (Top 45%)

        if (showLinkVehicleDialog && session != null) {
            var customVehicleNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showLinkVehicleDialog = false },
                title = { Text("Link Ride to Vehicle", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Select a vehicle to link to this ride session:", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (vehiclesList.isEmpty()) {
                            Text("No vehicles in garage. Go to Garage to add one.", color = Color.Gray, fontSize = 14.sp)
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 160.dp)
                            ) {
                                items(vehiclesList) { vehicle ->
                                    val itemIcon = if (vehicle.type.lowercase() == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike
                                    val displayName = if (vehicle.make.isBlank() && vehicle.model.isBlank()) vehicle.name else "${vehicle.make} ${vehicle.model} (${vehicle.name})"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2A2A2A),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    val dao = RideDatabase.getDatabase(context).rideDao()
                                                    val updatedSession = session!!.copy(
                                                        vehicleId = vehicle.id,
                                                        lastUpdated = System.currentTimeMillis()
                                                    )
                                                    dao.updateSession(updatedSession)
                                                    session = updatedSession
                                                    com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                                }
                                                showLinkVehicleDialog = false
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(itemIcon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        var selectedType by remember { mutableStateOf("Bike") }

                        Text("Or link an occasional custom vehicle:", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customVehicleNameInput,
                            onValueChange = { customVehicleNameInput = it },
                            placeholder = { Text("e.g. Friend's Duke 390", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Vehicle Type", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Bike", "Scooter", "Car").forEach { vType ->
                                FilterChip(
                                    selected = selectedType == vType,
                                    onClick = { selectedType = vType },
                                    label = { Text(vType) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF2A2A2A),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (customVehicleNameInput.isNotBlank()) {
                                    coroutineScope.launch {
                                        val nameTrimmed = customVehicleNameInput.trim()
                                        val existingOccasional = vehiclesList.find { it.isOccasional && it.name.equals(nameTrimmed, ignoreCase = true) }
                                        val finalVehicleId = if (existingOccasional != null) {
                                            existingOccasional.id
                                        } else {
                                            val newId = java.util.UUID.randomUUID().toString()
                                            val newVehicle = com.nexus.portal.data.local.ridetracker.VehicleEntity(
                                                id = newId,
                                                name = nameTrimmed,
                                                type = selectedType,
                                                make = "",
                                                model = "",
                                                year = "",
                                                licensePlate = "",
                                                vin = "",
                                                insurancePolicyNumber = "",
                                                insuranceDate = 0L,
                                                pucDate = 0L,
                                                nextServiceDate = 0L,
                                                fuelType = "",
                                                tankCapacity = "",
                                                description = "Occasional vehicle",
                                                color = "",
                                                engineCC = "",
                                                odometer = "0.0 km",
                                                topSpeed = "0.0 km/h",
                                                fuelRange = "0 km",
                                                maintenanceDue = "No service date inputted",
                                                isActive = true,
                                                imageUrl = "https://images.unsplash.com/photo-1558981806-ec527fa84c39?q=80&w=2070&auto=format&fit=crop",
                                                originalImageUrl = "",
                                                statsAvgSpeed = "0.0",
                                                statsLeanAngle = "0.0",
                                                connectedDevicesJson = "[]",
                                                tripHistoryPreviewCount = 0,
                                                isOccasional = true
                                            )
                                            vehicleDao.insertVehicle(newVehicle)
                                            newId
                                        }

                                        val dao = RideDatabase.getDatabase(context).rideDao()
                                        val updatedSession = session!!.copy(
                                            vehicleId = finalVehicleId,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        dao.updateSession(updatedSession)
                                        session = updatedSession
                                        com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                    }
                                    showLinkVehicleDialog = false
                                }
                            },
                            enabled = customVehicleNameInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Link Custom Vehicle", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLinkVehicleDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
        if (!isLoading && session != null) {
            val s = session!!

            var edgeScrollSpeed by remember { mutableStateOf(0f) }

            LaunchedEffect(edgeScrollSpeed) {
                if (edgeScrollSpeed != 0f) {
                    while (true) {
                        scrollState.dispatchRawDelta(edgeScrollSpeed)
                        if (draggedWidgetIdx != -1) {
                            dragCurrentOffset = androidx.compose.ui.geometry.Offset(dragCurrentOffset.x, dragCurrentOffset.y + edgeScrollSpeed)
                        }
                        kotlinx.coroutines.delay(16)
                    }
                }
            }

            // Container for Widgets
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                if (points.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .background(Color(0xFF141414), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val speeds = listOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Slider(
                                value = autoScrubIndex.toFloat(),
                                onValueChange = {
                                    autoScrubIndex = it.toInt()
                                    if (points.isNotEmpty()) {
                                        val pt = points[autoScrubIndex]
                                        scrubbedPoint = pt
                                        mapplsMapRef?.let { map ->
                                            val proj = map.projection
                                            val ll = LatLng(pt.latitude, pt.longitude)
                                            val px = proj.toScreenLocation(ll)
                                            scrubbedPixel = Offset(px.x, px.y)
                                        }
                                    }
                                },
                                valueRange = 0f..(if (points.size > 1) points.size - 1 else 1).toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))) {
                                IconButton(onClick = {
                                    val idx = speeds.indexOf(playbackSpeed)
                                    if (idx > 0) playbackSpeed = speeds[idx - 1]
                                }, modifier = Modifier.size(28.dp)) {
                                    Text("-", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = if (playbackSpeed < 1f) "${playbackSpeed}x" else "${playbackSpeed.toInt()}x",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(onClick = {
                                    val idx = speeds.indexOf(playbackSpeed)
                                    if (idx < speeds.size - 1) playbackSpeed = speeds[idx + 1]
                                }, modifier = Modifier.size(28.dp)) {
                                    Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = { 
                                    mapplsMapRef?.let { map ->
                                        val pt = scrubbedPoint ?: points.firstOrNull()
                                        if (pt != null) {
                                            val currentZoom = map.cameraPosition.zoom
                                            map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(LatLng(pt.latitude, pt.longitude), currentZoom - 1.0))
                                        } else {
                                            map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.zoomOut())
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Zoom -", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { 
                                    if (points.isNotEmpty()) {
                                        val currentPt = points.getOrNull(autoScrubIndex)
                                        if (currentPt != null) {
                                            val targetTime = currentPt.timestamp - 10000
                                            autoScrubIndex = points.indexOfLast { it.timestamp <= targetTime }.takeIf { it != -1 } ?: 0
                                            val pt = points[autoScrubIndex]
                                            scrubbedPoint = pt
                                            mapplsMapRef?.let { map ->
                                                val proj = map.projection
                                                val ll = LatLng(pt.latitude, pt.longitude)
                                                val px = proj.toScreenLocation(ll)
                                                scrubbedPixel = Offset(px.x, px.y)
                                            }
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("⟲ 10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            IconButton(onClick = { 
                                isAutoScrubbing = !isAutoScrubbing
                                if (isAutoScrubbing && autoScrubIndex >= points.size) autoScrubIndex = 0
                            }) {
                                Icon(
                                    imageVector = if (isAutoScrubbing) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(onClick = { 
                                isAutoScrubbing = false
                                autoScrubIndex = 0
                                scrubbedPoint = null
                                scrubbedPixel = null
                            }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { 
                                    if (points.isNotEmpty()) {
                                        val currentPt = points.getOrNull(autoScrubIndex)
                                        if (currentPt != null) {
                                            val targetTime = currentPt.timestamp + 10000
                                            autoScrubIndex = points.indexOfFirst { it.timestamp >= targetTime }.takeIf { it != -1 } ?: (points.size - 1)
                                            val pt = points[autoScrubIndex]
                                            scrubbedPoint = pt
                                            mapplsMapRef?.let { map ->
                                                val proj = map.projection
                                                val ll = LatLng(pt.latitude, pt.longitude)
                                                val px = proj.toScreenLocation(ll)
                                                scrubbedPixel = Offset(px.x, px.y)
                                            }
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("10s ⟳", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { 
                                    mapplsMapRef?.let { map ->
                                        val pt = scrubbedPoint ?: points.firstOrNull()
                                        if (pt != null) {
                                            val currentZoom = map.cameraPosition.zoom
                                            map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(LatLng(pt.latitude, pt.longitude), currentZoom + 1.0))
                                        } else {
                                            map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.zoomIn())
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Zoom +", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Draggable Grid Engine (Smart 1x1 / 1x2 Layout)
                val visibleWidgets = remember(widgetOrder, points) {
                    if (points.isEmpty()) {
                        widgetOrder.filter { it == SummaryWidgetType.SCORE || it == SummaryWidgetType.STATS }
                    } else {
                        widgetOrder
                    }
                }

                val rowHeightPx = with(density) { 230.dp.toPx() }
                val spacingPx = with(density) { 16.dp.toPx() }
                var gridWidthPx by remember { mutableStateOf(1f) }
                
                val gridLayout = remember(visibleWidgets, gridWidthPx, density) {
                    val rects = mutableListOf<androidx.compose.ui.geometry.Rect>()
                    var currentX = 0f
                    var currentY = 0f
                    val cellWidth = (gridWidthPx - spacingPx) / 2f
                    
                    for (widget in visibleWidgets) {
                        val w = if (widget.span == 1) cellWidth else gridWidthPx
                        if (currentX + w > gridWidthPx + 1f) {
                            currentX = 0f
                            currentY += rowHeightPx + spacingPx
                        }
                        rects.add(androidx.compose.ui.geometry.Rect(currentX, currentY, currentX + w, currentY + rowHeightPx))
                        currentX += w + spacingPx
                    }
                    rects
                }
                
                val totalGridHeightPx = gridLayout.maxOfOrNull { it.bottom } ?: 0f
            
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { totalGridHeightPx.toDp() })
                        .onSizeChanged { gridWidthPx = maxOf(1f, it.width.toFloat()) }
                        .pointerInput(visibleWidgets, gridLayout) {
                            if (points.isNotEmpty()) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val idx = gridLayout.indexOfFirst { it.contains(offset) }
                                        if (idx != -1) {
                                            draggedWidgetIdx = idx
                                            hoverTargetIdx = idx
                                            val rect = gridLayout[idx]
                                            dragCurrentOffset = Offset(offset.x - rect.width / 2f, offset.y - rect.height / 2f)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (draggedWidgetIdx != -1) {
                                            dragCurrentOffset += dragAmount
                                            val dragCenter = Offset(dragCurrentOffset.x + gridLayout[draggedWidgetIdx].width / 2f, dragCurrentOffset.y + gridLayout[draggedWidgetIdx].height / 2f)
                                            val hIdx = gridLayout.indexOfFirst { it.contains(dragCenter) }
                                            if (hIdx != -1) {
                                                hoverTargetIdx = hIdx
                                            }

                                            val spacerPx = 400 * density.density
                                            val screenHeightPx = context.resources.displayMetrics.heightPixels
                                            val screenY = change.position.y - scrollState.value + spacerPx
                                            
                                            if (screenY < 150f) {
                                                edgeScrollSpeed = -20f
                                            } else if (screenY > screenHeightPx - 150f) {
                                                edgeScrollSpeed = 20f
                                            } else {
                                                edgeScrollSpeed = 0f
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggedWidgetIdx != -1 && hoverTargetIdx != -1 && draggedWidgetIdx != hoverTargetIdx) {
                                            val list = widgetOrder.toMutableList()
                                            val item = list.removeAt(draggedWidgetIdx)
                                            list.add(hoverTargetIdx, item)
                                            widgetOrder = list
                                            prefs.edit().putString("widget_order", list.joinToString(",") { it.name }).apply()
                                        }
                                        draggedWidgetIdx = -1
                                        hoverTargetIdx = -1
                                        edgeScrollSpeed = 0f
                                    },
                                    onDragCancel = {
                                        draggedWidgetIdx = -1
                                        hoverTargetIdx = -1
                                        edgeScrollSpeed = 0f
                                    }
                                )
                            }
                        }
                ) {
                    visibleWidgets.forEachIndexed { index, widget ->
                        val isDragged = index == draggedWidgetIdx
                        val targetRect = gridLayout.getOrNull(index) ?: androidx.compose.ui.geometry.Rect.Zero
                        
                        val animatedX by animateFloatAsState(
                            targetValue = if (isDragged) dragCurrentOffset.x else targetRect.left,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "offsetX"
                        )
                        val animatedY by animateFloatAsState(
                            targetValue = if (isDragged) dragCurrentOffset.y else targetRect.top,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "offsetY"
                        )
                        
                        Box(
                            modifier = Modifier
                                .width(with(density) { targetRect.width.toDp() })
                                .height(with(density) { targetRect.height.toDp() })
                                .offset { IntOffset(animatedX.toInt(), animatedY.toInt()) }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    val isHovered = index == hoverTargetIdx && draggedWidgetIdx != -1 && !isDragged
                                    scaleX = if (isDragged) 1.05f else if (isHovered) 0.95f else 1f
                                    scaleY = if (isDragged) 1.05f else if (isHovered) 0.95f else 1f
                                    shadowElevation = if (isDragged) 16.dp.toPx() else 0f
                                    alpha = if (isDragged) 0.85f else 1f
                                }
                        ) {
                            when (widget) {
                                SummaryWidgetType.LEAN -> {
                                    val is4w = s.vehicleType == "four_wheeler"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF141414))
                                            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (is4w) {
                                                    if (scrubbedPoint != null) "Lateral G (Scrub)" else "Lateral G (Peak)"
                                                } else {
                                                    if (scrubbedPoint != null) "Lean (Scrub)" else "Lean (Max)"
                                                },
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(onClick = { expandedWidget = widget }, modifier = Modifier.size(24.dp)) {
                                                Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = "Expand", tint = Color.Gray)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            if (is4w) {
                                                val latG = scrubbedPoint?.accelLateral ?: (points.maxByOrNull { abs(it.accelLateral) }?.accelLateral ?: s.peakLatG)
                                                BentoLateralGBar(lateralG = latG)
                                            } else {
                                                BentoLeanAngleVisualizer(leanAngle = scrubbedPoint?.leanAngle ?: s.maxLeanAngle)
                                            }
                                        }
                                    }
                                }
                                SummaryWidgetType.GFORCE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF141414))
                                            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (scrubbedPoint != null) "G-Force (Scrub)" else "G-Force (Peak)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { expandedWidget = widget }, modifier = Modifier.size(24.dp)) {
                                                Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = "Expand", tint = Color.Gray)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            val latG = scrubbedPoint?.accelLateral ?: (points.maxByOrNull { abs(it.accelLateral) }?.accelLateral ?: s.peakLatG)
                                            val lonG = scrubbedPoint?.accelLongitudinal ?: (points.maxByOrNull { abs(it.accelLongitudinal) }?.accelLongitudinal ?: 0f)
                                            BentoGForceMeter(lateralG = latG, longitudinalG = lonG)
                                        }
                                    }
                                }
                            SummaryWidgetType.SCORE -> {
                                SummaryCard(title = "Performance Score", onExpandClick = { expandedWidget = widget }) {
                                    val dynamicScore = remember(points) {
                                        if (points.isNotEmpty()) {
                                            var flowScore = 100
                                            val hardBrakes = points.count { it.gForce > 0.6f }
                                            flowScore -= hardBrakes * 2
                                            val extremeLeanCount = points.count { kotlin.math.abs(it.leanAngle) > 40f }
                                            flowScore -= extremeLeanCount
                                            flowScore.coerceIn(0, 100)
                                        } else s.flowRating
                                    }
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        PerformanceRing(score = dynamicScore, maxScore = 100)
                                    }
                                }
                            }
                            SummaryWidgetType.STATS -> {
                                SummaryCard(title = "Session Stats", onExpandClick = { expandedWidget = widget }) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                        val durationMs = (s.endTime ?: System.currentTimeMillis()) - s.startTime
                                        val h = durationMs / 3600000
                                        val m = (durationMs / 60000) % 60
                                        val sec = (durationMs / 1000) % 60
                                        val durationStr = if (h > 0) String.format(java.util.Locale.getDefault(), "%d hr %02d min", h, m) else String.format(java.util.Locale.getDefault(), "%02d:%02d", m, sec)
                                        val distanceKm = s.totalDistanceMeters / 1000f
                                        val actualMaxSpeed = if (points.isNotEmpty()) points.maxOf { it.speedMetersPerSec * 3.6f } else s.maxSpeedKmh
                                        val actualAvgSpeed = if (points.isNotEmpty()) points.map { it.speedMetersPerSec * 3.6f }.average().toFloat() else s.averageSpeedKmh
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            StatItem("Duration", durationStr, Modifier.weight(1f))
                                            StatItem("Distance", String.format(java.util.Locale.getDefault(), "%.2f km", distanceKm), Modifier.weight(1f))
                                            StatItem("Avg Speed", String.format(java.util.Locale.getDefault(), "%.1f km/h", actualAvgSpeed), Modifier.weight(1f))
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            StatItem("Max Speed", String.format(java.util.Locale.getDefault(), "%.1f km/h", actualMaxSpeed), Modifier.weight(1f))
                                            if (s.vehicleType == "four_wheeler") {
                                                val peakLatGVal = points.maxByOrNull { abs(it.accelLateral) }?.accelLateral ?: s.peakLatG
                                                StatItem("Peak Lat G", String.format(java.util.Locale.getDefault(), "%.2f G", abs(peakLatGVal)), Modifier.weight(1f))
                                            } else {
                                                StatItem("Max Lean", String.format(java.util.Locale.getDefault(), "%.1f°", s.maxLeanAngle), Modifier.weight(1f))
                                            }
                                            val peakGForce = points.maxOfOrNull { it.gForce } ?: s.peakLatG
                                            StatItem("Peak G-Force", String.format(java.util.Locale.getDefault(), "%.2f G", peakGForce), Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            SummaryWidgetType.TIMELINE -> {
                                SummaryCard(title = "Interactive Timeline Graph", onExpandClick = { expandedWidget = widget }) {
                                    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                            LegendItem("Speed", ColorSpeed)
                                            LegendItem("Lean Angle", ColorLean)
                                            LegendItem("Braking G-force", ColorBrakeGraph)
                                        }
                                        TimelineGraphCanvas(
                                            points = points,
                                            scrubbedPoint = scrubbedPoint,
                                            onScrub = { pt ->
                                                scrubbedPoint = pt
                                                if (pt != null) {
                                                    val latLng = LatLng(pt.latitude, pt.longitude)
                                                    mapplsMapRef?.let { map ->
                                                        val px = map.projection.toScreenLocation(latLng)
                                                        scrubbedPixel = Offset(px.x, px.y)
                                                    }
                                                } else {
                                                    scrubbedPixel = null
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            SummaryWidgetType.SECTORS -> {
                                SummaryCard(title = "Sector Breakdown", onExpandClick = { expandedWidget = widget }) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                                        if (points.isEmpty()) {
                                            Text("Not enough data to calculate sectors.", color = Color.Gray)
                                        } else {
                                            val sectorDuration = ((s.endTime ?: System.currentTimeMillis()) - s.startTime) / 3
                                            var pIndex = 0
                                            val n = points.size
                                            for (i in 1..3) {
                                                val sectorStart = s.startTime + (i - 1) * sectorDuration
                                                val sectorEnd = s.startTime + i * sectorDuration
                                                var maxSpd = 0f
                                                while (pIndex < n && points[pIndex].timestamp <= sectorEnd) {
                                                    val spdKmh = points[pIndex].speedMetersPerSec * 3.6f
                                                    if (spdKmh > maxSpd) maxSpd = spdKmh
                                                    pIndex++
                                                }
                                                val status = when (i) {
                                                    1 -> "Warmup" to ColorFlow
                                                    2 -> "Push" to ColorSpeed
                                                    else -> "Cooldown" to ColorLean
                                                }
                                                SectorRow("Sector $i", String.format(Locale.getDefault(), "%02d:%02d", (sectorDuration / 60000), (sectorDuration / 1000) % 60), String.format(Locale.getDefault(), "%d", maxSpd.toInt()), status.first, status.second)
                                            }
                                        }
                                    }
                                }
                            }
                            SummaryWidgetType.ALTITUDE -> {
                                SummaryCard(title = "Altitude & Elevation", onExpandClick = { expandedWidget = widget }) {
                                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (points.isNotEmpty()) {
                                            Scrubbable2DGraph(
                                                points = points,
                                                lines = listOf(GraphLineDef("Altitude", Color(0xFF9C27B0), { it.altitude.toFloat() }, false)),
                                                scrubbedPoint = scrubbedPoint,
                                                showStats = true,
                                                onScrub = { pt ->
                                                    scrubbedPoint = pt
                                                    if (pt != null) {
                                                        val latLng = LatLng(pt.latitude, pt.longitude)
                                                        mapplsMapRef?.let { map ->
                                                            val px = map.projection.toScreenLocation(latLng)
                                                            scrubbedPixel = Offset(px.x, px.y)
                                                        }
                                                    } else {
                                                        scrubbedPixel = null
                                                    }
                                                }
                                            )
                                        } else {
                                            Text("No Elevation data", color = Color.Gray)
                                        }
                                    }
                                }
                            }
                            SummaryWidgetType.SPEED -> {
                                SummaryCard(title = "Speed Profile", onExpandClick = { expandedWidget = widget }) {
                                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (points.isNotEmpty()) {
                                            Scrubbable2DGraph(
                                                points = points,
                                                lines = listOf(GraphLineDef("Speed", ColorSpeed, { it.speedMetersPerSec * 3.6f }, false)),
                                                scrubbedPoint = scrubbedPoint,
                                                showStats = true,
                                                onScrub = { pt ->
                                                    scrubbedPoint = pt
                                                    if (pt != null) {
                                                        val latLng = LatLng(pt.latitude, pt.longitude)
                                                        mapplsMapRef?.let { map ->
                                                            val px = map.projection.toScreenLocation(latLng)
                                                            scrubbedPixel = Offset(px.x, px.y)
                                                        }
                                                    } else {
                                                        scrubbedPixel = null
                                                    }
                                                }
                                            )
                                        } else {
                                            Text("No Speed data", color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                } // Close Grid Engine Box
                
                Spacer(modifier = Modifier.height(24.dp))
                if (session != null && session!!.deviceName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDCF1", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recorded on ${session!!.deviceName}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Centered Public/Private Toggle Chip Row
                if (isOwnRide && session != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isPublic = session!!.isPublic
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF141414),
                            border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Public Option
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isPublic) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (isPublic) Color(0xFF00E5FF) else Color.Transparent),
                                    modifier = Modifier
                                        .clickable {
                                            if (!isPublic) {
                                                viewModel.togglePublicStatus()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🌍", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Public",
                                            color = if (isPublic) Color(0xFF00E5FF) else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Private Option
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (!isPublic) Color(0xFFFF1744).copy(alpha = 0.15f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (!isPublic) Color(0xFFFF1744) else Color.Transparent),
                                    modifier = Modifier
                                        .clickable {
                                            if (isPublic) {
                                                viewModel.togglePublicStatus()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🔒", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Private",
                                            color = if (!isPublic) Color(0xFFFF1744) else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                var showShareDialog by remember { mutableStateOf(false) }
                androidx.compose.material3.OutlinedButton(
                    onClick = { showShareDialog = true },
                    modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Text("Share Ride", fontWeight = FontWeight.Bold)
                }
                
                if (showShareDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showShareDialog = false },
                        title = { Text("Share Ride", color = Color.White) },
                        text = {
                            Column {
                                androidx.compose.material3.TextButton(onClick = {
                                    showShareDialog = false
                                    if (session != null) {
                                        isSyncingForShare = true
                                    }
                                }) {
                                    Text("Share as Web Link", color = Color(0xFF00E5FF))
                                }
                                androidx.compose.material3.TextButton(onClick = {
                                    showShareDialog = false
                                    if (session != null) {
                                        val gpxFile = com.nexus.portal.util.GPXExporter.exportRide(context, session!!, points)
                                        if (gpxFile != null) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.nexus.portal.fileprovider", gpxFile)
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/gpx+xml"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Share GPX File"))
                                        }
                                    }
                                }) {
                                    Text("Export as GPX File", color = Color(0xFF00E5FF))
                                }
                                androidx.compose.material3.TextButton(onClick = {
                                    showShareDialog = false
                                    if (session != null) {
                                        try {
                                            val pdfFile = generateRidePdfReport(context, session!!)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.nexus.portal.fileprovider", pdfFile)
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Share PDF Report"))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            android.widget.Toast.makeText(context, "Failed to generate PDF Report", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text("Share PDF Report", color = Color(0xFF00E5FF))
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showShareDialog = false }) {
                                Text("Cancel", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF1E1E1E)
                    )
                }
                
            } // Close Scrollable Column
        } // Close else block
    } // Close Root Column
    
    if (expandedWidget != null && session != null) {
        ExpandedWidgetOverlay(
            widget = expandedWidget!!,
            session = session!!,
            points = points,
            onDismiss = { expandedWidget = null }
        )
    }
} // Close RideSummaryScreen

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SummaryCard(title: String, onExpandClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (onExpandClick != null) {
                    IconButton(onClick = onExpandClick, modifier = Modifier.size(24.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = "Expand", tint = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun PerformanceRing(score: Int, maxScore: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeWidth = 8.dp.toPx()
            
            // Background track
            drawArc(
                color = Color.DarkGray,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Progress
            val sweep = (score.toFloat() / maxScore) * 270f
            drawArc(
                color = ColorFlow,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$score", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text("/$maxScore", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text("Flow Rating", color = ColorSpeed, fontSize = 10.sp)
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun TimelineGraphCanvas(
    points: List<RideLocationPoint>,
    scrubbedPoint: RideLocationPoint?,
    onScrub: (RideLocationPoint?) -> Unit
) {
    Scrubbable2DGraph(
        points = points,
        lines = listOf(
            GraphLineDef("Speed", ColorSpeed, { it.speedMetersPerSec * 3.6f }, true),
            GraphLineDef("Lean Angle", ColorLean, { abs(it.leanAngle) }, true),
            GraphLineDef("Braking", ColorBrakeGraph, { abs(it.gForce) }, true)
        ),
        scrubbedPoint = scrubbedPoint,
        onScrub = onScrub,
        showStats = false
    )
}

@Composable
fun SectorRow(name: String, split: String, maxSpeed: String, statusText: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(split, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(maxSpeed, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(statusColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    HorizontalDivider(color = Color.DarkGray)
}

data class GraphLineDef(
    val label: String,
    val color: Color,
    val valueExtractor: (RideLocationPoint) -> Float,
    val normalizeMax: Boolean = false
)

@Composable
fun Scrubbable2DGraph(
    points: List<RideLocationPoint>,
    lines: List<GraphLineDef>,
    scrubbedPoint: RideLocationPoint?,
    onScrub: (RideLocationPoint?) -> Unit,
    showStats: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth().height(150.dp)
) {
    var zoomScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("No telemetry data", color = Color.Gray)
        }
        return
    }

    val startTime = points.first().timestamp
    val duration = max(1L, points.last().timestamp - startTime)

    if (showStats && lines.size == 1) {
        val vals = points.map { lines[0].valueExtractor(it) }
        val maxV = vals.maxOrNull() ?: 0f
        val avgV = if (vals.isNotEmpty()) vals.average().toFloat() else 0f
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Avg: ${String.format(java.util.Locale.getDefault(), "%.1f", avgV)}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Max: ${String.format(java.util.Locale.getDefault(), "%.1f", maxV)}", color = Color.Gray, fontSize = 10.sp)
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = zoomScale
                    zoomScale = (zoomScale * zoom).coerceIn(1f, 10f)
                    
                    offsetX = (offsetX + pan.x - centroid.x) * (zoomScale / oldScale) + centroid.x
                    
                    val maxOffsetX = 0f
                    val minOffsetX = -size.width * (zoomScale - 1f)
                    offsetX = offsetX.coerceIn(minOffsetX, maxOffsetX)
                    
                    val graphX = (centroid.x - offsetX) / zoomScale
                    val ratio = (graphX / size.width).coerceIn(0f, 1f)
                    val targetTime = startTime + (duration.toFloat() * ratio).toLong()
                    val closest = points.minByOrNull { kotlin.math.abs(it.timestamp - targetTime) }
                    onScrub(closest)
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                        if (event.changes.none { it.pressed }) {
                            onScrub(null)
                        }
                    }
                }
            }
    ) {

        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val numHorizontalLines = 4
            for (i in 0..numHorizontalLines) {
                val y = i * (h / numHorizontalLines)
                drawLine(Color.White.copy(alpha = 0.1f), start = Offset(0f, y), end = Offset(w, y))
            }
            
            val numVerticalLines = 6
            for (i in 0..numVerticalLines) {
                if (w <= 0f) continue
                val screenX = i * (w / numVerticalLines)
                val graphX = (screenX - offsetX) / zoomScale
                val ratio = graphX / w
                if (ratio in 0f..1f && !ratio.isNaN()) {
                    val timeMs = (duration.toFloat() * ratio).toLong()
                    val mins = (timeMs / 60000)
                    val secs = (timeMs / 1000) % 60
                    val timeStr = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
                    
                    drawLine(Color.White.copy(alpha = 0.05f), start = Offset(screenX, 0f), end = Offset(screenX, h))
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 30f
                        }
                        canvas.nativeCanvas.drawText(timeStr, screenX + 10f, h - 20f, paint)
                    }
                }
            }

            val paths = lines.map { Path() }
            val maxVals = lines.map { l -> kotlin.math.max(1f, points.maxOfOrNull { kotlin.math.abs(l.valueExtractor(it)) } ?: 1f) }

            val started = BooleanArray(lines.size) { false }
            for (p in points) {
                val pointX = offsetX + (zoomScale * w * ((p.timestamp - startTime).toFloat() / duration.toFloat()))
                if (pointX.isNaN() || pointX.isInfinite()) continue
                
                for (i in lines.indices) {
                    val rawVal = kotlin.math.abs(lines[i].valueExtractor(p))
                    val maxVal = if (maxVals[i].isNaN() || maxVals[i] == 0f) 1f else maxVals[i]
                    val normVal = if (lines[i].normalizeMax) rawVal / maxVal else rawVal / maxVal 
                    val pointY = h - (h * normVal).coerceIn(0f, h)

                    if (pointY.isNaN() || pointY.isInfinite()) continue

                    if (!started[i]) {
                        paths[i].moveTo(pointX, pointY)
                        started[i] = true
                    } else {
                        paths[i].lineTo(pointX, pointY)
                    }
                }
            }

            for (i in lines.indices) {
                drawPath(path = paths[i], color = lines[i].color, style = Stroke(width = 2.dp.toPx()))
            }

            scrubbedPoint?.let { sp ->
                val ratio = (sp.timestamp - startTime).toFloat() / duration.toFloat()
                val scrubX = offsetX + (zoomScale * w * ratio)
                if (scrubX in 0f..w) {
                    drawLine(Color.White, start = Offset(scrubX, 0f), end = Offset(scrubX, h), strokeWidth = 1.dp.toPx())
                    for (i in lines.indices) {
                        val rawVal = abs(lines[i].valueExtractor(sp))
                        val normVal = if (lines[i].normalizeMax) rawVal / maxVals[i] else rawVal / maxVals[i]
                        val dotY = h - (h * normVal).coerceIn(0f, h)
                        drawCircle(lines[i].color, radius = 4.dp.toPx(), center = Offset(scrubX, dotY))
                        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(scrubX, dotY))
                    }
                }
            }
        }
        
        scrubbedPoint?.let { sp ->
            val ratio = (sp.timestamp - startTime).toFloat() / duration.toFloat()
            val scrubX = offsetX + (zoomScale * wPx * ratio)
            
            val maxVals = lines.map { l -> max(1f, points.maxOfOrNull { abs(l.valueExtractor(it)) } ?: 1f) }
            val primaryRaw = abs(lines[0].valueExtractor(sp))
            val primaryNorm = if (lines[0].normalizeMax) primaryRaw / maxVals[0] else primaryRaw / maxVals[0]
            val primaryDotY = hPx - (hPx * primaryNorm).coerceIn(0f, hPx)
            
            val tooltipWidthPx = with(density) { 120.dp.toPx() }
            var safeX = scrubX + with(density) { 16.dp.toPx() }
            if (safeX + tooltipWidthPx > wPx - with(density) { 8.dp.toPx() }) {
                safeX = scrubX - tooltipWidthPx - with(density) { 16.dp.toPx() }
            }
            val minX = with(density) { 8.dp.toPx() }
            val maxX = kotlin.math.max(minX, wPx - tooltipWidthPx - minX)
            safeX = safeX.coerceIn(minX, maxX)
            
            val tooltipHeightPx = with(density) { (lines.size * 30 + 40).dp.toPx() }
            var safeY = primaryDotY - tooltipHeightPx - with(density) { 8.dp.toPx() }
            val minY = with(density) { 8.dp.toPx() }
            val maxY = kotlin.math.max(minY, hPx - tooltipHeightPx - minY)
            safeY = safeY.coerceIn(minY, maxY)

            Column(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(safeX.toInt(), safeY.toInt()) }
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                lines.forEach { line ->
                    val rawVal = line.valueExtractor(sp)
                    Text("${line.label}: ${String.format(java.util.Locale.getDefault(), "%.1f", rawVal)}", color = line.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        if (lines.size == 1) {
            val maxV = points.maxOfOrNull { lines[0].valueExtractor(it) } ?: 0f
            Text("${maxV.toInt()}", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.align(Alignment.TopStart))
            Text("0", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
fun ExpandedWidgetOverlay(
    widget: SummaryWidgetType,
    session: RideSession,
    points: List<RideLocationPoint>,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (widget) {
                            SummaryWidgetType.LEAN -> if (session.vehicleType == "four_wheeler") "Lateral G Analytics" else "Lean Analytics"
                            SummaryWidgetType.GFORCE -> "G-Force Analytics"
                            SummaryWidgetType.SCORE -> "Performance Factors"
                            SummaryWidgetType.STATS -> "Detailed Session Stats"
                            SummaryWidgetType.TIMELINE -> "Timeline Graph"
                            SummaryWidgetType.SECTORS -> "All Sectors"
                            SummaryWidgetType.ALTITUDE -> "Altitude Graph"
                            SummaryWidgetType.SPEED -> "Speed Profile"
                        },
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                // Content Area
                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    var scrubbedPoint by remember { mutableStateOf<RideLocationPoint?>(null) }

                    
                    when (widget) {
                        SummaryWidgetType.TIMELINE -> {
                            Scrubbable2DGraph(
                                points = points,
                                lines = listOf(
                                    GraphLineDef("Speed", ColorSpeed, { it.speedMetersPerSec * 3.6f }, true),
                                    GraphLineDef("Lean Angle", ColorLean, { abs(it.leanAngle) }, true),
                                    GraphLineDef("Braking", ColorBrakeGraph, { abs(it.gForce) }, true)
                                ),
                                scrubbedPoint = scrubbedPoint,
                                onScrub = { scrubbedPoint = it },
                                showStats = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SummaryWidgetType.SPEED -> {
                            Scrubbable2DGraph(
                                points = points,
                                lines = listOf(GraphLineDef("Speed", ColorSpeed, { it.speedMetersPerSec * 3.6f }, false)),
                                scrubbedPoint = scrubbedPoint,
                                onScrub = { scrubbedPoint = it },
                                showStats = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SummaryWidgetType.ALTITUDE -> {
                            Scrubbable2DGraph(
                                points = points,
                                lines = listOf(GraphLineDef("Altitude", Color.White, { it.altitude.toFloat() }, false)),
                                scrubbedPoint = scrubbedPoint,
                                onScrub = { scrubbedPoint = it },
                                showStats = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SummaryWidgetType.LEAN -> {
                             val is4w = session.vehicleType == "four_wheeler"
                             if (is4w) {
                                 val maxLeftG = points.minOfOrNull { it.accelLateral } ?: 0f
                                 val maxRightG = points.maxOfOrNull { it.accelLateral } ?: 0f
                                 Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                     Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                                         BentoLateralGBar(lateralG = session.peakLatG)
                                     }
                                     Spacer(modifier = Modifier.height(32.dp))
                                     Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text("Peak Left G", color = Color.Gray, fontSize = 16.sp)
                                             Text("${String.format(java.util.Locale.getDefault(), "%.2f G", abs(maxLeftG))}", color = ColorVelocity, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                         }
                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text("Peak Right G", color = Color.Gray, fontSize = 16.sp)
                                             Text("${String.format(java.util.Locale.getDefault(), "%.2f G", abs(maxRightG))}", color = ColorVelocity, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                         }
                                     }
                                 }
                             } else {
                                 val maxLeft = points.minOfOrNull { it.leanAngle } ?: 0f
                                 val maxRight = points.maxOfOrNull { it.leanAngle } ?: 0f
                                 Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                     Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                                         BentoLeanAngleVisualizer(leanAngle = session.maxLeanAngle)
                                     }
                                     Spacer(modifier = Modifier.height(32.dp))
                                     Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text("Max Left", color = Color.Gray, fontSize = 16.sp)
                                             Text("${String.format(java.util.Locale.getDefault(), "%.1f", abs(maxLeft))}°", color = ColorLean, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                         }
                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text("Max Right", color = Color.Gray, fontSize = 16.sp)
                                             Text("${String.format(java.util.Locale.getDefault(), "%.1f", abs(maxRight))}°", color = ColorLean, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                         }
                                     }
                                 }
                             }
                         }
                        SummaryWidgetType.GFORCE -> {
                            val maxAccel = points.maxOfOrNull { if (it.accelLongitudinal > 0) it.accelLongitudinal else 0f } ?: 0f
                            val maxDecel = points.minOfOrNull { if (it.accelLongitudinal < 0) it.accelLongitudinal else 0f } ?: 0f
                            val peakLatG = points.maxByOrNull { abs(it.accelLateral) }?.accelLateral ?: session.peakLatG
                            val peakLonG = points.maxByOrNull { abs(it.accelLongitudinal) }?.accelLongitudinal ?: 0f
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                                    BentoGForceMeter(lateralG = peakLatG, longitudinalG = peakLonG)
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Max Acceleration", color = Color.Gray, fontSize = 16.sp)
                                        Text("${String.format(java.util.Locale.getDefault(), "%.2f", maxAccel)} G", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Max Braking", color = Color.Gray, fontSize = 16.sp)
                                        Text("${String.format(java.util.Locale.getDefault(), "%.2f", abs(maxDecel))} G", color = ColorBrakeGraph, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        SummaryWidgetType.SCORE -> {
                            val hardBrakes = points.count { it.gForce > 0.6f }
                            val extremeLeanCount = points.count { kotlin.math.abs(it.leanAngle) > 40f }
                            val flowScore = (100 - hardBrakes * 2 - extremeLeanCount).coerceIn(0, 100)
                            
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(32.dp))
                                PerformanceRing(score = flowScore, maxScore = 100)
                                Spacer(modifier = Modifier.height(48.dp))
                                
                                Text("Factor Breakdown", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Base Score", color = Color.Gray, fontSize = 16.sp)
                                    Text("100", color = Color.White, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Hard Braking Deductions (-2x)", color = Color.Gray, fontSize = 16.sp)
                                    Text("-${hardBrakes * 2}", color = ColorBrakeGraph, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Extreme Lean Deductions (-1x)", color = Color.Gray, fontSize = 16.sp)
                                    Text("-$extremeLeanCount", color = ColorLean, fontSize = 16.sp)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Final Flow Score", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("$flowScore", color = ColorFlow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        SummaryWidgetType.STATS -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val durationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime
                                val h = durationMs / 3600000
                                val m = (durationMs / 60000) % 60
                                val sec = (durationMs / 1000) % 60
                                val durationStr = if (h > 0) String.format(java.util.Locale.getDefault(), "%d hr %02d min", h, m) else String.format(java.util.Locale.getDefault(), "%02d:%02d", m, sec)
                                    
                                val movingTimeMs = points.filter { it.speedMetersPerSec > 1.0f }.size * 1000L // Approx 1s per point
                                val movingStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", 
                                    (movingTimeMs / 3600000) % 24, (movingTimeMs / 60000) % 60, (movingTimeMs / 1000) % 60)
                                    
                                val avgSpd = if (points.isNotEmpty()) points.map { it.speedMetersPerSec * 3.6f }.average() else 0.0
                                val elevGain = (points.maxOfOrNull { it.altitude } ?: 0.0) - (points.minOfOrNull { it.altitude } ?: 0.0)
                                
                                SectorRow("Total Distance", "", String.format(java.util.Locale.getDefault(), "%.1f km", session.totalDistanceMeters / 1000f), "", Color.Transparent)
                                SectorRow("Max Speed", "", String.format(java.util.Locale.getDefault(), "%.1f kph", session.maxSpeedKmh), "", Color.Transparent)
                                SectorRow("Avg Speed", "", String.format(java.util.Locale.getDefault(), "%.1f kph", avgSpd), "", Color.Transparent)
                                SectorRow("Total Time", "", durationStr, "", Color.Transparent)
                                SectorRow("Moving Time", "", movingStr, "", Color.Transparent)
                                SectorRow("Max Elevation", "", String.format(java.util.Locale.getDefault(), "%.1f m", elevGain), "", Color.Transparent)
                            }
                        }
                        SummaryWidgetType.SECTORS -> {
                            val sectors = mutableListOf<String>()
                            if (points.isNotEmpty()) {
                                val totalDistance = session.totalDistanceMeters
                                val sectorLength = totalDistance / 3f
                                var currentSector = 1
                                var distAccum = 0f
                                for (i in 1 until points.size) {
                                    val pt = points[i]
                                    val prev = points[i-1]
                                    val res = FloatArray(1)
                                    android.location.Location.distanceBetween(prev.latitude, prev.longitude, pt.latitude, pt.longitude, res)
                                    distAccum += res[0]
                                    if (distAccum >= sectorLength) {
                                        sectors.add("Sector $currentSector")
                                        currentSector++
                                        distAccum = 0f
                                    }
                                }
                                if (sectors.size < 3) sectors.add("Sector $currentSector")
                            }
                            
                            Column(modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                sectors.forEachIndexed { idx, s ->
                                    val maxS = session.maxSpeedKmh * (1f - (idx * 0.05f))
                                    val status = if (idx == 0) "PERSONAL BEST" else if (idx == 1) "FAST" else "AVERAGE"
                                    val color = if (idx == 0) Color(0xFFFFD700) else if (idx == 1) Color(0xFF00E676) else Color.Gray
                                    SectorRow(name = s, split = String.format(java.util.Locale.getDefault(), "+%.2fs", idx * 1.2f), maxSpeed = String.format(java.util.Locale.getDefault(), "%.1f kph", maxS), statusText = status, statusColor = color)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun generateRidePdfReport(context: android.content.Context, session: com.nexus.portal.data.local.ridetracker.RideSession): java.io.File {
    val document = android.graphics.pdf.PdfDocument()
    
    // Page dimensions (A4 size: 595 x 842)
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    
    // Paints
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0F0F0F")
        style = android.graphics.Paint.Style.FILL
    }
    // Draw background
    canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)
    
    // Header Banner
    val headerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#151515")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, 595f, 120f, headerPaint)
    
    // Header Accent Line (Cyan)
    val cyanPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00E5FF")
        strokeWidth = 3f
        style = android.graphics.Paint.Style.STROKE
    }
    canvas.drawLine(0f, 120f, 595f, 120f, cyanPaint)
    
    // Text Paints
    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val subtitlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00E5FF")
        textSize = 12f
        isAntiAlias = true
    }
    val datePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 10f
        isAntiAlias = true
    }
    
    // Draw Header Text
    canvas.drawText("NEXUS PORTAL", 30f, 50f, titlePaint)
    canvas.drawText("RIDE SUMMARY TELEMETRY REPORT", 30f, 75f, subtitlePaint)
    
    val sdf = java.text.SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    val tripDate = sdf.format(java.util.Date(session.startTime))
    canvas.drawText("Generated on: $tripDate", 30f, 95f, datePaint)
    
    // Content paints
    val sectionPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 14f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val cardBgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#1B1B1B")
        style = android.graphics.Paint.Style.FILL
    }
    val cardBorderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#2A2A2A")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 10f
        isAntiAlias = true
    }
    val valuePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 18f
        isFakeBoldText = true
        isAntiAlias = true
    }
    
    // Draw Ride Statistics Section
    canvas.drawText("RIDE STATISTICS", 30f, 160f, sectionPaint)
    canvas.drawLine(30f, 170f, 565f, 170f, android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#222222")
        strokeWidth = 1f
    })
    
    // Helper to draw a stats card
    fun drawStatCard(x: Float, y: Float, w: Float, h: Float, label: String, value: String) {
        val rect = android.graphics.RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, cardBorderPaint)
        canvas.drawText(label.uppercase(java.util.Locale.getDefault()), x + 15f, y + 22f, labelPaint)
        canvas.drawText(value, x + 15f, y + 48f, valuePaint)
    }
    
    // Stats layout Grid: 2 columns, 3 rows
    val col1X = 30f
    val col2X = 305f
    val row1Y = 190f
    val row2Y = 260f
    val row3Y = 330f
    val cardW = 260f
    val cardH = 58f
    
    // Calculate values
    val distKm = String.format(java.util.Locale.getDefault(), "%.2f km", session.totalDistanceMeters / 1000f)
    val topSpeed = String.format(java.util.Locale.getDefault(), "%.1f km/h", session.maxSpeedKmh)
    val avgSpeed = String.format(java.util.Locale.getDefault(), "%.1f km/h", session.averageSpeedKmh)
    val maxLean = String.format(java.util.Locale.getDefault(), "%.1f°", session.maxLeanAngle)
    val maxG = String.format(java.util.Locale.getDefault(), "%.2f G", session.peakLatG)
    val flowScore = "${session.flowRating} / 100"
    
    drawStatCard(col1X, row1Y, cardW, cardH, "Total Distance", distKm)
    drawStatCard(col2X, row1Y, cardW, cardH, "Top Speed", topSpeed)
    
    drawStatCard(col1X, row2Y, cardW, cardH, "Average Speed", avgSpeed)
    drawStatCard(col2X, row2Y, cardW, cardH, "Peak Lean Angle", maxLean)
    
    drawStatCard(col1X, row3Y, cardW, cardH, "Peak Lateral G", maxG)
    drawStatCard(col2X, row3Y, cardW, cardH, "Flow Rating", flowScore)
    
    // Draw Vehicle & Device Info Section
    canvas.drawText("RECORDING SYSTEM DETAILS", 30f, 430f, sectionPaint)
    canvas.drawLine(30f, 440f, 565f, 440f, android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#222222")
        strokeWidth = 1f
    })
    
    val infoLabelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 11f
        isAntiAlias = true
    }
    val infoValuePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 11f
        isAntiAlias = true
    }
    
    val infoY = 465f
    val lineH = 22f
    
    canvas.drawText("Device Name:", 35f, infoY, infoLabelPaint)
    canvas.drawText(session.deviceName, 160f, infoY, infoValuePaint)
    
    canvas.drawText("Connection Type:", 35f, infoY + lineH, infoLabelPaint)
    canvas.drawText(session.connectionLabel ?: "Direct Sensor GPS", 160f, infoY + lineH, infoValuePaint)
    
    canvas.drawText("Vehicle Type:", 35f, infoY + lineH * 2, infoLabelPaint)
    canvas.drawText(if (session.vehicleType == "two_wheeler") "Motorcycle / Scooter" else "Car / Four-Wheeler", 160f, infoY + lineH * 2, infoValuePaint)
    
    val durationText = if (session.endTime != null) {
        val diffMs = session.endTime - session.startTime
        val hrs = diffMs / (3600 * 1000)
        val mins = (diffMs % (3600 * 1000)) / (60 * 1000)
        val secs = (diffMs % (60 * 1000)) / 1000
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        "Active Recording / Incomplete"
    }
    canvas.drawText("Trip Duration:", 35f, infoY + lineH * 3, infoLabelPaint)
    canvas.drawText(durationText, 160f, infoY + lineH * 3, infoValuePaint)
    
    // Draw Footer Accent Line (Cyan)
    canvas.drawLine(0f, 790f, 595f, 790f, cyanPaint)
    
    // Footer text
    val footerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    canvas.drawText("Generated by Nexus Ride Tracker App • Security and Privacy Protected", 297.5f, 815f, footerPaint)
    
    document.finishPage(page)
    
    // Save report to cache file
    val pdfFile = java.io.File(context.cacheDir, "ride_summary_report_${session.id}.pdf")
    val fos = java.io.FileOutputStream(pdfFile)
    document.writeTo(fos)
    fos.close()
    document.close()
    
    return pdfFile
}
