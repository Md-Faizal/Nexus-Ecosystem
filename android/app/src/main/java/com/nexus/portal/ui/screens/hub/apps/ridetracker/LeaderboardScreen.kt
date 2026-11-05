package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.core.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBackClick: () -> Unit, onRideClick: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Stats, 1: Recent Trips
    var isLoading by remember { mutableStateOf(true) }
    var leaders by remember { mutableStateOf<List<LeaderData>>(emptyList()) }
    var recentTrips by remember { mutableStateOf<List<RecentTripData>>(emptyList()) }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL(NetworkConfig.leaderboardUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(raw)
                    if (json.optBoolean("success", false)) {
                        val leadArr = json.getJSONArray("leaderboard")
                        val leadList = mutableListOf<LeaderData>()
                        for (i in 0 until leadArr.length()) {
                            val obj = leadArr.getJSONObject(i)
                            leadList.add(
                                LeaderData(
                                    profileId = obj.getString("profileId"),
                                    profileName = obj.optString("profileName", "Unknown User"),
                                    avatarInitial = obj.optString("avatarInitial", "U"),
                                    themeColorHex = obj.optString("themeColorHex", "#00E5FF"),
                                    totalRides = obj.optInt("totalRides", 0),
                                    totalDistanceMeters = obj.optDouble("totalDistanceMeters", 0.0),
                                    maxSpeedKmh = obj.optDouble("maxSpeedKmh", 0.0),
                                    maxLeanAngle = obj.optDouble("maxLeanAngle", 0.0)
                                )
                            )
                        }
                        
                        val tripArr = json.getJSONArray("recentRides")
                        val tripList = mutableListOf<RecentTripData>()
                        for (i in 0 until tripArr.length()) {
                            val obj = tripArr.getJSONObject(i)
                            tripList.add(
                                RecentTripData(
                                    id = obj.getString("id"),
                                    profileId = obj.getString("profileId"),
                                    profileName = obj.optString("profileName", "Unknown User"),
                                    avatarInitial = obj.optString("avatarInitial", "U"),
                                    themeColorHex = obj.optString("themeColorHex", "#00E5FF"),
                                    startTime = obj.optLong("startTime"),
                                    endTime = if (obj.isNull("endTime")) null else obj.optLong("endTime"),
                                    totalDistanceMeters = obj.optDouble("totalDistanceMeters", 0.0),
                                    maxSpeedKmh = obj.optDouble("maxSpeedKmh", 0.0),
                                    averageSpeedKmh = obj.optDouble("averageSpeedKmh", 0.0),
                                    maxLeanAngle = obj.optDouble("maxLeanAngle", 0.0),
                                    vehicleType = obj.optString("vehicleType", "two_wheeler")
                                )
                            )
                        }
                        
                        withContext(Dispatchers.Main) {
                            leaders = leadList
                            recentTrips = tripList
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load leaderboard. Check connection.", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        TopAppBar(
            title = { Text("Nexus Leaderboard", color = Color.White, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF050505))
        )

        // Custom Premium Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141414))
                .padding(4.dp)
        ) {
            val tabs = listOf("Stats Leaders", "Recent Public Trips")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
        } else {
            if (selectedTab == 0) {
                if (leaders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No rider stats found on Nexus.", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(leaders, key = { _, leader -> leader.profileId }) { index, leader ->
                            LeaderCard(rank = index + 1, leader = leader)
                        }
                    }
                }
            } else {
                if (recentTrips.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recent public trips found.", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(recentTrips, key = { it.id }) { trip ->
                            RecentTripCard(trip = trip, onClick = { onRideClick(trip.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderCard(rank: Int, leader: LeaderData) {
    val distanceKm = leader.totalDistanceMeters / 1000.0
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(leader.themeColorHex))
    } catch (e: Exception) {
        Color(0xFF00E5FF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank Badge
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            when (rank) {
                1 -> Icon(Icons.Default.EmojiEvents, contentDescription = "Gold", tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                2 -> Icon(Icons.Default.EmojiEvents, contentDescription = "Silver", tint = Color(0xFFC0C0C0), modifier = Modifier.size(28.dp))
                3 -> Icon(Icons.Default.EmojiEvents, contentDescription = "Bronze", tint = Color(0xFFCD7F32), modifier = Modifier.size(28.dp))
                else -> Text("$rank", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Avatar Initial
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(parsedColor.copy(alpha = 0.2f))
                .border(2.dp, parsedColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = leader.avatarInitial.uppercase(),
                color = parsedColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Profile Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leader.profileName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${leader.totalRides} Rides", color = Color.Gray, fontSize = 11.sp)
                Text("|", color = Color(0xFF333333), fontSize = 11.sp)
                Text(String.format(Locale.getDefault(), "Max: %.1f km/h", leader.maxSpeedKmh), color = Color.Gray, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Distance Ridden (primary stat)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format(Locale.getDefault(), "%.1f", distanceKm),
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
            Text(
                text = "total km",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun RecentTripCard(trip: RecentTripData, onClick: () -> Unit) {
    val distanceKm = trip.totalDistanceMeters / 1000.0
    val durationMs = (trip.endTime ?: System.currentTimeMillis()) - trip.startTime
    val m = (durationMs / 60000) % 60
    val h = durationMs / 3600000
    val durationStr = if (h > 0) String.format(Locale.getDefault(), "%d hr %02d min", h, m) else String.format(Locale.getDefault(), "%02d min", m)
    
    val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(trip.startTime))

    val parsedColor = try {
        Color(android.graphics.Color.parseColor(trip.themeColorHex))
    } catch (e: Exception) {
        Color(0xFF00E5FF)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rider Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parsedColor.copy(alpha = 0.2f))
                    .border(1.5.dp, parsedColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trip.avatarInitial.uppercase(),
                    color = parsedColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Rider & Vehicle details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.profileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = dateStr,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            // Vehicle Type Icon
            Icon(
                imageVector = if (trip.vehicleType.lowercase() == "car") Icons.Filled.DirectionsCar else Icons.Filled.DirectionsBike,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Divider(color = Color(0xFF222222), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Trip Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Distance", color = Color.Gray, fontSize = 11.sp)
                Text(String.format(Locale.getDefault(), "%.2f km", distanceKm), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column {
                Text("Duration", color = Color.Gray, fontSize = 11.sp)
                Text(durationStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column {
                Text("Top Speed", color = Color.Gray, fontSize = 11.sp)
                Text(String.format(Locale.getDefault(), "%.1f km/h", trip.maxSpeedKmh), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

data class LeaderData(
    val profileId: String,
    val profileName: String,
    val avatarInitial: String,
    val themeColorHex: String,
    val totalRides: Int,
    val totalDistanceMeters: Double,
    val maxSpeedKmh: Double,
    val maxLeanAngle: Double
)

data class RecentTripData(
    val id: String,
    val profileId: String,
    val profileName: String,
    val avatarInitial: String,
    val themeColorHex: String,
    val startTime: Long,
    val endTime: Long?,
    val totalDistanceMeters: Double,
    val maxSpeedKmh: Double,
    val averageSpeedKmh: Double,
    val maxLeanAngle: Double,
    val vehicleType: String
)
