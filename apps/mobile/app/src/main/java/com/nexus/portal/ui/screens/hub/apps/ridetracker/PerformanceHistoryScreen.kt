package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceHistoryScreen(
    onBackClick: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { RideDatabase.getDatabase(context) }
    
    var runsList by remember { mutableStateOf<List<PerformanceRunEntity>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("All") }
    var runToDelete by remember { mutableStateOf<PerformanceRunEntity?>(null) }

    // Collect runs from local DB
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            db.performanceRunDao().getAllRuns().collect { runs ->
                runsList = runs
            }
        }
    }

    val tabs = listOf("All", "Accel", "Drag", "Braking", "Laps")

    // Helper to group category
    fun getRunCategory(run: PerformanceRunEntity): String {
        return when {
            run.lapTimesJson != "[]" && run.lapTimesJson.isNotEmpty() -> "Laps"
            run.brakingTime != null || run.brakingDistanceMeters != null -> "Braking"
            run.drag1_4Time != null || run.drag1_8Time != null || run.drag1_2Time != null -> "Drag"
            else -> "Accel"
        }
    }

    val filteredRuns = remember(runsList, selectedTab) {
        if (selectedTab == "All") {
            runsList
        } else {
            runsList.filter { getRunCategory(it) == selectedTab }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Runs", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(horizontal = 16.dp)
        ) {
            // Tab Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
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
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (filteredRuns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recorded performance runs found.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredRuns, key = { it.id }) { run ->
                        PerformanceRunCard(
                            run = run,
                            category = getRunCategory(run),
                            onCardClick = { onNavigateToDetail(run.id) },
                            onDeleteClick = { runToDelete = run }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (runToDelete != null) {
        val run = runToDelete!!
        AlertDialog(
            onDismissRequest = { runToDelete = null },
            title = { Text("Delete Run", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this run? This will be updated on the cloud during the next sync.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                db.performanceRunDao().deleteRunById(run.id)
                            }
                            Toast.makeText(context, "Run marked as deleted.", Toast.LENGTH_SHORT).show()
                            com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
                            runToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF1744), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { runToDelete = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }
}

@Composable
fun PerformanceRunCard(
    run: PerformanceRunEntity,
    category: String,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(run.timestamp))
    
    val accentColor = when(category) {
        "Accel" -> Color(0xFF00E5FF)
        "Drag" -> Color(0xFFFFAB00)
        "Braking" -> Color(0xFFFF1744)
        "Laps" -> Color(0xFF00E676)
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF141418), accentColor.copy(alpha = 0.15f))))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable { onCardClick() }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Header (Vehicle & Date)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = if (run.vehicleType == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike
                    Icon(icon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(run.vehicleName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(dateStr, color = Color.Gray, fontSize = 11.sp)
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Mode metrics display
                when (category) {
                    "Accel" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricLabelValue("0-100 km/h", run.accel0To100Kmh?.let { String.format("%.2f s", it) } ?: "--")
                            MetricLabelValue("0-60 mph", run.accel0To60Mph?.let { String.format("%.2f s", it) } ?: "--")
                        }
                    }
                    "Drag" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricLabelValue("1/4 Mile", run.drag1_4Time?.let { String.format("%.2f s", it) } ?: "--")
                            MetricLabelValue("1/8 Mile", run.drag1_8Time?.let { String.format("%.2f s", it) } ?: "--")
                        }
                    }
                    "Braking" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricLabelValue("Distance", run.brakingDistanceMeters?.let { String.format("%.1f m", it) } ?: "--")
                            MetricLabelValue("Duration", run.brakingTime?.let { String.format("%.2f s", it) } ?: "--")
                        }
                    }
                    "Laps" -> {
                        // Extract best lap time from json
                        val laps = remember(run.lapTimesJson) {
                            try {
                                val arr = JSONArray(run.lapTimesJson)
                                val list = mutableListOf<Long>()
                                for (i in 0 until arr.length()) {
                                    list.add(arr.getLong(i))
                                }
                                list
                            } catch (e: Exception) {
                                emptyList<Long>()
                            }
                        }
                        val bestLap = laps.minOrNull()
                        val bestLapStr = if (bestLap != null) {
                            val min = (bestLap / 60000) % 60
                            val sec = (bestLap / 1000) % 60
                            val ms = (bestLap % 1000) / 10
                            String.format("%02d:%02d.%02d", min, sec, ms)
                        } else {
                            "--"
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricLabelValue("Best Lap", bestLapStr)
                            MetricLabelValue("Total Laps", laps.size.toString())
                        }
                    }
                }
            }
            
            // Sync & Delete block
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(80.dp)
            ) {
                // Sync status indicator
                if (run.isSynced) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Not Synced",
                        tint = Color(0xFFFFAB00),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF1744).copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun MetricLabelValue(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}
