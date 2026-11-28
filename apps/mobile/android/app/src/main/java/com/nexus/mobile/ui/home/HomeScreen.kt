package com.nexus.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.mobile.ui.components.EdgeFadePlacement
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusEdgeFadeContainer
import com.nexus.mobile.ui.components.NexusGlassCard
import com.nexus.mobile.ui.components.NexusModalTopBar
import com.nexus.mobile.ui.components.NexusSectionHeader
import com.nexus.mobile.ui.components.NexusStatusDot
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class HomeSubPage(val title: String, val subtitle: String) {
    BRIEF_ME("Brief Me", "Weather, your day and fleet digest (Dummy)"),
    NEWS("News", "Curated tech & world headlines (Dummy)"),
    PLANNER("Planner", "Tasks & schedule (Dummy)"),
    BUDGET("Daily Budget", "Spend & receipt tracker (Dummy)"),
    JOURNAL("Journal", "Tonight's entry & insights (Dummy)"),
}

@Composable
fun HomeScreen(
    displayName: String,
    onlineNodeCount: Int,
    totalNodeCount: Int,
    isLanMode: Boolean,
    onRefreshFleet: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    val localHaze = remember { HazeState() }
    val edgeConfig = LocalNexusEdgeFadeConfig.current.copy(placement = EdgeFadePlacement.BOTH)
    var activeSubPage by remember { mutableStateOf<HomeSubPage?>(null) }

    if (activeSubPage != null) {
        HomeDummySubPageModal(
            page = activeSubPage!!,
            onOpenCamera = onOpenCamera,
            onBack = { activeSubPage = null },
        )
        return
    }

    val now = remember { Date() }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }
    val dateLine = remember {
        SimpleDateFormat("EEEE d MMM • h:mm a", Locale.getDefault()).format(now)
    }
    val statusLine = if (totalNodeCount > 0) {
        val modeLabel = if (isLanMode) "Local LAN" else "Central Mesh"
        "All systems normal • $onlineNodeCount/$totalNodeCount nodes • $modeLabel"
    } else if (isLanMode) {
        "Local LAN active • Ready"
    } else {
        "Connecting to Nexus Fleet..."
    }

    Box(modifier = modifier.fillMaxSize()) {
        NexusEdgeFadeContainer(
            listState = listState,
            config = edgeConfig,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(localHaze),
                contentPadding = PaddingValues(top = 20.dp, bottom = 156.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(end = 96.dp),
                    ) {
                        Text(
                            text = "$greeting\n$displayName",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "$dateLine • Bengaluru",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NexusStatusDot(
                                color = if (onlineNodeCount > 0 || isLanMode) palette.onlineDot else palette.warningDot,
                            )
                            Text(
                                text = statusLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Weather Card (Dummy PA UI)
                item {
                    NexusGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "21°",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Column {
                                    Text(
                                        "Cloudy",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Feels like 25° • Dummy",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "20° – 29°",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "56% rain",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Brief Me Hero Card (Dummy PA UI)
                item {
                    NexusGlassCard(onClick = { activeSubPage = HomeSubPage.BRIEF_ME }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = palette.cardElevated,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = FzLimeAccent,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        "Brief me",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Weather, your day and the news",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // 3-Tile Quick Strip: News | Planner | Daily Budget (Dummy PA UI)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeQuickTile(
                            icon = Icons.Rounded.Newspaper,
                            label = "News",
                            onClick = { activeSubPage = HomeSubPage.NEWS },
                            modifier = Modifier.weight(1f),
                        )
                        HomeQuickTile(
                            icon = Icons.Rounded.Checklist,
                            label = "Planner",
                            onClick = { activeSubPage = HomeSubPage.PLANNER },
                            modifier = Modifier.weight(1f),
                        )
                        HomeQuickTile(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            label = "Daily budget",
                            onClick = { activeSubPage = HomeSubPage.BUDGET },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Your Day Agenda (Dummy PA UI)
                item {
                    NexusSectionHeader(title = "Your Day")
                    Spacer(Modifier.height(8.dp))
                    NexusGlassCard(onClick = { activeSubPage = HomeSubPage.PLANNER }) {
                        Text(
                            "Nothing planned today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = palette.hairlineBorder,
                        )
                        Text(
                            "TOMORROW • NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "2–3 PM",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Nexus Fleet Sync & Review",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Journal Preview Card (Dummy PA UI)
                item {
                    NexusGlassCard(onClick = { activeSubPage = HomeSubPage.JOURNAL }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Rounded.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column {
                                    Text(
                                        "Tonight's entry",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Tap to jot down thoughts • Dummy",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // World Clock Card (Live local time comparison)
                item {
                    NexusSectionHeader(title = "World Clock", trailing = "Compared with India")
                    Spacer(Modifier.height(8.dp))
                    NexusGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            WorldClockItem("San Francisco", "America/Los_Angeles")
                            WorldClockItem("London", "Europe/London")
                            WorldClockItem("Tokyo", "Asia/Tokyo")
                        }
                    }
                }
            }
        }

        // Top-right circular glass controls: Camera + Refresh
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 12.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NexusCircularButton(
                icon = Icons.Rounded.CameraAlt,
                contentDescription = "Open Multipurpose Camera",
                onClick = onOpenCamera,
                hazeState = localHaze,
            )
            NexusCircularButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh Fleet Status",
                onClick = onRefreshFleet,
                hazeState = localHaze,
            )
        }
    }
}

@Composable
private fun HomeQuickTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = palette.cardSurface,
        border = nexusSpecularBorder(palette.isDark, 0.75f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.clip(shape),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WorldClockItem(city: String, zoneId: String) {
    val formatted = remember(zoneId) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone(zoneId)
        sdf.format(Date())
    }
    Column {
        Text(
            city,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeDummySubPageModal(
    page: HomeSubPage,
    onOpenCamera: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalNexusPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NexusModalTopBar(
            title = page.title,
            subtitle = page.subtitle,
            onBack = onBack,
            trailingContent = if (page == HomeSubPage.BUDGET) {
                {
                    NexusCircularButton(
                        icon = Icons.Rounded.CameraAlt,
                        contentDescription = "Scan Receipt",
                        onClick = onOpenCamera,
                    )
                }
            } else null,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                NexusGlassCard {
                    Text(
                        text = "Placeholder Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when (page) {
                            HomeSubPage.BRIEF_ME -> "Morning & evening AI briefing (weather, agenda, overnight Night Log, and fleet summary) will render here once PA backend endpoints are connected."
                            HomeSubPage.NEWS -> "Curated AI, systems, and world headlines will appear here."
                            HomeSubPage.PLANNER -> "Tasks, reminders, and calendar blocks will sync here."
                            HomeSubPage.BUDGET -> "Daily spend & receipt scanning (tap the top-right Camera button to test Receipt mode)."
                            HomeSubPage.JOURNAL -> "Tonight's entry editor, voice dictation, and Deep Insights history."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
