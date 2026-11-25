package com.nexus.portal.ui.screens.hub.apps.ridetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSettingsScreen(
    onBackClick: () -> Unit,
    settingsManager: com.nexus.portal.managers.RideSettingsManager = com.nexus.portal.managers.RideSettingsManager.getInstance(androidx.compose.ui.platform.LocalContext.current)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isMetric by settingsManager.isMetric.collectAsState()
    val recordGForce by settingsManager.recordGForce.collectAsState()
    val recordLean by settingsManager.recordLean.collectAsState()
    val autoCenterMap by settingsManager.autoCenterMap.collectAsState()
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var syncErrorToShow by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        TopAppBar(
            title = { Text("Settings", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF050505))
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsGroup(title = "Display & Units") {
                    SettingsSwitchRow("Use Metric Units (km/h)", isMetric) { settingsManager.setMetric(it) }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                    SettingsSwitchRow("Auto-Center Map", autoCenterMap) { settingsManager.setAutoCenterMap(it) }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                    val showKilometerMarkers by settingsManager.showKilometerMarkers.collectAsState()
                    SettingsSwitchRow("Show Kilometer Markers", showKilometerMarkers) { settingsManager.setShowKilometerMarkers(it) }
                }
            }

            item {
                SettingsGroup(title = "Telemetry Sensors") {
                    SettingsSwitchRow("Record G-Force", recordGForce) { settingsManager.setRecordGForce(it) }
                    if (recordGForce) {
                        Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                        val gForceSensitivity by settingsManager.gForceSensitivity.collectAsState()
                        SettingsSliderRow(
                            label = "G-Force Circle Sensitivity",
                            value = gForceSensitivity,
                            valueRange = 0.1f..2.0f,
                            onValueChange = { settingsManager.setGForceSensitivity(it) },
                            valueDisplay = { String.format("%.1fx", it) }
                        )
                    }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                    SettingsSwitchRow("Record Lean Angle", recordLean) { settingsManager.setRecordLean(it) }
                }
            }
            
            item {
                SettingsGroup(title = "Privacy & Community") {
                    val participateInLeaderboard by settingsManager.participateInLeaderboard.collectAsState()
                    SettingsSwitchRow("Participate in Leaderboard", participateInLeaderboard) {
                        settingsManager.setParticipateInLeaderboard(it)
                    }
                    if (participateInLeaderboard) {
                        Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                        val defaultRideVisibilityPublic by settingsManager.defaultRideVisibilityPublic.collectAsState()
                        SettingsSwitchRow("Make New Rides Public by Default", defaultRideVisibilityPublic) {
                            settingsManager.setDefaultRideVisibilityPublic(it)
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "Data Management") {
                    val enableAutoSync by settingsManager.enableAutoSync.collectAsState()
                    SettingsSwitchRow("Enable Background Auto-Sync", enableAutoSync) {
                        settingsManager.setEnableAutoSync(it)
                        com.nexus.portal.managers.RideTrackerSyncManager.schedulePeriodicSync(context, it)
                    }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                    
                    if (isSyncing) {
                        val progress by com.nexus.portal.managers.RideTrackerSyncManager.syncProgress.collectAsState()
                        val status by com.nexus.portal.managers.RideTrackerSyncManager.syncStatus.collectAsState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(status, color = Color.Gray, fontSize = 14.sp)
                                Text("${(progress * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF00E5FF),
                                trackColor = Color(0xFF222222)
                            )
                        }
                    } else {
                        TextButton(onClick = {
                            isSyncing = true
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val (success, error) = com.nexus.portal.managers.RideTrackerSyncManager.performFullSync(context)
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    isSyncing = false
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Sync successful!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        syncErrorToShow = error
                                    }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text("Sync Data to Cloud", color = Color(0xFF00E5FF), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                    TextButton(onClick = { /* Export */ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("Export All Rides to CSV", color = Color(0xFF00E5FF), modifier = Modifier.fillMaxWidth())
                    }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(start = 16.dp))
                    TextButton(onClick = { /* Clear DB */ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("Clear All Ride History", color = Color.Red, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (syncErrorToShow != null) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { syncErrorToShow = null },
            title = { Text("Sync Error", color = Color.White) },
            text = { Text(syncErrorToShow!!, color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(syncErrorToShow!!))
                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    syncErrorToShow = null
                }) {
                    Text("Copy Log", color = Color(0xFF00E5FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { syncErrorToShow = null }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF151515)
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF151515))
        ) {
            content()
        }
    }
}

@Composable
fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00E5FF)
            )
        )
    }
}

@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 16.sp)
            Text(
                text = valueDisplay(value),
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF00E5FF),
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
