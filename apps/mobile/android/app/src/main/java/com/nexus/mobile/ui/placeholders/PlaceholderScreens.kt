package com.nexus.mobile.ui.placeholders

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.mobile.ui.components.EdgeFadePlacement
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusEdgeFadeContainer
import com.nexus.mobile.ui.theme.LocalNexusPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun HomePlaceholderScreen(
    displayName: String,
    onLockClick: () -> Unit,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    val localHaze = remember { HazeState() }
    val edgeConfig = LocalNexusEdgeFadeConfig.current.copy(placement = EdgeFadePlacement.BOTH)

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
                            .padding(end = 52.dp),
                    ) {
                        Text(
                            text = "Good day,\n$displayName",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.onlineDot),
                            )
                            Text(
                                text = "Home placeholder • Ready for live wiring",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

        // Weather card placeholder (Homelab PA reference)
        item {
            CleanCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    ) {
                        Text(
                            text = "21°",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Column {
                            Text("Partly Cloudy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Placeholder • Tap Settings later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = "20° – 29°",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        // "Brief Me" hero placeholder
        item {
            CleanCard(onClick = onOpenUpdates) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Brief me", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "System brief, fleet summary & OTA updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }

        // 3 quick tiles placeholder
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("News", "Planner", "Budget").forEach { title ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.cardSurface,
                        border = BorderStroke(1.dp, palette.hairlineBorder),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Extra placeholder cards so scroll-morphing dock can be tested smoothly
        items(3) { idx ->
            CleanCard {
                Text("Activity Slot #${idx + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Scroll down to preview BeatWave floating dock collapse and smooth spring expansion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
            }
        }

        NexusCircularButton(
            icon = Icons.Rounded.Lock,
            contentDescription = "Lock App",
            onClick = onLockClick,
            hazeState = localHaze,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

/**
 * Server & Fleet Placeholder — showcases SELECTIVE Apple Music-style top progressive blur.
 */
@Composable
fun ServerPlaceholderScreen(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    val localHaze = remember { HazeState() }
    val edgeConfig = LocalNexusEdgeFadeConfig.current.copy(placement = EdgeFadePlacement.BOTH)

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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(end = 52.dp),
                    ) {
                        Text("Server & Apps", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Placeholder telemetry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionTile("Free VRAM", Icons.Rounded.Memory, Modifier.weight(1f))
                    ActionTile("Restart App", Icons.Rounded.RestartAlt, Modifier.weight(1f))
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiPlaceholder("2%", "CPU Busy", Modifier.weight(1f))
                        KpiPlaceholder("51°C", "Temp, cool", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiPlaceholder("33%", "Memory used", Modifier.weight(1f))
                        KpiPlaceholder("333 GB", "Free storage", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiPlaceholder("1.5d", "Uptime", Modifier.weight(1f))
                        KpiPlaceholder("17/17", "Services up", Modifier.weight(1f))
                    }
                }
            }

            item {
                CleanCard {
                    Text("Memory & Storage (Placeholder)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text("Memory • 10.1 / 31 GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { 0.33f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("NVMe • 170 / 502 GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { 0.34f }, modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                CleanCard {
                    Text("AI Models (Placeholder)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "22.5 GB free • Wire to /api/fleet/nodes/:id/ai/ps in next milestone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onlineDot,
                    )
                }
            }

            // Extra scrollable service rows to test scroll-linked Top Blur & BeatWave dock
            items(12) { idx ->
                CleanCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Nexus Service #${idx + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Running on my-mac • Healthy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.onlineDot),
                        )
                    }
                }
            }
        }
        }

        NexusCircularButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = "Refresh telemetry",
            onClick = {},
            hazeState = localHaze,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun AssistantPlaceholderScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 156.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.statusBarsPadding()) {
                Text("Personal Assistant", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Placeholder • Ready for Nexus Fleet AI integration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CleanCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Column {
                        Text("Nexus PA Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "In the next milestone this connects via Rust Core to /api/fleet/ai/conversations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalNexusPalette.current
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.hairlineBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) { content() }
        }
    } else {
        Surface(
            shape = shape,
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.hairlineBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) { content() }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val palette = LocalNexusPalette.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.cardSurface,
        border = BorderStroke(1.dp, palette.hairlineBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun KpiPlaceholder(value: String, label: String, modifier: Modifier = Modifier) {
    val palette = LocalNexusPalette.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.cardSurface,
        border = BorderStroke(1.dp, palette.hairlineBorder),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
