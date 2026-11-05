package com.nexus.portal.ui.screens.hub.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.data.local.timeline.TimelineLocationPoint
import com.nexus.portal.ui.theme.PortalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullTimelineScreen(
    onBackClick: () -> Unit,
    onDrawRouteForDay: (List<TimelineLocationPoint>) -> Unit,
    onCenterMapOnPoints: (List<TimelineLocationPoint>) -> Unit
) {
    val tabs = listOf("Day", "Trips", "Insights", "Places", "Cities")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent, // Allow map to show through
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Timeline", color = PortalTheme.colors.textPrimary, fontSize = 20.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Trigger Sync */ }) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Sync", tint = PortalTheme.colors.textPrimary)
                        }
                        IconButton(onClick = { /* More options */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = PortalTheme.colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PortalTheme.colors.background
                    )
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = PortalTheme.colors.background,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(Color(0xFF00897B))
                        )
                    },
                    divider = {} // No bottom divider
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedTabIndex == index) Color(0xFF00897B) else PortalTheme.colors.textSecondary,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // ─── Day Tab ────────────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Transparent area to let the map shine through
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(Color.Transparent)
                        )
                        
                        // Pull-up handle style indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    PortalTheme.colors.background,
                                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PortalTheme.colors.border)
                            )
                        }

                        // The actual timeline content (Date navigator + points list)
                        YouTimelineScreen(
                            onDrawRouteForDay = onDrawRouteForDay,
                            onCenterMapOnPoints = onCenterMapOnPoints
                        )
                    }
                }
                2 -> {
                    // ─── Insights Tab (Mockup) ──────────────────────────────────
                    Box(modifier = Modifier.fillMaxSize().background(PortalTheme.colors.background)) {
                        InsightsTabContent()
                    }
                }
                else -> {
                    // ─── Placeholder for other tabs ──────────────────────────────
                    Box(
                        modifier = Modifier.fillMaxSize().background(PortalTheme.colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tabs[selectedTabIndex]} features coming soon",
                            color = PortalTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

// ─── Insights Tab Mockup ──────────────────────────────────────────────────────

@Composable
fun InsightsTabContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.ChevronLeft, tint = PortalTheme.colors.textPrimary, contentDescription = null) }
                Text("May 2026", color = PortalTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = {}) { Icon(Icons.Default.ChevronRight, tint = PortalTheme.colors.textSecondary, contentDescription = null) }
            }
        }

        item {
            Text("Travel", color = PortalTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 24.dp))
        }

        item { InsightChartRow(icon = Icons.Default.DirectionsCar, label = "Driving", distance = "27 km", time = "1 hr 24 min", barHeight = 80.dp, color = Color(0xFF00838F)) }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item { InsightChartRow(icon = Icons.Default.DirectionsBike, label = "Cycling", distance = "1 km", time = "10 min", barHeight = 60.dp, color = Color(0xFF00838F)) }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item { InsightChartRow(icon = Icons.Default.TwoWheeler, label = "Motorcycling", distance = "22 km", time = "1 hr 39 min", barHeight = 75.dp, color = Color(0xFF00838F)) }

        item { Spacer(modifier = Modifier.height(40.dp)) }
        item {
            Text("Visits", color = PortalTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 24.dp))
        }

        item { InsightVisitRow(icon = Icons.Default.Restaurant, label = "Food and drink", time = "1 hr 6 min", places = "See 2 places", barHeight = 60.dp, color = Color(0xFFFF8F00)) }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item { InsightVisitRow(icon = Icons.Default.ShoppingBag, label = "Shopping", time = "45 min", places = "See 1 place", barHeight = 40.dp, color = Color(0xFF5C6BC0)) }
    }
}

@Composable
fun InsightChartRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, distance: String, time: String, barHeight: androidx.compose.ui.unit.Dp, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = distance, color = PortalTheme.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(text = time, color = PortalTheme.colors.textSecondary, fontSize = 14.sp)
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Text("Dec", color = PortalTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.Top).padding(bottom = 8.dp))
            Box(modifier = Modifier.width(12.dp).height(4.dp).background(PortalTheme.colors.border, RoundedCornerShape(2.dp)))
            Box(modifier = Modifier.width(12.dp).height(barHeight).background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
        }
    }
}

@Composable
fun InsightVisitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, time: String, places: String, barHeight: androidx.compose.ui.unit.Dp, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = time, color = PortalTheme.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(text = places, color = Color(0xFF00897B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Text("Dec", color = PortalTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.Top).padding(bottom = 8.dp))
            Box(modifier = Modifier.width(12.dp).height(4.dp).background(PortalTheme.colors.border, RoundedCornerShape(2.dp)))
            Box(modifier = Modifier.width(12.dp).height(barHeight).background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
        }
    }
}
