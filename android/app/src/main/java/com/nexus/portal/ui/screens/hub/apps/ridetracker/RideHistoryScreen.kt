package com.nexus.portal.ui.screens.hub.apps.ridetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Sort
import android.app.DatePickerDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.RideSession
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.ui.viewinterop.AndroidView
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.firstOrNull
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.rememberLazyListState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    onBackClick: () -> Unit,
    onRideClick: (String) -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToGarage: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { RideDatabase.getDatabase(context).rideDao() }
    val vehicleDao = remember { RideDatabase.getDatabase(context).vehicleDao() }
    
    fun getStartOfDay(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfDay(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    var selectedDate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(getStartOfDay(System.currentTimeMillis())) }
    var selectedVehicleFilter by remember { mutableStateOf<VehicleFilter>(VehicleFilter.All) }
    var activeSortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    val allSessionsState = dao.getAllSessions().collectAsState(initial = emptyList())

    val lazyListState = rememberLazyListState()
    var isPillVisible by remember { mutableStateOf(true) }
    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        val currentIndex = lazyListState.firstVisibleItemIndex
        val currentOffset = lazyListState.firstVisibleItemScrollOffset

        if (currentIndex == lastScrollIndex) {
            if (currentOffset > lastScrollOffset) {
                isPillVisible = false
            } else if (currentOffset < lastScrollOffset) {
                isPillVisible = true
            }
        } else if (currentIndex > lastScrollIndex) {
            isPillVisible = false
        } else {
            isPillVisible = true
        }
        lastScrollIndex = currentIndex
        lastScrollOffset = currentOffset
    }

    val sessionsState = dao.getSessionsFiltered(
        filterVehicle = when (selectedVehicleFilter) {
            VehicleFilter.All -> 0
            is VehicleFilter.Specific -> 1
            VehicleFilter.Unlinked -> 2
        },
        vehicleId = (selectedVehicleFilter as? VehicleFilter.Specific)?.vehicleId,
        filterDate = if (selectedDate == null) 0 else 1,
        startOfDay = selectedDate?.let { getStartOfDay(it) } ?: 0L,
        endOfDay = selectedDate?.let { getEndOfDay(it) } ?: 0L
    ).collectAsState(initial = null)
    
    val vehiclesList by vehicleDao.getAllVehicles().collectAsState(initial = emptyList())
    
    val sortedSessions = remember(sessionsState.value, activeSortOption, vehiclesList) {
        sessionsState.value?.let { list ->
            when (activeSortOption) {
                SortOption.DATE_DESC -> list.sortedByDescending { it.startTime }
                SortOption.DATE_ASC -> list.sortedBy { it.startTime }
                SortOption.VEHICLE -> list.sortedBy { session ->
                    vehiclesList.find { it.id == session.vehicleId }?.let { 
                        if (it.make.isBlank() && it.model.isBlank()) it.name else "${it.make} ${it.model}"
                    } ?: session.vehicleId?.removePrefix("custom:") ?: "zzzzz"
                }
                SortOption.DISTANCE -> list.sortedByDescending { it.totalDistanceMeters }
                SortOption.DURATION -> list.sortedByDescending {
                    val durationMs = (it.endTime ?: System.currentTimeMillis()) - it.startTime
                    durationMs
                }
            }
        }
    }
    
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        TopAppBar(
            title = { Text("Ride History", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Sort,
                            contentDescription = "Sort Rides",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.displayName,
                                            color = if (activeSortOption == option) Color(0xFF00E5FF) else Color.White,
                                            fontSize = 14.sp
                                        )
                                        if (activeSortOption == option) {
                                            Text(
                                                text = "✓",
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    activeSortOption = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onNavigateToGarage) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.DirectionsCar,
                        contentDescription = "My Garage",
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
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF050505))
        )

        // Date Picker UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { 
                    selectedDate = selectedDate?.let { it - 86400000L } 
                },
                enabled = selectedDate != null
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day", tint = if (selectedDate != null) Color.White else Color.Gray)
            }

            val today = getStartOfDay(System.currentTimeMillis())
            val canGoNext = selectedDate != null && getStartOfDay(selectedDate!!) < today

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = selectedDate != null) {
                showCalendarDialog = true
            }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date", tint = if (selectedDate != null) Color(0xFF00E5FF) else Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                
                val dateLabel = if (selectedDate == null) {
                    "All Rides"
                } else {
                    val yesterday = today - 86400000L
                    val tomorrow = today + 86400000L
                    val currentSelected = getStartOfDay(selectedDate!!)
                    when (currentSelected) {
                        today -> "Today"
                        yesterday -> "Yesterday"
                        tomorrow -> "Tomorrow"
                        else -> {
                            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                            sdf.format(Date(currentSelected))
                        }
                    }
                }
                
                Text(
                    text = dateLabel,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = { 
                    if (canGoNext) {
                        selectedDate = selectedDate?.let { it + 86400000L } 
                    }
                },
                enabled = canGoNext
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day", tint = if (canGoNext) Color.White else Color.Gray)
            }
            
            TextButton(onClick = { selectedDate = if (selectedDate == null) getStartOfDay(System.currentTimeMillis()) else null }) {
                Text(if (selectedDate == null) "Today" else "All", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }
        }

        HorizontalDivider(color = Color(0xFF1C1C1C), thickness = 1.dp)

        // Vehicle Filter UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VehicleFilterChip(
                text = "All Vehicles",
                selected = selectedVehicleFilter is VehicleFilter.All,
                onClick = { selectedVehicleFilter = VehicleFilter.All }
            )
            VehicleFilterChip(
                text = "Unlinked Rides",
                selected = selectedVehicleFilter is VehicleFilter.Unlinked,
                onClick = { selectedVehicleFilter = VehicleFilter.Unlinked }
            )
            vehiclesList.forEach { vehicle ->
                val vehicleLabel = if (vehicle.make.isBlank() && vehicle.model.isBlank()) vehicle.name else "${vehicle.make} ${vehicle.model}"
                VehicleFilterChip(
                    text = vehicleLabel,
                    selected = (selectedVehicleFilter as? VehicleFilter.Specific)?.vehicleId == vehicle.id,
                    onClick = { selectedVehicleFilter = VehicleFilter.Specific(vehicle.id) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (sortedSessions == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else if (sortedSessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val noRidesMsg = when {
                        selectedDate == null && selectedVehicleFilter is VehicleFilter.All -> "No rides recorded yet."
                        selectedDate != null && selectedVehicleFilter is VehicleFilter.All -> "No rides recorded for this date."
                        selectedDate == null && selectedVehicleFilter is VehicleFilter.Specific -> "No rides recorded for this vehicle."
                        selectedDate != null && selectedVehicleFilter is VehicleFilter.Specific -> "No rides recorded for this vehicle on this date."
                        selectedDate == null && selectedVehicleFilter is VehicleFilter.Unlinked -> "No unlinked rides recorded."
                        else -> "No unlinked rides recorded for this date."
                    }
                    Text(noRidesMsg, color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedSessions, key = { it.id }) { session ->
                        RideHistoryCard(
                            session = session, 
                            vehiclesList = vehiclesList,
                            onClick = { onRideClick(session.id) },
                            onDelete = {
                                coroutineScope.launch {
                                    dao.deleteSession(session.id, System.currentTimeMillis())
                                }
                            }
                        )
                    }
                }
            }

            val listNotEmpty = sortedSessions?.isNotEmpty() == true
            val showPill = (isPillVisible || (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0)) && listNotEmpty

            androidx.compose.animation.AnimatedVisibility(
                visible = showPill,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                val nonNullSessions = sortedSessions ?: emptyList()
                val totalRides = nonNullSessions.size
                val totalDistanceKm = nonNullSessions.sumOf { it.totalDistanceMeters.toDouble() } / 1000f
                val totalDurationMs = nonNullSessions.sumOf {
                    (it.endTime ?: System.currentTimeMillis()) - it.startTime
                }

                val h = totalDurationMs / 3600000
                val m = (totalDurationMs / 60000) % 60
                val durationStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0xEC1A1A1C), // Glassy translucent dark grey
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rides", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            Text("$totalRides", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        VerticalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(24.dp).width(1.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distance", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f km", totalDistanceKm),
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        VerticalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(24.dp).width(1.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Duration", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            Text(durationStr, color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCalendarDialog) {
        val allSessions = allSessionsState.value ?: emptyList()
        val calendarSessions = remember(allSessions, selectedVehicleFilter) {
            when (val filter = selectedVehicleFilter) {
                is VehicleFilter.All -> allSessions
                is VehicleFilter.Unlinked -> allSessions.filter { it.vehicleId == null }
                is VehicleFilter.Specific -> allSessions.filter { it.vehicleId == filter.vehicleId }
            }
        }
        val filterSubtitle = when (val filter = selectedVehicleFilter) {
            is VehicleFilter.All -> null
            is VehicleFilter.Unlinked -> "Showing unlinked rides"
            is VehicleFilter.Specific -> {
                vehiclesList.find { it.id == filter.vehicleId }?.let { "Showing ${it.make} ${it.model}" } ?: "Showing selected vehicle"
            }
        }

        RideHistoryCalendarDialog(
            initialDate = selectedDate,
            sessions = calendarSessions,
            onDismissRequest = { showCalendarDialog = false },
            onDateSelected = { dateTimestamp ->
                selectedDate = dateTimestamp
                showCalendarDialog = false
            },
            filterSubtitle = filterSubtitle
        )
    }
}

@Composable
fun RideHistoryCard(
    session: RideSession, 
    vehiclesList: List<com.nexus.portal.data.local.ridetracker.VehicleEntity>,
    onClick: () -> Unit, 
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateStr = sdf.format(Date(session.startTime))
    val distanceKm = session.totalDistanceMeters / 1000f
    
    val durationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime
    val h = durationMs / 3600000
    val m = (durationMs / 60000) % 60
    val s = (durationMs / 1000) % 60
    val durationStr = if (h > 0) String.format(java.util.Locale.getDefault(), "%d hr %02d min", h, m) else String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
    
    val fullSdf = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    val fullDateStr = "${fullSdf.format(Date(session.startTime))} - Completed Ride"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() }
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val linkedVehicle = remember(session.vehicleId, vehiclesList) {
            vehiclesList.find { it.id == session.vehicleId }
        }
        var showLinkVehicleDialog by remember { mutableStateOf(false) }

        var points by remember { mutableStateOf<List<RideLocationPoint>>(emptyList()) }
        var isMapVisible by remember { mutableStateOf(false) }

        val mapView = remember(session.id) { MapView(context) }

        DisposableEffect(mapView) {
            try {
                mapView.onCreate(android.os.Bundle())
                mapView.onStart()
                mapView.onResume()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onDispose {
                try {
                    mapView.onPause()
                    mapView.onStop()
                    mapView.onDestroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        LaunchedEffect(session.id) {
            // Stagger loading to prevent lag during rapid scrolling
            delay(500)
            val dao = RideDatabase.getDatabase(context).rideDao()
            dao.getLocationsForSession(session.id).collect { list ->
                points = list
                isMapVisible = true
            }
        }

        var showDeleteDialog by remember { mutableStateOf(false) }
        
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Ride", color = Color.White) },
                text = { Text("Are you sure you want to delete this ride? This action cannot be undone.", color = Color.LightGray) },
                containerColor = Color(0xFF1E1E1E),
                confirmButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        onDelete()
                    }) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }

        if (showLinkVehicleDialog) {
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
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 160.dp)) {
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
                                                    val updatedSession = session.copy(
                                                        vehicleId = vehicle.id,
                                                        lastUpdated = System.currentTimeMillis()
                                                     )
                                                     dao.updateSession(updatedSession)
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
                                            RideDatabase.getDatabase(context).vehicleDao().insertVehicle(newVehicle)
                                            newId
                                        }

                                        val dao = RideDatabase.getDatabase(context).rideDao()
                                        val updatedSession = session.copy(
                                            vehicleId = finalVehicleId,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        dao.updateSession(updatedSession)
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

        // Header: Map Screenshot or Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center
        ) {
            if (isMapVisible) {
                if (points.isNotEmpty()) {
                    AndroidView(
                        factory = { _ ->
                            mapView.apply {
                                getMapAsync(object : OnMapReadyCallback {
                                    override fun onMapReady(mapplsMap: MapplsMap) {
                                        try {
                                            mapplsMap.uiSettings?.setAllGesturesEnabled(false)
                                            drawRouteOnMap(context, mapplsMap, points, showKilometerMarkers = false)
                                            
                                            val latLngs = points.map { LatLng(it.latitude, it.longitude) }
                                            if (latLngs.isNotEmpty()) {
                                                val boundsBuilder = com.mappls.sdk.maps.geometry.LatLngBounds.Builder()
                                                latLngs.forEach { boundsBuilder.include(it) }
                                                mapplsMap.setPadding(0, 40, 0, 40)
                                                mapplsMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 50))
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    override fun onMapError(p0: Int, p1: String?) {}
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (session.vehicleType == "four_wheeler") "🚗" else "🏍️",
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Manual Entry (No Route)",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗺️", fontSize = 24.sp, modifier = Modifier.graphicsLayer { alpha = 0.3f })
                }
            }
            
            // Delete button at top right
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Ride", tint = Color.White)
            }
        }
        
        // Stats Section
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                
                val actualFlowRating = remember(points) {
                    if (points.isNotEmpty()) {
                        var flowScore = 100
                        val hardBrakes = points.count { it.gForce > 0.6f }
                        flowScore -= hardBrakes * 2
                        val extremeLeanCount = points.count { kotlin.math.abs(it.leanAngle) > 40f }
                        flowScore -= extremeLeanCount
                        flowScore.coerceIn(0, 100)
                    } else session.flowRating
                }
                
                if (actualFlowRating >= 0) {
                    LargePerformanceGauge(score = actualFlowRating)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val actualMaxSpeed = if (points.isNotEmpty()) points.maxOf { it.speedMetersPerSec * 3.6f } else session.maxSpeedKmh
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(icon = "📍", label = "Distance", value = String.format(Locale.getDefault(), "%.1f km", distanceKm))
                StatColumn(icon = "🕒", label = "Duration", value = durationStr)
                StatColumn(icon = "⏱", label = "Top Speed", value = String.format(Locale.getDefault(), "%.1f km/h", actualMaxSpeed))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.DarkGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            
            val deviceSuffix = if (session.deviceName.isNotBlank()) " via ${session.deviceName}" else ""
            Text(fullDateStr + deviceSuffix, color = Color.LightGray, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (session.isAutoRecorded) Color(0xFF00363A) else Color(0xFF2E2E2E),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (session.isAutoRecorded) "⚡" else "👤", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (session.isAutoRecorded) "Auto-Tracked" else "Manual",
                            color = if (session.isAutoRecorded) Color(0xFF00E5FF) else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                val isCustomVehicle = session.vehicleId?.startsWith("custom:") == true
                val customVehicleName = remember(session.vehicleId) {
                    session.vehicleId?.removePrefix("custom:")
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (linkedVehicle != null) Color(0xFF1B5E20) else if (isCustomVehicle) Color(0xFF006064) else Color(0xFFE65100),
                    modifier = Modifier
                        .wrapContentWidth()
                        .clickable { showLinkVehicleDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        val icon = if (linkedVehicle != null) {
                            when (linkedVehicle.type.lowercase()) {
                                "car" -> "🚗"
                                "scooter" -> "🛵"
                                else -> "🏍️"
                            }
                        } else if (isCustomVehicle) {
                            "🚲"
                        } else {
                            "🔗"
                        }
                        Text(icon, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = linkedVehicle?.let { 
                                if (it.make.isBlank() && it.model.isBlank()) it.name else "${it.make} ${it.model}"
                            } ?: customVehicleName ?: "Link Vehicle",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatColumn(icon: String, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LargePerformanceGauge(score: Int) {
    Box(
        modifier = Modifier.size(120.dp).padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val ratio = (score / 100f).coerceIn(0f, 1f)
        val scoreColor = when {
            ratio >= 0.8f -> Color(0xFF00E676)
            ratio >= 0.5f -> Color(0xFFFFB300)
            else -> Color(0xFFFF1744)
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val startAngle = 135f
            val sweepAngle = 270f
            
            drawArc(
                color = Color.DarkGray,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            drawArc(
                color = scoreColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * ratio,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$score", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("/100", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("Performance Score", color = Color.Gray, fontSize = 7.sp)
            Text("Flow Rating", color = scoreColor, fontSize = 8.sp)
        }
    }
}

sealed class VehicleFilter {
    object All : VehicleFilter()
    object Unlinked : VehicleFilter()
    data class Specific(val vehicleId: String) : VehicleFilter()
}

enum class SortOption(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    VEHICLE("Vehicle Name (A-Z)"),
    DISTANCE("Distance (Longest First)"),
    DURATION("Duration (Longest First)")
}

@Composable
fun VehicleFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E1E),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0xFF00E5FF) else Color.Transparent
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = if (selected) Color(0xFF00E5FF) else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RideHistoryCalendarDialog(
    initialDate: Long?,
    sessions: List<RideSession>,
    onDismissRequest: () -> Unit,
    onDateSelected: (Long) -> Unit,
    filterSubtitle: String? = null
) {
    var displayedMonthCalendar by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                timeInMillis = initialDate ?: System.currentTimeMillis()
            }
        )
    }
    
    val currentMonth = displayedMonthCalendar.get(Calendar.MONTH)
    val currentYear = displayedMonthCalendar.get(Calendar.YEAR)
    val todayCal = remember { Calendar.getInstance() }
    
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthYearString = remember(displayedMonthCalendar) { monthYearFormat.format(displayedMonthCalendar.time) }
    
    var showSliderSelectors by remember { mutableStateOf(false) }
    
    if (showSliderSelectors) {
        var tempMonth by remember { mutableIntStateOf(currentMonth) }
        var tempYear by remember { mutableIntStateOf(currentYear) }
        val monthNames = remember {
            listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        }

        AlertDialog(
            onDismissRequest = { showSliderSelectors = false },
            title = { Text("Select Month & Year", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${monthNames[tempMonth]} $tempYear",
                        color = Color(0xFF00E5FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Month: ${monthNames[tempMonth]}", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = tempMonth.toFloat(),
                        onValueChange = { tempMonth = it.roundToInt().coerceIn(0, 11) },
                        valueRange = 0f..11f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Year: $tempYear", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = tempYear.toFloat(),
                        onValueChange = { tempYear = it.roundToInt().coerceIn(2018, 2030) },
                        valueRange = 2018f..2030f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            },
            containerColor = Color(0xFF1E1E1E),
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = displayedMonthCalendar.timeInMillis
                        set(Calendar.MONTH, tempMonth)
                        set(Calendar.YEAR, tempYear)
                    }
                    displayedMonthCalendar = newCal
                    showSliderSelectors = false
                }) {
                    Text("Apply", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSliderSelectors = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Month, Year, Nav arrows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = displayedMonthCalendar.timeInMillis
                            add(Calendar.MONTH, -1)
                        }
                        displayedMonthCalendar = newCal
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month", tint = Color.White)
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSliderSelectors = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = monthYearString,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (filterSubtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = filterSubtitle,
                                color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    IconButton(onClick = {
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = displayedMonthCalendar.timeInMillis
                            add(Calendar.MONTH, 1)
                        }
                        displayedMonthCalendar = newCal
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Weekday headings
                val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekdays.forEach { day ->
                        Text(
                            text = day,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Days grid
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday...
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                
                val totalCells = (firstDayOfWeek - 1) + daysInMonth
                
                val selectedCal = initialDate?.let {
                    Calendar.getInstance().apply { timeInMillis = it }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until (totalCells + 6) / 7) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - (firstDayOfWeek - 1) + 1
                                
                                if (dayNum in 1..daysInMonth) {
                                    val isToday = todayCal.get(Calendar.YEAR) == currentYear &&
                                            todayCal.get(Calendar.MONTH) == currentMonth &&
                                            todayCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                            
                                    val isSelected = selectedCal != null &&
                                            selectedCal.get(Calendar.YEAR) == currentYear &&
                                            selectedCal.get(Calendar.MONTH) == currentMonth &&
                                            selectedCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                            
                                    val calLookup = remember { Calendar.getInstance() }
                                    val rideCount = sessions.count { s ->
                                        calLookup.timeInMillis = s.startTime
                                        calLookup.get(Calendar.YEAR) == currentYear &&
                                        calLookup.get(Calendar.MONTH) == currentMonth &&
                                        calLookup.get(Calendar.DAY_OF_MONTH) == dayNum
                                    }
                                    
                                    val cellBorder = when {
                                        isSelected -> null
                                        isToday -> BorderStroke(1.5.dp, Color(0xFF00E5FF))
                                        else -> null
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color(0xFF00E5FF)
                                                else Color.Transparent
                                            )
                                            .then(
                                                if (cellBorder != null) Modifier.border(cellBorder, RoundedCornerShape(8.dp))
                                                else Modifier
                                            )
                                            .clickable {
                                                val clickCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, currentYear)
                                                    set(Calendar.MONTH, currentMonth)
                                                    set(Calendar.DAY_OF_MONTH, dayNum)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                onDateSelected(clickCal.timeInMillis)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (rideCount > 0) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .requiredSize(16.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isSelected) Color.Black.copy(alpha = 0.15f) else Color(0xFF00E5FF).copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "$rideCount",
                                                        color = if (isSelected) Color.Black else Color(0xFF00E5FF),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 9.sp
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.height(18.dp))
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val today = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onDateSelected(today.timeInMillis)
                        }
                    ) {
                        Text("Today", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onDismissRequest
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }
}

