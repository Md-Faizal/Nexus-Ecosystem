package com.nexus.portal.ui.screens.hub.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.ui.theme.PortalTheme

@Composable
fun YouDashboardScreen(
    onExploreTimelineClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
            .verticalScroll(scrollState)
    ) {
        // ─── Header: You ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "You",
                color = PortalTheme.colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PortalTheme.colors.surfaceCard)
                    .border(1.dp, PortalTheme.colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = PortalTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                // Red dot notification badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
            }
        }

        // ─── Section: Your lists ──────────────────────────────────────────────────
        SectionHeaderWithAction(title = "Your lists", actionText = "+ New list")

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ListItemRow(icon = Icons.Default.StarBorder, tint = Color(0xFFE67C00), title = "Starred places", subtitle = "Private · 6 places")
            ListItemRow(icon = Icons.Default.Face, tint = Color(0xFF00897B), title = "My School", subtitle = "Private · 1 place")
            ListItemRow(icon = Icons.Default.DirectionsBus, tint = Color(0xFF00897B), title = "Saved trips", subtitle = "Private · 2 trips")
            ListItemRow(icon = Icons.Default.BookmarkBorder, tint = Color(0xFF00897B), title = "Labelled", subtitle = "Private · 4 places")
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = PortalTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("More", color = PortalTheme.colors.textSecondary, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Section: Recently saved ──────────────────────────────────────────────
        SectionHeader(title = "Recently saved")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(3) { index ->
                SavedPlaceCard(
                    title = "20° 15' 52.796...",
                    subtitle = "Dropped Pin",
                    listType = "Starred places"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ─── Section: Your recent places ──────────────────────────────────────────
        SectionHeader(title = "Your recent places", subtitle = "From your Maps history and saves")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipItem(icon = Icons.Default.Search, label = "Area", showDropdown = true)
            FilterChipItem(icon = null, label = "Category", showDropdown = true)
            FilterChipItem(icon = null, label = "Saved", showDropdown = true)
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            RecentPlaceRow(
                title = "PANWARx FARMHOUSE",
                subtitle = "Farm",
                time = "Searched · 19h"
            )
            RecentPlaceRow(
                title = "SunShine Nursing Home",
                subtitle = "Hospital",
                time = "Got directions · 1d"
            )
            RecentPlaceRow(
                title = "Wok n Kadhai",
                subtitle = "Restaurant",
                time = "Viewed · 2d"
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PortalTheme.colors.surfaceCard),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("See all", color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ─── Section: Visited (Timeline) ──────────────────────────────────────────
        SectionHeader(title = "Visited", subtitle = "From your Timeline")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { TimelineSummaryCard(title = "Yesterday") }
            item { TimelineSummaryCard(title = "May") }
            item { TimelineSummaryCard(title = "Places") }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onExploreTimelineClick) {
                Text(
                    text = "Explore Timeline",
                    color = Color(0xFF00897B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ─── Helper Composables ───────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = title,
            color = PortalTheme.colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = PortalTheme.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SectionHeaderWithAction(title: String, actionText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = PortalTheme.colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE0F2F1)) // Cyan-ish tint
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionText,
                color = Color(0xFF00695C),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ListItemRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = PortalTheme.colors.textPrimary, fontSize = 16.sp)
            Text(text = subtitle, color = PortalTheme.colors.textSecondary, fontSize = 14.sp)
        }
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options", tint = PortalTheme.colors.textSecondary)
    }
}

@Composable
fun SavedPlaceCard(title: String, subtitle: String, listType: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PortalTheme.colors.surfaceCard),
        modifier = Modifier.width(140.dp).height(160.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(0xFF64B5F6))
            ) // Placeholder for map/image
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = title, color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subtitle, color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.StarBorder, contentDescription = null, tint = Color(0xFFE67C00), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = listType, color = PortalTheme.colors.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, showDropdown: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PortalTheme.colors.surfaceCard)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = PortalTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, color = PortalTheme.colors.textPrimary, fontSize = 14.sp)
        if (showDropdown) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = PortalTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun RecentPlaceRow(title: String, subtitle: String, time: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PortalTheme.colors.border) // Placeholder for thumbnail
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = PortalTheme.colors.textPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, color = PortalTheme.colors.textSecondary, fontSize = 14.sp)
            Text(text = time, color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = "Save", tint = PortalTheme.colors.textSecondary, modifier = Modifier.padding(end = 12.dp))
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = PortalTheme.colors.textSecondary)
    }
}

@Composable
fun TimelineSummaryCard(title: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(130.dp).height(90.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1976D2))) { // Blue mockup
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
        }
    }
}
