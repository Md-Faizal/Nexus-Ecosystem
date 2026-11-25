package com.nexus.portal.ui.screens.hub.apps.ridetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.VehicleEntity
import com.nexus.portal.data.local.ridetracker.ConnectedDeviceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

data class ConnectedDevice(
    val name: String,
    val status: String,
    val icon: ImageVector,
    val isConnected: Boolean,
    val macAddress: String
)

data class VehicleStats(
    val avgSpeed: String,
    val leanAngle: String
)

data class Vehicle(
    val id: String,
    val name: String,
    val type: String,
    val odometer: String,
    val topSpeed: String,
    val fuelRange: String,
    val maintenanceDue: String,
    val isActive: Boolean,
    val imageUrl: String,
    val connectedDevices: List<ConnectedDevice>,
    val stats: VehicleStats?,
    val recentTrips: List<com.nexus.portal.data.local.ridetracker.RideSession> = emptyList(),
    val totalTrips: Int = 0,
    val isOccasional: Boolean = false
)

fun isBluetoothDeviceConnected(macAddress: String, context: Context): Boolean {
    if (macAddress.isBlank()) return false
    val normalized = com.nexus.portal.util.BluetoothMacUtils.normalize(macAddress)
    if (com.nexus.portal.util.BluetoothMacUtils.containsMac(
            com.nexus.portal.services.RideTrackerService.activelyConnectedMacs,
            normalized
        )) {
        return true
    }
    return com.nexus.portal.util.BluetoothMacUtils.probeConnectedSavedMacs(context, setOf(normalized))
        .any { com.nexus.portal.util.BluetoothMacUtils.macsEqual(it, normalized) }
}

fun mapEntityToVehicle(entity: VehicleEntity, context: Context): Vehicle {
    return mapEntityToVehicleWithComputed(
        entity = entity,
        context = context,
        computedOdometer = entity.odometer,
        computedTopSpeed = entity.topSpeed,
        computedStats = if (entity.statsAvgSpeed != null && entity.statsLeanAngle != null) {
            VehicleStats(entity.statsAvgSpeed, entity.statsLeanAngle)
        } else null,
        recentTrips = emptyList(),
        totalTrips = 0
    )
}

