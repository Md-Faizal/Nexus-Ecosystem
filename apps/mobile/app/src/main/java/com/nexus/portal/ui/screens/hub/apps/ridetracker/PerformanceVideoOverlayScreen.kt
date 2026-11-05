package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalConfiguration
import com.google.android.gms.location.*
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleEntity
import com.nexus.portal.services.PerformanceTelemetryTracker
import com.nexus.portal.services.VehicleTelemetryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceVideoOverlayScreen(
    onBackClick: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Telemetry Engine & Tracker
    val telemetryEngine = remember { VehicleTelemetryEngine() }
    val tracker = remember { PerformanceTelemetryTracker() }
    val trackerState by tracker.state.collectAsState()
    val db = remember { RideDatabase.getDatabase(context) }

    // Local state
    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val aud = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        val loc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (cam == PackageManager.PERMISSION_GRANTED && aud == PackageManager.PERMISSION_GRANTED && loc == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
            hasAudioPermission = true
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    // Passive Sensor & GPS Listeners
    var passiveLocation by remember { mutableStateOf<Location?>(null) }
    var currentLatG by remember { mutableStateOf(0f) }
    var currentLongG by remember { mutableStateOf(0f) }

    DisposableEffect(trackerState.isRecording, hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val gyroValues = FloatArray(3)
        var hasGyro = false

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
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
                        val telemetry = telemetryEngine.processSensors(
                            accelValues = event.values,
                            gyroValues = if (hasGyro) gyroValues else null,
                            speedMps = passiveLocation?.speed ?: 0f,
                            isTwoWheeler = false // Defaults to Car style for overlay screen
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

        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            accel?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
            gyro?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        } catch (_: SecurityException) {}

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // Camera PreviewView Setup
    val previewView = remember { PreviewView(context) }
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val cameraProviderProvider = ProcessCameraProvider.getInstance(context)
            cameraProviderProvider.addListener({
                val provider = cameraProviderProvider.get()
                val preview = Preview.Builder().build()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                preview.setSurfaceProvider(previewView.surfaceProvider)
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)
                } catch (e: Exception) {
                    Log.e("CameraOverlay", "Error binding camera", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Current Telemetry Values
    val speed = trackerState.currentSpeedKmh
    val elevation = passiveLocation?.altitude ?: 0.0
    val tempC = 24.0 // Simulated ambient temperature

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission required for overlay mode", color = Color.White)
            }
        }

        // --- HUD Overlay Layout ---
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLandscape) 32.dp else 20.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                if (trackerState.isRecording) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        val min = (trackerState.elapsedMs / 60000) % 60
                        val sec = (trackerState.elapsedMs / 1000) % 60
                        Text(
                            text = String.format("REC %02d:%02d", min, sec),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Dashboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(if (isLandscape) 24.dp else 16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(if (isLandscape) 24.dp else 16.dp))
                    .padding(if (isLandscape) 16.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f", speed),
                        color = Color.White,
                        fontSize = if (isLandscape) 48.sp else 36.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("km/h", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Temp & Elevation
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TEMP", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                        Text(String.format("%.0f°C", tempC), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ELEV", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                        Text(String.format("%.0f m", elevation), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // G-Force Map
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 64.dp else 50.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f
                        
                        drawCircle(color = Color.White.copy(alpha = 0.1f), radius = radius * 0.5f, style = Stroke(1f))
                        drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, center.y), Offset(size.width, center.y), 1f)
                        drawLine(Color.White.copy(alpha = 0.1f), Offset(center.x, 0f), Offset(center.x, size.height), 1f)
                        
                        val ptX = center.x + (currentLatG / 1.0f) * radius
                        val ptY = center.y - (currentLongG / 1.0f) * radius
                        
                        drawCircle(
                            color = Color(0xFF00E676),
                            radius = 4f,
                            center = Offset(ptX.coerceIn(0f, size.width), ptY.coerceIn(0f, size.height))
                        )
                    }
                }
                
                // Record Button
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 56.dp else 48.dp)
                        .clip(CircleShape)
                        .background(if (trackerState.isRecording) Color.Red else Color.White)
                        .clickable {
                            if (!trackerState.isRecording) {
                                tracker.startRecording()
                            } else {
                                coroutineScope.launch {
                                    val run = withContext(Dispatchers.IO) {
                                        tracker.stopRecording(
                                            vehicleId = null,
                                            vehicleName = "Video Run",
                                            vehicleType = "four_wheeler"
                                        )
                                    }
                                    if (run != null) {
                                        val activeProfileId = com.nexus.portal.data.repository.ProfileRepository.activeProfile.value.id
                                        val currentDeviceId = com.nexus.portal.data.repository.ProfileRepository.deviceId
                                        val mockVideoPath = File(context.cacheDir, "mock_run_${run.id}.mp4").absolutePath
                                        val updatedRun = run.copy(
                                            videoPath = mockVideoPath,
                                            profileId = activeProfileId,
                                            deviceId = currentDeviceId
                                        )
                                        withContext(Dispatchers.IO) {
                                            db.performanceRunDao().insertRun(updatedRun)
                                        }
                                        Toast.makeText(context, "Video run saved successfully!", Toast.LENGTH_SHORT).show()
                                        com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                        onNavigateToDetail(run.id)
                                    } else {
                                        Toast.makeText(context, "Run too short to save", Toast.LENGTH_SHORT).show()
                                        tracker.resetTracking()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (trackerState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (trackerState.isRecording) Color.White else Color.Red,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
