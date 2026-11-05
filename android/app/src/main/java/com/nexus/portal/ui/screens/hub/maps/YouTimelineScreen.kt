package com.nexus.portal.ui.screens.hub.maps

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexus.portal.managers.TimelineSyncManager
import com.nexus.portal.data.local.timeline.TimelineDatabase
import com.nexus.portal.data.local.timeline.TimelineLocationPoint
import com.nexus.portal.services.LocationTimelineService
import com.nexus.portal.ui.theme.PortalTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * YouTimelineScreen — the full Google Maps–style location timeline panel.
 *
 * Shows a day-by-day timeline of places the user visited, with route segments
 * showing distance traveled between stops. Integrates with LocationTimelineService
 * for live background tracking and TimelineSyncManager for cloud backup.
 */
@Composable
fun YouTimelineScreen(
    onDrawRouteForDay: (List<TimelineLocationPoint>) -> Unit,
    onCenterMapOnPoints: (List<TimelineLocationPoint>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val primaryColor = PortalTheme.colors.primary

    // Tracking service state
    val isServiceRunning by LocationTimelineService.isRunning.collectAsState()

    // Selected day state — defaults to today
    var selectedDayMs by remember {
        mutableStateOf(getDayStartMs(System.currentTimeMillis()))
    }

    // Database-driven timeline points for the selected day
    val db = remember { TimelineDatabase.getDatabase(context) }
    val dayPoints by db.timelineDao()
        .getPointsForDay(selectedDayMs, selectedDayMs + 86_400_000L)
        .collectAsState(initial = emptyList())

    // All distinct days that have recorded data
    val allDays by db.timelineDao()
        .getDistinctDayTimestamps()
        .collectAsState(initial = emptyList())

    // Sync state
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncResult by remember { mutableStateOf<String?>(null) }

    // Background permission state
    var hasBgPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Auto-draw route on the map whenever day points change
    LaunchedEffect(dayPoints) {
        if (dayPoints.isNotEmpty()) {
            onDrawRouteForDay(dayPoints)
            onCenterMapOnPoints(dayPoints)
        }
    }

    // Format helpers
    val dayLabelFmt = remember { SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()) }
    val timeFmt     = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalTheme.colors.background)
    ) {



        // ─── Day Navigator ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous day
                IconButton(
                    onClick = { selectedDayMs -= 86_400_000L },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PortalTheme.colors.surfaceCard)
                        .border(1.dp, PortalTheme.colors.border, CircleShape)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day",
                        tint = PortalTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dayLabelFmt.format(Date(selectedDayMs)),
                        color = PortalTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (getDayStartMs(System.currentTimeMillis()) == selectedDayMs) {
                        Text(
                            text = "TODAY",
                            color = primaryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                Row {
                    // Today button
                    if (getDayStartMs(System.currentTimeMillis()) != selectedDayMs) {
                        TextButton(
                            onClick = { selectedDayMs = getDayStartMs(System.currentTimeMillis()) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Today",
                                color = primaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Next day (only if there's a future day with data, or within the past)
                    val canGoForward = selectedDayMs < getDayStartMs(System.currentTimeMillis())
                    IconButton(
                        onClick = { if (canGoForward) selectedDayMs += 86_400_000L },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (canGoForward) PortalTheme.colors.surfaceCard else PortalTheme.colors.surfaceCard.copy(alpha = 0.4f))
                            .border(1.dp, PortalTheme.colors.border.copy(alpha = if (canGoForward) 1f else 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Day",
                            tint = if (canGoForward) PortalTheme.colors.textPrimary else PortalTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }



        // ─── Timeline Content ─────────────────────────────────────────────
        if (dayPoints.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = primaryColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isServiceRunning) "No data for this day yet" else "Enable timeline tracking to record your daily journey",
                        color = PortalTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    if (!isServiceRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use the toggle above to start",
                            color = primaryColor.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Build grouped timeline entries (merge nearby/significant points into "stops")
            val timelineEntries = remember(dayPoints) { buildTimelineEntries(dayPoints) }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(timelineEntries, key = { it.id }) { entry ->
                    when (entry) {
                        is TimelineEntry.PlaceStop -> PlaceStopNode(entry, timeFmt, primaryColor)
                        is TimelineEntry.RouteSegment -> RouteSegmentNode(entry, primaryColor)
                    }
                }
            }
        }
    }
}

// ─── Timeline Entry Models ────────────────────────────────────────────────────

sealed class TimelineEntry {
    abstract val id: String

    data class PlaceStop(
        override val id: String,
        val label: String,
        val subLabel: String,
        val icon: String,
        val arrivalMs: Long,
        val departureMs: Long?,
        val isSignificant: Boolean,
        val lat: Double,
        val lon: Double
    ) : TimelineEntry()

    data class RouteSegment(
        override val id: String,
        val distanceMeters: Float,
        val durationMs: Long,
        val pointCount: Int
    ) : TimelineEntry()
}

/**
 * Groups raw GPS points into a human-readable timeline:
 * - Significant places (stayed ≥5 min in 50m radius) become PlaceStop nodes.
 * - Movement between stops becomes RouteSegment nodes.
 * - Points without geocoding use coordinate-derived fallback labels.
 */
fun buildTimelineEntries(points: List<TimelineLocationPoint>): List<TimelineEntry> {
    if (points.isEmpty()) return emptyList()

    val entries = mutableListOf<TimelineEntry>()
    var i = 0

    while (i < points.size) {
        val point = points[i]

        if (point.isSignificantPlace || point.placeLabel.isNotBlank()) {
            // Find end of this "stop" — consecutive points within ~100m
            var j = i + 1
            while (j < points.size) {
                val dist = distanceBetween(
                    point.latitude, point.longitude,
                    points[j].latitude, points[j].longitude
                )
                if (dist > 150f) break
                j++
            }

            val departureMs = if (j < points.size) points[j].timestamp else null
            entries.add(
                TimelineEntry.PlaceStop(
                    id = point.id,
                    label = point.placeLabel.ifBlank { formatCoord(point.latitude, point.longitude) },
                    subLabel = point.placeSubLabel,
                    icon = point.placeCategoryIcon,
                    arrivalMs = point.timestamp,
                    departureMs = departureMs,
                    isSignificant = point.isSignificantPlace,
                    lat = point.latitude,
                    lon = point.longitude
                )
            )

            // Add route segment to next stop if there are more points
            if (j < points.size) {
                val segPoints = points.subList(j, minOf(j + 20, points.size))
                val dist = calculateRouteDistance(points.subList(i, j + 1))
                val dur = (points.getOrNull(j)?.timestamp ?: point.timestamp) - point.timestamp
                entries.add(
                    TimelineEntry.RouteSegment(
                        id = "seg_${point.id}",
                        distanceMeters = dist,
                        durationMs = dur,
                        pointCount = j - i
                    )
                )
            }
            i = j
        } else {
            i++
        }
    }

    // If no significant places found, create a single movement entry for the whole day
    if (entries.isEmpty()) {
        val totalDist = calculateRouteDistance(points)
        val dur = points.last().timestamp - points.first().timestamp
        entries.add(
            TimelineEntry.RouteSegment(
                id = "day_route",
                distanceMeters = totalDist,
                durationMs = dur,
                pointCount = points.size
            )
        )
        // Add start and end as anonymous stops
        entries.add(0, TimelineEntry.PlaceStop(
            id = points.first().id,
            label = points.first().placeLabel.ifBlank { "Start" },
            subLabel = "",
            icon = "trip_origin",
            arrivalMs = points.first().timestamp,
            departureMs = points.last().timestamp,
            isSignificant = false,
            lat = points.first().latitude,
            lon = points.first().longitude
        ))
        entries.add(TimelineEntry.PlaceStop(
            id = "end_${points.last().id}",
            label = points.last().placeLabel.ifBlank { "End" },
            subLabel = "",
            icon = "flag",
            arrivalMs = points.last().timestamp,
            departureMs = null,
            isSignificant = false,
            lat = points.last().latitude,
            lon = points.last().longitude
        ))
    }

    return entries
}

fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}

fun calculateRouteDistance(points: List<TimelineLocationPoint>): Float {
    var total = 0f
    for (i in 1 until points.size) {
        total += distanceBetween(
            points[i - 1].latitude, points[i - 1].longitude,
            points[i].latitude, points[i].longitude
        )
    }
    return total
}

fun formatCoord(lat: Double, lon: Double): String =
    "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"

fun getDayStartMs(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ─── Timeline Node Composables ────────────────────────────────────────────────

@Composable
fun PlaceStopNode(
    entry: TimelineEntry.PlaceStop,
    timeFmt: SimpleDateFormat,
    primaryColor: Color
) {
    val accentColor = if (entry.isSignificant) primaryColor else PortalTheme.colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Timeline spine
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (entry.isSignificant) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.isSignificant) primaryColor
                        else PortalTheme.colors.border
                    )
                    .border(
                        2.dp,
                        if (entry.isSignificant) primaryColor.copy(alpha = 0.3f) else Color.Transparent,
                        CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Place content card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(PortalTheme.colors.surfaceCard)
                .border(
                    1.dp,
                    if (entry.isSignificant) primaryColor.copy(alpha = 0.3f) else PortalTheme.colors.border,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Place icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getPlaceIcon(entry.icon),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        color = PortalTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.subLabel.isNotBlank()) {
                        Text(
                            text = entry.subLabel,
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time info
                    val arrivalStr = timeFmt.format(Date(entry.arrivalMs))
                    val stayStr = entry.departureMs?.let { dep ->
                        val dur = dep - entry.arrivalMs
                        val mins = (dur / 60000).toInt()
                        val hrs = mins / 60
                        if (hrs > 0) "${hrs}h ${mins % 60}m" else "${mins}m"
                    }

                    Row {
                        Text(
                            text = arrivalStr,
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        stayStr?.let {
                            Text(
                                text = "  ·  $it",
                                color = PortalTheme.colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteSegmentNode(entry: TimelineEntry.RouteSegment, primaryColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline spine connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PortalTheme.colors.border,
                                primaryColor.copy(alpha = 0.4f),
                                PortalTheme.colors.border
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Route stats
        val distKm = entry.distanceMeters / 1000f
        val distStr = if (distKm >= 1f) "${String.format("%.1f", distKm)} km" else "${entry.distanceMeters.toInt()} m"
        val durMin = (entry.durationMs / 60000).toInt()
        val durStr = when {
            durMin >= 60 -> "${durMin / 60}h ${durMin % 60}m"
            durMin > 0 -> "${durMin}m"
            else -> ""
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = primaryColor.copy(alpha = 0.5f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = buildString {
                    append(distStr)
                    if (durStr.isNotEmpty()) append("  ·  $durStr")
                },
                color = PortalTheme.colors.textSecondary,
                fontSize = 10.sp
            )
        }
    }
}

// ─── Helper: Map icon string → MaterialIcon ───────────────────────────────────
@Composable
fun getPlaceIcon(iconName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconName) {
        "home"               -> Icons.Default.Home
        "work"               -> Icons.Default.Work
        "local_cafe"         -> Icons.Default.LocalCafe
        "restaurant"         -> Icons.Default.Restaurant
        "local_hospital"     -> Icons.Default.LocalHospital
        "school"             -> Icons.Default.School
        "shopping_cart"      -> Icons.Default.ShoppingCart
        "fitness_center"     -> Icons.Default.FitnessCenter
        "park"               -> Icons.Default.Park
        "flight"             -> Icons.Default.Flight
        "directions_transit" -> Icons.Default.DirectionsTransit
        "hotel"              -> Icons.Default.Hotel
        "account_balance"    -> Icons.Default.AccountBalance
        "trip_origin"        -> Icons.Default.TripOrigin
        "flag"               -> Icons.Default.Flag
        else                 -> Icons.Default.LocationOn
    }
}
