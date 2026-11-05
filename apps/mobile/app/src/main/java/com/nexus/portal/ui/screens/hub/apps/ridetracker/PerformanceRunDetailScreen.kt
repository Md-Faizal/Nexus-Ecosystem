package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.data.local.ridetracker.PerformanceRunEntity
import com.nexus.portal.data.local.ridetracker.RideDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.os.Bundle
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLngBounds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceRunDetailScreen(
    runId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { RideDatabase.getDatabase(context) }
    
    var run by remember { mutableStateOf<PerformanceRunEntity?>(null) }
    var points by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isImperial by remember { mutableStateOf(false) }

    LaunchedEffect(runId) {
        withContext(Dispatchers.IO) {
            val entity = db.performanceRunDao().getRunById(runId)
            if (entity != null) {
                run = entity
                // Parse points
                val list = mutableListOf<ChartPoint>()
                try {
                    val arr = JSONArray(entity.telemetryJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            ChartPoint(
                                elapsedMs = obj.optLong("elapsedMs"),
                                speedKmh = obj.optDouble("speedKmh").toFloat(),
                                altitude = obj.optDouble("altitude").toFloat(),
                                slope = obj.optDouble("slopePercent").toFloat(),
                                longG = obj.optDouble("longitudinalG").toFloat(),
                                latG = obj.optDouble("lateralG").toFloat(),
                                lat = obj.optDouble("lat", 0.0),
                                lng = obj.optDouble("lng", 0.0)
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Fallback to mock data points if empty (to keep chart functional for mock UI)
                if (list.isEmpty()) {
                    val duration = 8000L
                    val steps = 40
                    for (i in 0..steps) {
                        val fraction = i.toFloat() / steps
                        val elapsed = (fraction * duration).toLong()
                        // 0-100kmh acceleration curve style
                        val speedVal = (1 - kotlin.math.exp(-3f * fraction)) * 105f
                        val alt = 120f + (fraction * 15f)
                        val sl = 2.5f
                        val loG = (1f - fraction) * 0.8f
                        val laG = 0.05f
                        list.add(ChartPoint(elapsed, speedVal, alt, sl, loG, laG, 0.0, 0.0))
                    }
                }
                points = list
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0C)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
        return
    }

    if (run == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0C)), contentAlignment = Alignment.Center) {
            Text("Run details not found.", color = Color.White)
        }
        return
    }

    val finalRun = run!!
    val dateStr = SimpleDateFormat("MMMM dd, yyyy  hh:mm a", Locale.getDefault()).format(Date(finalRun.timestamp))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Run Result", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { isImperial = !isImperial }) {
                        Text(if (isImperial) "MPH" else "KM/H", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    db.performanceRunDao().deleteRunById(runId)
                                }
                                Toast.makeText(context, "Run deleted.", Toast.LENGTH_SHORT).show()
                                com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF1744))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0C))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0C))
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Vehicle & Timestamp Header card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141418))
                        .border(1.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = finalRun.vehicleName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dateStr,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Recorded Times Metrics grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Time & Distance Metrics", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        val accelStr = if (isImperial) {
                            finalRun.accel0To60Mph?.let { String.format("%.2f s", it) } ?: "-- s"
                        } else {
                            finalRun.accel0To100Kmh?.let { String.format("%.2f s", it) } ?: "-- s"
                        }
                        MetricBlock(
                            label = if (isImperial) "0-60 mph" else "0-100 km/h (0-62 mph)",
                            value = accelStr,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.weight(1f)
                        )
                        val dragStr = finalRun.drag1_4Time?.let { String.format("%.2f s", it) } ?: "-- s"
                        MetricBlock(
                            label = "1/4 Mile (Drag)",
                            value = dragStr,
                            color = Color(0xFF00E676),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        val brDistStr = finalRun.brakingDistanceMeters?.let {
                            if (isImperial) String.format("%.1f ft", it * 3.28084f) else String.format("%.1f m", it)
                        } ?: "--"
                        MetricBlock(
                            label = "Braking Distance",
                            value = brDistStr,
                            color = Color(0xFFFF1744),
                            modifier = Modifier.weight(1f)
                        )
                        MetricBlock(
                            label = "Peak Lateral G-Force",
                            value = String.format("%.2f G", finalRun.peakLateralG),
                            color = Color(0xFFFFAB00),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Interactive Canvas Telemetry Chart Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Interactive Telemetry Chart", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Drag to inspect exact speed and slope values at any timestamp.", color = Color.DarkGray, fontSize = 10.sp)
                    InteractivePerformanceChart(points = points, isImperial = isImperial)
                }
            }

            // Video Overlay Playback card (if videoPath is recorded or simulated)
            if (finalRun.videoPath != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Recorded Overlay Run", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .border(1.dp, Color(0xFF222228), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Mock video dashboard overlay
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Play Run Video with Overlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Export format: ${finalRun.videoPath}", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // Map Replay Card
            val validPoints = points.filter { it.lat != 0.0 && it.lng != 0.0 }
            if (validPoints.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GPS Route Map", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        val mapView = remember {
                            try {
                                MapView(context)
                            } catch (t: Throwable) {
                                android.util.Log.e("PerformanceRunDetailScreen", "Failed to initialize MapView due to credentials mismatch", t)
                                null
                            }
                        }
                        var mapplsMap by remember { mutableStateOf<MapplsMap?>(null) }
                        
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
                                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .border(1.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
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
                                                    
                                                    val polylineOptions = PolylineOptions()
                                                        .color(android.graphics.Color.parseColor("#00E5FF"))
                                                        .width(8f)
                                                        
                                                    val boundsBuilder = LatLngBounds.Builder()
                                                    
                                                    validPoints.forEach { pt ->
                                                        val latLng = LatLng(pt.lat, pt.lng)
                                                        polylineOptions.add(latLng)
                                                        boundsBuilder.include(latLng)
                                                    }
                                                    
                                                    map.addPolyline(polylineOptions)
                                                    
                                                    // Start marker
                                                    val startPt = validPoints.first()
                                                    map.addMarker(MarkerOptions().position(LatLng(startPt.lat, startPt.lng)).title("Start"))
                                                    
                                                    // End marker
                                                    val endPt = validPoints.last()
                                                    map.addMarker(MarkerOptions().position(LatLng(endPt.lat, endPt.lng)).title("End"))
                                                    
                                                    try {
                                                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 50, 50, 50, 50))
                                                    } catch (e: Exception) {}
                                                }
                                                override fun onMapError(p0: Int, p1: String?) {}
                                            })
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
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
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBlock(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141418))
            .border(1.dp, Color(0xFF222228), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

data class ChartPoint(
    val elapsedMs: Long,
    val speedKmh: Float,
    val altitude: Float,
    val slope: Float,
    val longG: Float,
    val latG: Float,
    val lat: Double,
    val lng: Double
)

@Composable
fun InteractivePerformanceChart(points: List<ChartPoint>, isImperial: Boolean) {
    var dragXFraction by remember { mutableStateOf<Float?>(null) }
    
    val maxSpeed = remember(points) { points.maxOfOrNull { it.speedKmh }?.coerceAtLeast(100f) ?: 100f }
    val maxAltitude = remember(points) { points.maxOfOrNull { it.altitude }?.coerceAtLeast(150f) ?: 150f }
    val minAltitude = remember(points) { points.minOfOrNull { it.altitude }?.coerceAtMost(0f) ?: 0f }
    
    val inspectedPoint = remember(dragXFraction, points) {
        val frac = dragXFraction
        if (frac != null && points.isNotEmpty()) {
            val index = (frac * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
            points[index]
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141418))
            .border(1.dp, Color(0xFF222228), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            // Inspector readout panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inspectedPoint != null) {
                    Text(
                        text = String.format("Time: %.1fs", inspectedPoint.elapsedMs / 1000f),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("Speed: %.1f %s", if (isImperial) inspectedPoint.speedKmh * 0.621371f else inspectedPoint.speedKmh, if (isImperial) "mph" else "km/h"),
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("Slope: %.1f%%", inspectedPoint.slope),
                        color = if (inspectedPoint.slope >= 0) Color(0xFF00E676) else Color(0xFFFF1744),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("Speed", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Road Slope", color = Color(0xFFFFAB00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            // The Canvas chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragEnd = { dragXFraction = null },
                            onDragCancel = { dragXFraction = null },
                            onDrag = { change, _ ->
                                val xFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                dragXFraction = xFraction
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    if (points.isEmpty()) return@Canvas
                    
                    // Draw Grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = (height / gridLines) * i
                        drawLine(
                            color = Color(0xFF222228),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // --- PLOT SPEED (Cyan Line with Fill) ---
                    val speedPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = (i.toFloat() / (points.size - 1)) * width
                        // Y coordinate (0 speed maps to bottom of canvas)
                        val y = height - (pt.speedKmh / maxSpeed) * height
                        if (i == 0) {
                            speedPath.moveTo(x, y)
                        } else {
                            speedPath.lineTo(x, y)
                        }
                    }
                    // Speed line
                    drawPath(
                        path = speedPath,
                        color = Color(0xFF00E5FF),
                        style = Stroke(width = 4f)
                    )
                    
                    // Speed translucent fill
                    val speedFillPath = Path().apply {
                        addPath(speedPath)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(
                        path = speedFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent)
                        )
                    )

                    // --- PLOT ALTITUDE/SLOPE (Orange Dashed Line) ---
                    val altPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = (i.toFloat() / (points.size - 1)) * width
                        val yRange = maxAltitude - minAltitude
                        val y = if (yRange > 0) {
                            height - ((pt.altitude - minAltitude) / yRange) * height
                        } else height / 2f
                        
                        if (i == 0) {
                            altPath.moveTo(x, y)
                        } else {
                            altPath.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = altPath,
                        color = Color(0xFFFFAB00),
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )

                    // --- DRAW INSPECT CROSSHAIR RULER ---
                    dragXFraction?.let { frac ->
                        val x = frac * width
                        // Draw vertical line
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 2f
                        )
                        
                        // Draw inspection dot on speed line
                        val index = (frac * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                        val inspectPt = points[index]
                        val inspectY = height - (inspectPt.speedKmh / maxSpeed) * height
                        
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = Offset(x, inspectY)
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 10f,
                            center = Offset(x, inspectY),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
        }
    }
}