fun mapEntityToVehicleWithComputed(
    entity: VehicleEntity,
    context: Context,
    computedOdometer: String,
    computedTopSpeed: String,
    computedStats: VehicleStats?,
    recentTrips: List<com.nexus.portal.data.local.ridetracker.RideSession>,
    totalTrips: Int
): Vehicle {
    val converters = com.nexus.portal.data.local.ridetracker.VehicleConverters()
    val deviceList = converters.toConnectedDevicesList(entity.connectedDevicesJson)
    val devices = deviceList.map { deviceData ->
        val icon = when (deviceData.iconType) {
            "headset" -> Icons.Filled.Headset
            "car" -> Icons.Filled.DirectionsCar
            "smartphone" -> Icons.Filled.Smartphone
            "gps" -> Icons.Filled.LocationOn
            "tire" -> Icons.Filled.BrightnessHigh
            "lock" -> Icons.Filled.Lock
            else -> Icons.Filled.Bluetooth
        }
        val isConnected = isBluetoothDeviceConnected(deviceData.macAddress, context)
        ConnectedDevice(
            name = deviceData.name,
            status = if (isConnected) "Connected" else "Disconnected",
            icon = icon,
            isConnected = isConnected,
            macAddress = deviceData.macAddress
        )
    }
    
    return Vehicle(
        id = entity.id,
        name = entity.name,
        type = entity.type,
        odometer = computedOdometer,
        topSpeed = computedTopSpeed,
        fuelRange = entity.fuelRange,
        maintenanceDue = entity.maintenanceDue,
        isActive = entity.isActive,
        imageUrl = entity.imageUrl,
        connectedDevices = devices,
        stats = computedStats,
        recentTrips = recentTrips,
        totalTrips = totalTrips,
        isOccasional = entity.isOccasional
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    onBackClick: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToEditVehicle: (String) -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onRideClick: (String) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { RideDatabase.getDatabase(context).vehicleDao() }
    val vehiclesEntityList by dao.getAllVehicles().collectAsState(initial = emptyList())
    
    val sessionDao = remember { RideDatabase.getDatabase(context).rideDao() }
    val allSessions by sessionDao.getAllSessions().collectAsState(initial = emptyList())
    
    var bluetoothTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nexus.portal.util.BluetoothMacUtils.refreshKnownMacsCache(context)
        }
        com.nexus.portal.services.RideTrackerService.checkAndStartAutoDetectIfConnected(context)
        bluetoothTrigger++
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val action = intent.action
                if (action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED ||
                    action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    bluetoothTrigger++
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val vehicles = remember(vehiclesEntityList, allSessions, bluetoothTrigger) {
        vehiclesEntityList.map { entity ->
            val vehicleSessions = allSessions.filter { it.vehicleId == entity.id }
            
            // Odometer: show only the trip total
            val tripsDistanceKm = vehicleSessions.sumOf { it.totalDistanceMeters.toDouble() } / 1000.0
            val odometerStr = String.format(java.util.Locale.getDefault(), "%.1f km", tripsDistanceKm)
            
            // Top Speed: max speed recorded
            val tripsMaxSpeed = vehicleSessions.maxOfOrNull { it.maxSpeedKmh } ?: 0f
            val topSpeedStr = String.format(java.util.Locale.getDefault(), "%.1f km/h", tripsMaxSpeed)
            
            // Maintenance status string
            val maintenanceStr = if (entity.nextServiceDate > 0L) {
                val sdf = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                "Next service: " + sdf.format(java.util.Date(entity.nextServiceDate))
            } else {
                "No service date inputted"
            }
            
            // Recent stats: 7 days average speed and lean angle/lateral G
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val recentSessions = vehicleSessions.filter { it.startTime >= sevenDaysAgo }
            
            val avgSpeed = if (recentSessions.isNotEmpty()) {
                recentSessions.map { it.averageSpeedKmh }.average()
            } else {
                if (vehicleSessions.isNotEmpty()) vehicleSessions.map { it.averageSpeedKmh }.average() else 0.0
            }
            val avgSpeedStr = String.format(java.util.Locale.getDefault(), "%.1f", avgSpeed)
            
            val isTwoWheeler = entity.type.equals("Bike", ignoreCase = true) || entity.type.equals("Scooter", ignoreCase = true)
            val leanAngleOrGForceStr = if (isTwoWheeler) {
                val maxLean = vehicleSessions.maxOfOrNull { it.maxLeanAngle } ?: 0f
                String.format(java.util.Locale.getDefault(), "%.1f", maxLean)
            } else {
                val maxG = vehicleSessions.maxOfOrNull { it.peakLatG } ?: 0f
                String.format(java.util.Locale.getDefault(), "%.2f", maxG)
            }
            
            val stats = VehicleStats(avgSpeedStr, leanAngleOrGForceStr)
            
            // Fetch top 3 recent sessions for this vehicle
            val recentTrips = vehicleSessions.sortedByDescending { it.startTime }.take(3)
            
            mapEntityToVehicleWithComputed(
                entity = entity.copy(maintenanceDue = maintenanceStr),
                context = context,
                computedOdometer = odometerStr,
                computedTopSpeed = topSpeedStr,
                computedStats = stats,
                recentTrips = recentTrips,
                totalTrips = vehicleSessions.size
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()

    var activeVehicleForDevicePicker by remember { mutableStateOf<Vehicle?>(null) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var selectedDeviceCategory by remember { mutableStateOf("Helmet Intercom") }
    var manualMacAddress by remember { mutableStateOf("") }
    var manualDeviceName by remember { mutableStateOf("") }
    val bluetoothAdapter = remember { android.bluetooth.BluetoothAdapter.getDefaultAdapter() }
    var pairedBluetoothDevices by remember { mutableStateOf(emptyList<android.bluetooth.BluetoothDevice>()) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    pairedBluetoothDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                    showDevicePicker = true
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        TopAppBar(
            title = { Text("My Garage", color = Color.White, fontSize = 22.sp) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.History,
                        contentDescription = "Ride History",
                        tint = Color(0xFF00E5FF)
                    )
                }
                IconButton(onClick = onNavigateToLeaderboard) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Leaderboard,
                        contentDescription = "Leaderboard",
                        tint = Color(0xFF00E5FF)
                    )
                }
                IconButton(onClick = onNavigateToAddVehicle) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Vehicle", tint = Color(0xFF00E676))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF050505))
        )

        if (vehicles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No vehicles in garage. Click + to add one.", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            val regularVehicles = remember(vehicles) { vehicles.filter { !it.isOccasional } }
            val occasionalVehicles = remember(vehicles) { vehicles.filter { it.isOccasional } }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (regularVehicles.isNotEmpty()) {
                    item {
                        Text(
                            text = "My Garage",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(regularVehicles, key = { it.id }) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            onEditClick = onNavigateToEditVehicle,
                            onDeleteClick = { id ->
                                coroutineScope.launch {
                                    val entity = vehiclesEntityList.find { it.id == id }
                                    if (entity != null) {
                                        val currentDevices = com.nexus.portal.data.local.ridetracker.VehicleConverters().toConnectedDevicesList(entity.connectedDevicesJson)
                                        val trackingState = com.nexus.portal.services.RideTrackerService.trackingState.value
                                        if ((trackingState.isPreRecording || trackingState.isTracking) && trackingState.connectedMacAddress != null) {
                                            if (currentDevices.any { it.macAddress == trackingState.connectedMacAddress }) {
                                                val cancelIntent = Intent(context, com.nexus.portal.services.RideTrackerService::class.java).apply {
                                                    action = com.nexus.portal.services.RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                                    putExtra(com.nexus.portal.services.RideTrackerService.EXTRA_MAC_ADDRESS, trackingState.connectedMacAddress)
                                                }
                                                context.startService(cancelIntent)
                                            }
                                        }
                                    }
                                    dao.deleteVehicle(id)
                                    com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                }
                            },
                            onAddDeviceClick = { v ->
                                activeVehicleForDevicePicker = v
                                val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                } else {
                                    android.Manifest.permission.BLUETOOTH
                                }
                                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        pairedBluetoothDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                                        showDevicePicker = true
                                    } catch (e: SecurityException) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    btPermissionLauncher.launch(permission)
                                }
                            },
                            onRemoveDeviceClick = { v, mac ->
                                coroutineScope.launch {
                                    val trackingState = com.nexus.portal.services.RideTrackerService.trackingState.value
                                    if ((trackingState.isPreRecording || trackingState.isTracking) && trackingState.connectedMacAddress == mac) {
                                        val cancelIntent = Intent(context, com.nexus.portal.services.RideTrackerService::class.java).apply {
                                            action = com.nexus.portal.services.RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                            putExtra(com.nexus.portal.services.RideTrackerService.EXTRA_MAC_ADDRESS, mac)
                                        }
                                        context.startService(cancelIntent)
                                    }
                                    val entity = vehiclesEntityList.find { it.id == v.id }
                                    if (entity != null) {
                                        val currentDevices = com.nexus.portal.data.local.ridetracker.VehicleConverters().toConnectedDevicesList(entity.connectedDevicesJson)
                                        val updatedDevices = currentDevices.filter { it.macAddress != mac }
                                        val updatedJson = com.nexus.portal.data.local.ridetracker.VehicleConverters().fromConnectedDevicesList(updatedDevices)
                                        val updatedEntity = entity.copy(
                                            connectedDevicesJson = updatedJson,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        dao.insertVehicle(updatedEntity)
                                        com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                    }
                                }
                            },
                            onRideClick = onRideClick
                        )
                    }
                }

                if (occasionalVehicles.isNotEmpty()) {
                    item {
                        Text(
                            text = "Occasional Vehicles",
                            color = Color(0xFF00E5FF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(occasionalVehicles, key = { it.id }) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            onEditClick = onNavigateToEditVehicle,
                            onDeleteClick = { id ->
                                coroutineScope.launch {
                                    val entity = vehiclesEntityList.find { it.id == id }
                                    if (entity != null) {
                                        val currentDevices = com.nexus.portal.data.local.ridetracker.VehicleConverters().toConnectedDevicesList(entity.connectedDevicesJson)
                                        val trackingState = com.nexus.portal.services.RideTrackerService.trackingState.value
                                        if ((trackingState.isPreRecording || trackingState.isTracking) && trackingState.connectedMacAddress != null) {
                                            if (currentDevices.any { it.macAddress == trackingState.connectedMacAddress }) {
                                                val cancelIntent = Intent(context, com.nexus.portal.services.RideTrackerService::class.java).apply {
                                                    action = com.nexus.portal.services.RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                                    putExtra(com.nexus.portal.services.RideTrackerService.EXTRA_MAC_ADDRESS, trackingState.connectedMacAddress)
                                                }
                                                context.startService(cancelIntent)
                                            }
                                        }
                                    }
                                    dao.deleteVehicle(id)
                                    com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                }
                            },
                            onAddDeviceClick = { v ->
                                activeVehicleForDevicePicker = v
                                val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                } else {
                                    android.Manifest.permission.BLUETOOTH
                                }
                                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        pairedBluetoothDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                                        showDevicePicker = true
                                    } catch (e: SecurityException) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    btPermissionLauncher.launch(permission)
                                }
                            },
                            onRemoveDeviceClick = { v, mac ->
                                coroutineScope.launch {
                                    val trackingState = com.nexus.portal.services.RideTrackerService.trackingState.value
                                    if ((trackingState.isPreRecording || trackingState.isTracking) && trackingState.connectedMacAddress == mac) {
                                        val cancelIntent = Intent(context, com.nexus.portal.services.RideTrackerService::class.java).apply {
                                            action = com.nexus.portal.services.RideTrackerService.ACTION_CANCEL_AUTO_DETECT
                                            putExtra(com.nexus.portal.services.RideTrackerService.EXTRA_MAC_ADDRESS, mac)
                                        }
                                        context.startService(cancelIntent)
                                    }
                                    val entity = vehiclesEntityList.find { it.id == v.id }
                                    if (entity != null) {
                                        val currentDevices = com.nexus.portal.data.local.ridetracker.VehicleConverters().toConnectedDevicesList(entity.connectedDevicesJson)
                                        val updatedDevices = currentDevices.filter { it.macAddress != mac }
                                        val updatedJson = com.nexus.portal.data.local.ridetracker.VehicleConverters().fromConnectedDevicesList(updatedDevices)
                                        val updatedEntity = entity.copy(
                                            connectedDevicesJson = updatedJson,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        dao.insertVehicle(updatedEntity)
                                        com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                                    }
                                }
                            },
                            onRideClick = onRideClick
                        )
                    }
                }
            }
        }
    }

    if (showDevicePicker) {
        // Helper lambda to add a device (shared between paired-list taps & manual entry)
        fun addDeviceToVehicle(mac: String, name: String, closeDialog: Boolean = true) {
            val vehicle = activeVehicleForDevicePicker ?: return
            val entity = vehiclesEntityList.find { it.id == vehicle.id } ?: return
            val normalizedMac = com.nexus.portal.util.BluetoothMacUtils.normalize(mac)
            if (normalizedMac.isEmpty()) return
            val currentDevices = com.nexus.portal.data.local.ridetracker.VehicleConverters().toConnectedDevicesList(entity.connectedDevicesJson)
            if (currentDevices.any { com.nexus.portal.util.BluetoothMacUtils.macsEqual(it.macAddress, normalizedMac) }) return
            val icon = when (selectedDeviceCategory) {
                "Helmet Intercom" -> "headset"
                "Vehicle Dash"   -> "car"
                "Car Screen"     -> "car"
                "Scooter Dash"   -> "smartphone"
                else             -> "bluetooth"
            }
            val newDevice = com.nexus.portal.data.local.ridetracker.ConnectedDeviceData(
                name           = name.ifBlank { "Unknown Device" },
                status         = "Disconnected",
                iconType       = icon,
                macAddress     = normalizedMac,
                deviceCategory = selectedDeviceCategory
            )
            val updatedJson = com.nexus.portal.data.local.ridetracker.VehicleConverters()
                .fromConnectedDevicesList(currentDevices + newDevice)
            coroutineScope.launch {
                dao.insertVehicle(entity.copy(connectedDevicesJson = updatedJson, lastUpdated = System.currentTimeMillis()))
                com.nexus.portal.util.BluetoothMacUtils.refreshKnownMacsCache(context)
                com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                if (isBluetoothDeviceConnected(normalizedMac, context)) {
                    com.nexus.portal.services.RideTrackerService.addActiveMac(context, normalizedMac)
                    val si = Intent(context, com.nexus.portal.services.RideTrackerService::class.java).apply {
                        action = com.nexus.portal.services.RideTrackerService.ACTION_DEVICE_CONNECTED
                        putExtra(com.nexus.portal.services.RideTrackerService.EXTRA_MAC_ADDRESS, normalizedMac)
                        putExtra(com.nexus.portal.services.RideTrackerService.EXTRA_DEVICE_NAME, name)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        context.startForegroundService(si) else context.startService(si)
                }
            }
            if (closeDialog) showDevicePicker = false
        }

        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = { Text("Add Bluetooth Device", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // ── Category chips ──────────────────────────────────────────────
                    Text("Device Type", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Helmet Intercom", "Vehicle Dash", "Car Screen", "Scooter Dash").forEach { cat ->
                            FilterChip(
                                selected = selectedDeviceCategory == cat,
                                onClick = { selectedDeviceCategory = cat },
                                label = { Text(cat, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF),
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E1E1E),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    // ── Manual MAC entry (for hidden/unlisted devices e.g. Suzuki dash) ──
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Enter MAC Manually", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        "Use this for devices not visible in the paired list (e.g. Suzuki Ride Connect dash).",
                        color = Color(0xFF555555), fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = manualDeviceName,
                        onValueChange = { manualDeviceName = it },
                        label = { Text("Device Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Suzuki Dash", color = Color(0xFF555555), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color(0xFF00E5FF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = manualMacAddress,
                        onValueChange = { manualMacAddress = it.uppercase().take(20) },
                        label = { Text("MAC Address", fontSize = 12.sp) },
                        placeholder = { Text("AA:BB:CC:DD:EE:FF", color = Color(0xFF555555), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color(0xFF00E5FF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val cleanMac = remember(manualMacAddress) { manualMacAddress.replace('O', '0').replace(Regex("[^0-9A-F]"), "") }
                    val isMacValid = cleanMac.length == 12
                    Button(
                        onClick = {
                            if (isMacValid) {
                                val formattedMac = cleanMac.chunked(2).joinToString(":")
                                addDeviceToVehicle(formattedMac, manualDeviceName.ifBlank { "Manual Device" })
                                manualMacAddress = ""
                                manualDeviceName = ""
                            }
                        },
                        enabled = isMacValid,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF1E1E1E),
                            disabledContentColor = Color(0xFF444444)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Manual Device", fontWeight = FontWeight.Bold)
                    }

                    // ── Paired devices list ─────────────────────────────────────────
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Paired Devices", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (pairedBluetoothDevices.isEmpty()) {
                        Text("No paired devices found.", color = Color(0xFF555555), fontSize = 14.sp)
                    } else {
                        pairedBluetoothDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            addDeviceToVehicle(
                                                mac  = device.address,
                                                name = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
                                            )
                                        } catch (e: SecurityException) { e.printStackTrace() }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }, color = Color.White, fontSize = 14.sp)
                                    Text(device.address, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false; manualMacAddress = ""; manualDeviceName = "" }) {
                    Text("Close", color = Color(0xFF00E5FF))
                }
            },
            containerColor = Color(0xFF0F0F0F)
        )
    }
}

// Removed AddVehicleDialog


@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onAddDeviceClick: (Vehicle) -> Unit,
    onRemoveDeviceClick: (Vehicle, String) -> Unit,
    onRideClick: (String) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))
    ) {
        Column {
            // Image Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                coil.compose.SubcomposeAsyncImage(
                    model = remember(vehicle.imageUrl) { getCoilImageModel(vehicle.imageUrl) },
                    contentDescription = vehicle.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF00E5FF))
                        }
                    }
                )
                
                // Gradient overlay for readability
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xCC000000), Color(0x33000000), Color(0xDD000000))
                        )
                    )
                )

                // Top content: Name, Type, Active/Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vehicle.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(vehicle.type, color = Color.LightGray, fontSize = 16.sp)
                            if (vehicle.isOccasional) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Occasional", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onEditClick(vehicle.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Vehicle",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteClick(vehicle.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Vehicle",
                                tint = Color(0xFFFF5252)
                            )
                        }
                        if (vehicle.isActive) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Active", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom Left: Odometer, Top Speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("Odometer", color = Color.LightGray, fontSize = 12.sp)
                        Text(vehicle.odometer, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    Column {
                        Text("Top Speed", color = Color.LightGray, fontSize = 12.sp)
                        Text(vehicle.topSpeed, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bottom Right: Share Action Area
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xAA000000), RoundedCornerShape(topStart = 16.dp))
                        .clip(RoundedCornerShape(topStart = 16.dp))
                        .clickable {
                            try {
                                val pdfFile = generateVehiclePdfReport(context, vehicle)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "com.nexus.portal.fileprovider",
                                    pdfFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Vehicle PDF Report"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "Failed to generate PDF Report", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share\nRide", color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                    }
                }
            }

            // Details Section
            Column(modifier = Modifier.padding(16.dp)) {
                // Maintenance & Total Trips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Maintenance", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Build, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(vehicle.maintenanceDue, color = Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Trips", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Explore, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${vehicle.totalTrips}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Connected Devices
                Text("Connected Devices", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    vehicle.connectedDevices.forEach { device ->
                        DeviceItem(device, onDeleteClick = { onRemoveDeviceClick(vehicle, device.macAddress) })
                    }
                    AddDeviceItem(onClick = { onAddDeviceClick(vehicle) })
                }

                // Stats and Trip History
                if (vehicle.stats != null || vehicle.recentTrips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Recent Stats
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("Recent Stats", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("7-day Avg", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(vehicle.stats?.avgSpeed ?: "--", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("km/h", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                }
                                Box(modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(Color.DarkGray))
                                Column(modifier = Modifier.weight(1f)) {
                                    val isTwoWheeler = vehicle.type.equals("Bike", ignoreCase = true) || vehicle.type.equals("Scooter", ignoreCase = true)
                                    Text(if (isTwoWheeler) "Max Lean" else "Peak Lat. G", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(vehicle.stats?.leanAngle ?: "--", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(if (isTwoWheeler) "°" else "G", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Trip History
                        Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                            Text("Trip History", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (vehicle.recentTrips.isEmpty()) {
                                Text("No trips yet", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                val sdf = remember { java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    vehicle.recentTrips.forEach { trip ->
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF222222))
                                                .clickable { onRideClick(trip.id) }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = sdf.format(java.util.Date(trip.startTime)),
                                                    color = Color.LightGray,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
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
        }
    }
}

@Composable
fun DeviceItem(device: ConnectedDevice, onDeleteClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF222222), RoundedCornerShape(20.dp))
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = device.icon,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(device.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = device.status,
                color = if (device.isConnected) Color(0xFF00E676) else Color.Gray,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove Device",
            tint = Color.Gray,
            modifier = Modifier
                .size(16.dp)
                .clickable { onDeleteClick() }
        )
    }
}

@Composable
fun AddDeviceItem(onClick: () -> Unit) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .drawBehind {
                    drawCircle(
                        color = Color.Gray,
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Device", tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text("Add Device", color = Color.White, fontSize = 13.sp)
            Text("Bluetooth", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

fun generateVehiclePdfReport(context: Context, vehicle: Vehicle): java.io.File {
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
        textSize = 20f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val subtitlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00E5FF")
        textSize = 11f
        isAntiAlias = true
    }
    val datePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        isAntiAlias = true
    }
    
    // Draw Header Text
    canvas.drawText("NEXUS PORTAL - MY GARAGE", 30f, 45f, titlePaint)
    canvas.drawText("VEHICLE DETAILS & HISTORY REPORT", 30f, 70f, subtitlePaint)
    
    val sdf = java.text.SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    val reportDate = sdf.format(java.util.Date())
    canvas.drawText("Generated on: $reportDate", 30f, 90f, datePaint)
    
    // Content paints
    val sectionPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 13f
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
        textSize = 9f
        isAntiAlias = true
    }
    val valuePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
    }
    
    // Section: Vehicle Info
    canvas.drawText("VEHICLE OVERVIEW: ${vehicle.name.uppercase(java.util.Locale.getDefault())}", 30f, 150f, sectionPaint)
    canvas.drawLine(30f, 158f, 565f, 158f, android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#222222")
        strokeWidth = 1f
    })
    
    // Helper to draw a stats card
    fun drawStatCard(x: Float, y: Float, w: Float, h: Float, label: String, value: String) {
        val rect = android.graphics.RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, cardBorderPaint)
        canvas.drawText(label.uppercase(java.util.Locale.getDefault()), x + 12f, y + 20f, labelPaint)
        canvas.drawText(value, x + 12f, y + 42f, valuePaint)
    }
    
    // Stats Grid
    val col1X = 30f
    val col2X = 305f
    val row1Y = 175f
    val row2Y = 235f
    val row3Y = 295f
    val cardW = 260f
    val cardH = 50f
    
    val isTwoWheeler = vehicle.type.equals("Bike", ignoreCase = true) || vehicle.type.equals("Scooter", ignoreCase = true)
    
    drawStatCard(col1X, row1Y, cardW, cardH, "Vehicle Name", vehicle.name)
    drawStatCard(col2X, row1Y, cardW, cardH, "Type", vehicle.type)
    
    drawStatCard(col1X, row2Y, cardW, cardH, "Trip Total Odometer", vehicle.odometer)
    drawStatCard(col2X, row2Y, cardW, cardH, "Top Speed", vehicle.topSpeed)
    
    drawStatCard(col1X, row3Y, cardW, cardH, "Total Trips", "${vehicle.totalTrips}")
    drawStatCard(col2X, row3Y, cardW, cardH, "Maintenance Status", vehicle.maintenanceDue)
    
    // Section: Recent Stats (7-Day stats)
    canvas.drawText("7-DAY PERFORMANCE STATS", 30f, 370f, sectionPaint)
    canvas.drawLine(30f, 378f, 565f, 378f, android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#222222")
        strokeWidth = 1f
    })
    
    val avgSpeedVal = if (vehicle.stats?.avgSpeed != null) "${vehicle.stats.avgSpeed} km/h" else "--"
    val peakValue = if (vehicle.stats?.leanAngle != null) {
        if (isTwoWheeler) "${vehicle.stats.leanAngle}°" else "${vehicle.stats.leanAngle} G"
    } else "--"
    val peakLabel = if (isTwoWheeler) "Max Lean Angle" else "Peak Lateral G"
    
    drawStatCard(col1X, row1Y + 220f, cardW, cardH, "7-Day Avg Speed", avgSpeedVal)
    drawStatCard(col2X, row1Y + 220f, cardW, cardH, peakLabel, peakValue)
    
    // Section: Connected Devices
    canvas.drawText("CONNECTED DEVICES", 30f, 470f, sectionPaint)
    canvas.drawLine(30f, 478f, 565f, 478f, android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#222222")
        strokeWidth = 1f
    })
    
    val deviceTextPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 10f
        isAntiAlias = true
    }
    
    var deviceY = 498f
    if (vehicle.connectedDevices.isEmpty()) {
        canvas.drawText("No connected devices registered for this vehicle.", 35f, deviceY, labelPaint)
    } else {
        vehicle.connectedDevices.forEach { device ->
            canvas.drawText("• ${device.name} (${device.macAddress}) - ${device.status}", 35f, deviceY, deviceTextPaint)
            deviceY += 18f
        }
    }
    
    // Section: Recent Trips
    val tripsStartY = 570f
    canvas.drawText("RECENT TRIPS HISTORY", 30f, tripsStartY, sectionPaint)
    canvas.drawLine(30f, tripsStartY + 8f, 565f, tripsStartY + 8f, android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#222222")
        strokeWidth = 1f
    })
    
    var tripY = tripsStartY + 28f
    if (vehicle.recentTrips.isEmpty()) {
        canvas.drawText("No trips recorded yet.", 35f, tripY, labelPaint)
    } else {
        // Draw Table Header
        val headerTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00E5FF")
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("DATE", 35f, tripY, headerTextPaint)
        canvas.drawText("DISTANCE", 170f, tripY, headerTextPaint)
        canvas.drawText("MAX SPEED", 280f, tripY, headerTextPaint)
        canvas.drawText("AVG SPEED", 390f, tripY, headerTextPaint)
        canvas.drawText(if (isTwoWheeler) "MAX LEAN" else "PEAK LAT G", 490f, tripY, headerTextPaint)
        
        canvas.drawLine(30f, tripY + 6f, 565f, tripY + 6f, android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#333333")
            strokeWidth = 0.5f
        })
        
        tripY += 22f
        
        val tripSdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        vehicle.recentTrips.forEach { trip ->
            val tripDateStr = tripSdf.format(java.util.Date(trip.startTime))
            val tripDist = String.format(java.util.Locale.getDefault(), "%.2f km", trip.totalDistanceMeters / 1000f)
            val tripMax = String.format(java.util.Locale.getDefault(), "%.1f km/h", trip.maxSpeedKmh)
            val tripAvg = String.format(java.util.Locale.getDefault(), "%.1f km/h", trip.averageSpeedKmh)
            val tripPeak = if (isTwoWheeler) {
                String.format(java.util.Locale.getDefault(), "%.1f°", trip.maxLeanAngle)
            } else {
                String.format(java.util.Locale.getDefault(), "%.2f G", trip.peakLatG)
            }
            
            canvas.drawText(tripDateStr, 35f, tripY, deviceTextPaint)
            canvas.drawText(tripDist, 170f, tripY, deviceTextPaint)
            canvas.drawText(tripMax, 280f, tripY, deviceTextPaint)
            canvas.drawText(tripAvg, 390f, tripY, deviceTextPaint)
            canvas.drawText(tripPeak, 490f, tripY, deviceTextPaint)
            
            canvas.drawLine(30f, tripY + 6f, 565f, tripY + 6f, android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#222222")
                strokeWidth = 0.5f
            })
            
            tripY += 20f
        }
    }
    
    // Draw Footer Accent Line (Cyan)
    canvas.drawLine(0f, 790f, 595f, 790f, cyanPaint)
    
    // Footer text
    val footerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    canvas.drawText("Generated by Nexus Garage Manager • Security and Privacy Protected", 297.5f, 815f, footerPaint)
    
    document.finishPage(page)
    
    // Save report to cache file
    val pdfFile = java.io.File(context.cacheDir, "vehicle_summary_report_${vehicle.id}.pdf")
    val fos = java.io.FileOutputStream(pdfFile)
    document.writeTo(fos)
    fos.close()
    document.close()
    
    return pdfFile
}
