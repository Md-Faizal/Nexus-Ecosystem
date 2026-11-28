package com.nexus.mobile.ui.network

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.mobile.ui.components.EdgeFadePlacement
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusEdgeFadeContainer
import com.nexus.mobile.ui.components.NexusGlassCard
import com.nexus.mobile.ui.components.NexusSectionHeader
import com.nexus.mobile.ui.components.NexusStatusDot
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale

private data class AppTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isOnline: Boolean = true,
    val isLive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ServerAppsScreen(
    onOpenAiChat: () -> Unit,
    onOpenNexusChat: () -> Unit,
    onOpenAutomations: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    val localHaze = remember { HazeState() }
    val edgeConfig = LocalNexusEdgeFadeConfig.current.copy(placement = EdgeFadePlacement.BOTH)
    var dummyBanner by remember { mutableStateOf<String?>(null) }
    var showInfraDetail by remember { mutableStateOf(false) }

    if (showInfraDetail) {
        FleetInfraScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onControlService = viewModel::controlService,
            onBack = { showInfraDetail = false },
        )
        return
    }

    val cloud = state.selectedCloudNode
    val mesh = state.selectedMeshNode

    val cpuLabel = cloud?.let { "${it.cpuLoadPercent}%" }
        ?: mesh?.cpuLoadPercent?.let { "$it%" }
        ?: "—"
    val memPctLabel = cloud?.let { "${it.memoryUsedPercent}%" }
        ?: mesh?.memoryUsedPercent?.let { "$it%" }
        ?: "—"
    val diskFreeLabel = cloud?.let { String.format(Locale.US, "%.0f GB", it.diskAvailGb) }
        ?: mesh?.diskFreeGb?.let { String.format(Locale.US, "%.0f GB", it) }
        ?: "—"
    val diskSubLabel = cloud?.let { String.format(Locale.US, "Free, out of %.0f GB", it.diskTotalGb) }
        ?: "Free storage"
    val uptimeLabel = formatUptime(cloud?.uptimeSecs ?: mesh?.uptimeSecs)
    val modelsForNode = mesh?.models.orEmpty()

    val everydayApps = remember(onOpenAiChat, onOpenNexusChat, onOpenAutomations) {
        listOf(
            AppTileSpec("AI chat", "Talk to your AI", Icons.Rounded.AutoAwesome, isLive = true, onClick = onOpenAiChat),
            AppTileSpec("Nexus chat", "Fleet mesh chat", Icons.Rounded.Forum, isLive = true, onClick = onOpenNexusChat),
            AppTileSpec("Automations", "mDNS & rules", Icons.Rounded.Hub, isLive = true, onClick = onOpenAutomations),
            AppTileSpec("Fleet & Infra", "Balancers & logs", Icons.Rounded.Dns, isLive = true, onClick = { showInfraDetail = true }),
            AppTileSpec("Charts", "Server history", Icons.AutoMirrored.Rounded.ShowChart, onClick = { dummyBanner = "Charts (Dummy UI)" }),
            AppTileSpec("Uptime", "Better Stack", Icons.Rounded.MonitorHeart, isLive = true, onClick = { showInfraDetail = true }),
        )
    }

    val tools = remember {
        listOf(
            AppTileSpec("Ad blocker", "AdGuard", Icons.Rounded.Shield, onClick = { dummyBanner = "AdGuard (Dummy UI)" }),
            AppTileSpec("Logs", "App activity", Icons.Rounded.Terminal, onClick = { dummyBanner = "Logs (Dummy UI)" }),
            AppTileSpec("Search", "Private search", Icons.Rounded.Search, onClick = { dummyBanner = "Private Search (Dummy UI)" }),
            AppTileSpec("Drive sync", "Google Drive", Icons.Rounded.CloudSync, onClick = { dummyBanner = "Drive Sync (Dummy UI)" }),
            AppTileSpec("Metrics", "Prometheus", Icons.Rounded.Speed, onClick = { dummyBanner = "Prometheus Metrics (Dummy UI)" }),
            AppTileSpec("Dashboard", "Everything", Icons.Rounded.Dashboard, onClick = { dummyBanner = "Full Dashboard (Dummy UI)" }),
        )
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(end = 56.dp),
                    ) {
                        Text(
                            text = "Server & Apps",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.isLoading && state.cloudNodes.isEmpty()) {
                                "Syncing cloud & mesh nodes..."
                            } else {
                                "${state.onlineCloudCount}/${state.cloudNodes.size} cloud • ${state.onlineMeshCount} mesh • ${state.mdnsPeers.size} mDNS"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val banner = state.actionStatus ?: dummyBanner
                if (!banner.isNullOrBlank()) {
                    item {
                        NexusGlassCard(onClick = {
                            viewModel.clearStatus()
                            dummyBanner = null
                        }) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.bodySmall,
                                color = FzLimeAccent,
                            )
                        }
                    }
                }

                // 1. Cloud Nodes (my-mac, cos, pro)
                item {
                    NexusSectionHeader(title = "Cloud Nodes", trailing = cloud?.ip ?: "Tri-Cluster")
                    Spacer(Modifier.height(8.dp))
                    val cloudItems = state.cloudNodes.ifEmpty {
                        listOf("my-mac", "cos", "pro").map { id ->
                            com.nexus.mobile.data.network.CloudNodeStats(
                                id = id, shortLabel = id, hostname = id, role = "Cluster",
                                ip = "", os = "", status = "syncing", uptimeSecs = 0L,
                                cpuLoadPercent = 0, memoryUsedPercent = 0,
                                memoryUsedGb = 0.0, memoryTotalGb = 1.0,
                                diskUsedPercent = 0, diskAvailGb = 0.0, diskTotalGb = 1.0,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        cloudItems.forEach { node ->
                            val isSelected = node.id == (cloud?.id ?: state.selectedCloudId)
                            val isOnline = node.status.equals("online", ignoreCase = true)
                            val mdnsLinked = state.isMdnsLinked(node.id) || state.isMdnsLinked(node.hostname)
                            val shape = RoundedCornerShape(999.dp)
                            Surface(
                                onClick = { viewModel.selectCloudNode(node.id) },
                                shape = shape,
                                color = if (isSelected) palette.cardElevated else palette.cardSurface,
                                border = nexusSpecularBorder(palette.isDark, if (isSelected) 1f else 0.75f),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    NexusStatusDot(
                                        color = if (isOnline) palette.onlineDot else palette.warningDot,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = node.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                    )
                                    if (mdnsLinked) {
                                        Spacer(Modifier.size(4.dp))
                                        Icon(
                                            Icons.Rounded.Wifi,
                                            contentDescription = "mDNS linked",
                                            tint = FzLimeAccent,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Action Buttons for Selected Node
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ServerActionTile(
                            icon = Icons.Rounded.Memory,
                            label = "Free AI\nmemory",
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = viewModel::freeAiMemory,
                            modifier = Modifier.weight(1f),
                        )
                        ServerActionTile(
                            icon = Icons.Rounded.RestartAlt,
                            label = "Refresh\nnode",
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = viewModel::refresh,
                            modifier = Modifier.weight(1f),
                        )
                        HoldToRestartTile(
                            onConfirmed = viewModel::restartSelectedNode,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 3. 2x3 Live Telemetry Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TelemetryStatCard(cpuLabel, "CPU load", Modifier.weight(1f))
                            TelemetryStatCard(memPctLabel, "Memory used", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TelemetryStatCard(diskFreeLabel, diskSubLabel, Modifier.weight(1f))
                            TelemetryStatCard(uptimeLabel, "Uptime", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TelemetryStatCard(state.uptimePercentLabel, "Better Stack Uptime", Modifier.weight(1f))
                            TelemetryStatCard("${modelsForNode.size}", "Models on node", Modifier.weight(1f))
                        }
                    }
                }

                // 4. Memory & Storage Bars
                item {
                    val memRatio = ((cloud?.memoryUsedPercent ?: mesh?.memoryUsedPercent ?: 0) / 100f).coerceIn(0f, 1f)
                    val diskRatio = ((cloud?.diskUsedPercent ?: mesh?.diskUsedPercent ?: 0) / 100f).coerceIn(0f, 1f)
                    NexusGlassCard {
                        ProgressBarRow(
                            label = "Memory",
                            trailing = cloud?.let {
                                String.format(Locale.US, "%.1f GB of %.1f GB", it.memoryUsedGb, it.memoryTotalGb)
                            } ?: memPctLabel,
                            progress = memRatio,
                        )
                        Spacer(Modifier.height(14.dp))
                        ProgressBarRow(
                            label = "Storage",
                            trailing = diskSubLabel,
                            progress = diskRatio,
                        )
                    }
                }

                // 5. Local & Remote Mesh Nodes (with mDNS icon if linked)
                item {
                    NexusSectionHeader(title = "Local & Remote Nodes", trailing = "${state.meshNodes.size} mesh • ${state.mdnsPeers.size} LAN")
                    Spacer(Modifier.height(8.dp))
                    if (state.meshNodes.isEmpty() && state.mdnsPeers.isEmpty()) {
                        NexusGlassCard {
                            Text(
                                text = "No local/remote mesh agents connected right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.meshNodes, key = { it.nodeId }) { node ->
                                val isSelected = node.nodeId == mesh?.nodeId
                                val mdnsLinked = state.isMdnsLinked(node.nodeId) || state.isMdnsLinked(node.hostname)
                                val shape = RoundedCornerShape(16.dp)
                                Surface(
                                    onClick = { viewModel.selectMeshNode(node.nodeId) },
                                    shape = shape,
                                    color = if (isSelected) palette.cardElevated else palette.cardSurface,
                                    border = nexusSpecularBorder(palette.isDark, if (isSelected) 1f else 0.75f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        NexusStatusDot(
                                            color = if (node.status.equals("online", ignoreCase = true)) palette.onlineDot else palette.warningDot,
                                        )
                                        Column {
                                            Text(node.hostname, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            Text("${node.os} • ${node.models.size} models", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (mdnsLinked) {
                                            Icon(
                                                Icons.Rounded.Wifi,
                                                contentDescription = "mDNS Linked",
                                                tint = FzLimeAccent,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            items(state.mdnsPeers.filter { p ->
                                state.meshNodes.none { it.nodeId.equals(p.nodeId, ignoreCase = true) }
                            }, key = { it.serviceName }) { peer ->
                                val shape = RoundedCornerShape(16.dp)
                                Surface(
                                    shape = shape,
                                    color = palette.cardSurface,
                                    border = nexusSpecularBorder(palette.isDark, 0.75f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Rounded.Wifi, contentDescription = "mDNS Peer", tint = FzLimeAccent, modifier = Modifier.size(16.dp))
                                        Column {
                                            Text(peer.nodeId, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            Text("${peer.host}:${peer.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. AI Models (Per Selected Node)
                item {
                    val nodeTitle = mesh?.hostname ?: cloud?.shortLabel ?: "node"
                    NexusGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("AI models • $nodeTitle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${modelsForNode.size} models",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (modelsForNode.isEmpty()) {
                            Text(
                                text = "No Ollama models reported on $nodeTitle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            modelsForNode.forEach { modelName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        NexusStatusDot(color = palette.onlineDot)
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            onClick = { viewModel.toggleModel(modelName, unload = false) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = palette.cardElevated,
                                        ) {
                                            Text(
                                                text = "Load",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        Surface(
                                            onClick = { viewModel.toggleModel(modelName, unload = true) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = palette.cardElevated,
                                        ) {
                                            Text(
                                                text = "Unload",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. Load Balancers, Failovers & Better Stack Uptime (Compact Summary -> Opens Fleet & Infra)
                item {
                    NexusGlassCard(onClick = { showInfraDetail = true }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = FzLimeAccent, modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Balancers, Failovers & Uptime", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${state.runningServicesCount}/${state.services.size} services • ${state.uptimePercentLabel} Better Stack",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 8. Cloudflare R2 & SQLite Sync (Replaces Night Log & GitHub Backup)
                item {
                    NexusGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = FzLimeAccent, modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Cloudflare R2 & SQLite Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${state.lastR2SyncLabel} • my-mac -> cos -> pro",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Surface(
                                onClick = viewModel::triggerR2SqliteSync,
                                shape = RoundedCornerShape(999.dp),
                                color = palette.cardElevated,
                            ) {
                                Text(
                                    text = if (state.isSyncingR2) "Syncing..." else "Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                // 9. Everyday Apps Grid
                item {
                    NexusSectionHeader(title = "Everyday Apps")
                    Spacer(Modifier.height(8.dp))
                    TwoColumnAppGrid(everydayApps)
                }

                // 10. Tools Grid
                item {
                    NexusSectionHeader(title = "Tools")
                    Spacer(Modifier.height(8.dp))
                    TwoColumnAppGrid(tools)
                }
            }
        }

        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        NexusCircularButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = "Refresh Server & Apps",
            onClick = viewModel::refresh,
            hazeState = localHaze,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 12.dp, end = 20.dp),
        )
    }
}

@Composable
private fun TwoColumnAppGrid(items: List<AppTileSpec>) {
    val palette = LocalNexusPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    val shape = RoundedCornerShape(18.dp)
                    Surface(
                        onClick = item.onClick,
                        shape = shape,
                        color = palette.cardSurface,
                        border = nexusSpecularBorder(palette.isDark, 0.75f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.cardElevated,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            tint = if (item.isLive) FzLimeAccent else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                if (item.isOnline) {
                                    NexusStatusDot(
                                        color = palette.onlineDot,
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    NexusGlassCard(modifier = modifier) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServerActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
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
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoldToRestartTile(
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = palette.cardSurface,
        border = nexusSpecularBorder(palette.isDark, 0.75f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmed()
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = "Hold to restart server",
                tint = palette.dangerAccent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Hold to restart\nserver",
                style = MaterialTheme.typography.labelSmall,
                color = palette.dangerAccent,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun ProgressBarRow(label: String, trailing: String, progress: Float) {
    val palette = LocalNexusPalette.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = FzLimeAccent,
            trackColor = palette.cardElevated,
        )
    }
}

private fun formatUptime(secs: Long?): String {
    if (secs == null || secs <= 0L) return "—"
    val days = secs / 86400.0
    return if (days >= 1.0) String.format(Locale.US, "%.1fd", days) else "${secs / 3600}h"
}
