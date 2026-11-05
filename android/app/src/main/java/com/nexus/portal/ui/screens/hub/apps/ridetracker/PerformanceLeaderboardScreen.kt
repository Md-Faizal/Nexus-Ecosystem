package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Verified
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
import com.nexus.portal.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.nexus.portal.data.local.ridetracker.RideDatabase
import kotlinx.coroutines.flow.first

data class PerformanceLeaderboardEntry(
    val rank: Int,
    val name: String,
    val vehicle: String,
    val scoreText: String,
    val scoreValue: Float,
    val isUser: Boolean = false,
    val isVerified: Boolean = true,
    val avatarInitial: String = "U",
    val colorHex: String = "#00E5FF"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceLeaderboardScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(0) } // 0: 0-100 km/h, 1: 1/4 Mile, 2: Braking, 3: Laps
    
    var entriesList by remember { mutableStateOf<List<PerformanceLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedCategory) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                fun getBestLapMs(json: String): Float? {
                    if (json == "[]" || json.isEmpty()) return null
                    return try {
                        val arr = org.json.JSONArray(json)
                        var best: Long? = null
                        for (i in 0 until arr.length()) {
                            val lap = arr.getLong(i)
                            if (best == null || lap < best) best = lap
                        }
                        best?.toFloat()
                    } catch (e: Exception) {
                        null
                    }
                }

                val db = RideDatabase.getDatabase(context)
                val runs = db.performanceRunDao().getAllRuns().first()
                
                val filtered = runs.filter { run ->
                    when (selectedCategory) {
                        0 -> run.accel0To100Kmh != null && run.accel0To100Kmh > 0
                        1 -> run.drag1_4Time != null && run.drag1_4Time > 0
                        2 -> run.brakingDistanceMeters != null && run.brakingDistanceMeters > 0
                        else -> getBestLapMs(run.lapTimesJson) != null && getBestLapMs(run.lapTimesJson)!! > 0
                    }
                }
                
                val sorted = filtered.sortedBy { run ->
                    when (selectedCategory) {
                        0 -> run.accel0To100Kmh
                        1 -> run.drag1_4Time
                        2 -> run.brakingDistanceMeters
                        else -> getBestLapMs(run.lapTimesJson) ?: Float.MAX_VALUE
                    }
                }
                
                val list = sorted.mapIndexed { index, run ->
                    val scoreVal = when (selectedCategory) {
                        0 -> run.accel0To100Kmh ?: 0f
                        1 -> run.drag1_4Time ?: 0f
                        2 -> run.brakingDistanceMeters ?: 0f
                        else -> getBestLapMs(run.lapTimesJson) ?: 0f
                    }
                    val scoreText = when (selectedCategory) {
                        0, 1 -> String.format("%.2f s", scoreVal)
                        2 -> String.format("%.1f m", scoreVal)
                        else -> {
                            val lapMs = scoreVal.toLong()
                            val min = (lapMs / 60000) % 60
                            val sec = (lapMs / 1000) % 60
                            val ms = (lapMs % 1000) / 10
                            String.format("%02d:%02d.%02d", min, sec, ms)
                        }
                    }
                    
                    PerformanceLeaderboardEntry(
                        rank = index + 1,
                        name = "You",
                        vehicle = run.vehicleName,
                        scoreText = scoreText,
                        scoreValue = scoreVal,
                        isUser = true,
                        isVerified = true,
                        avatarInitial = "U",
                        colorHex = "#00E5FF"
                    )
                }
                
                withContext(Dispatchers.Main) {
                    entriesList = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Bests", color = Color.White, fontWeight = FontWeight.Bold) },
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
        ) {
            // Verified header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141418))
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Local Device: Your verified GPS runs.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tabs for Categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141418))
                    .padding(4.dp)
            ) {
                val tabs = listOf("0-100", "1/4 Mile", "Braking", "Laps")
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedCategory == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF1E1E24) else Color.Transparent)
                            .clickable { selectedCategory = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                if (entriesList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No runs recorded in this category yet.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(entriesList) { _, entry ->
                            LeaderboardCard(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardCard(entry: PerformanceLeaderboardEntry) {
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(entry.colorHex))
    } catch (e: Exception) {
        Color(0xFF00E5FF)
    }
    
    val cardBackground = if (entry.isUser) Color(0xFF1F292E) else Color(0xFF141418)
    val cardBorderColor = if (entry.isUser) Color(0xFF00E5FF) else Color(0xFF222228)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank Badge
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            when (entry.rank) {
                1 -> Icon(Icons.Default.EmojiEvents, contentDescription = "Gold", tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                2 -> Icon(Icons.Default.EmojiEvents, contentDescription = "Silver", tint = Color(0xFFC0C0C0), modifier = Modifier.size(24.dp))
                3 -> Icon(Icons.Default.EmojiEvents, contentDescription = "Bronze", tint = Color(0xFFCD7F32), modifier = Modifier.size(24.dp))
                else -> Text("${entry.rank}", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Avatar Initial
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(parsedColor.copy(alpha = 0.2f))
                .border(1.5.dp, parsedColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.avatarInitial.uppercase(),
                color = parsedColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name & Vehicle details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.vehicle,
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Performance Score
        Text(
            text = entry.scoreText,
            color = if (entry.isUser) Color(0xFF00E676) else Color(0xFF00E5FF),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )
    }
}
