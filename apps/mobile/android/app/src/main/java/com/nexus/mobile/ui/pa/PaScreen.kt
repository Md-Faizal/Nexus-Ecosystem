package com.nexus.mobile.ui.pa

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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.EdgeFadePlacement
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusEdgeFadeContainer
import com.nexus.mobile.ui.components.NexusGlassCard
import com.nexus.mobile.ui.components.NexusSectionHeader
import com.nexus.mobile.ui.components.NexusStatusDot
import com.nexus.mobile.ui.theme.LocalNexusPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun PaScreen(
    onOpenAiChat: () -> Unit,
    onOpenNexusChat: () -> Unit,
    onOpenAutomations: () -> Unit,
    onOpenCamera: () -> Unit,
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
                            .padding(end = 56.dp),
                    ) {
                        Text(
                            text = "Personal\nAssistant",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NexusStatusDot(color = FzLimeAccent)
                            Text(
                                text = "PA modules in preview • Live Chat & Automations below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Live Connected Apps inside PA
                item {
                    NexusSectionHeader(title = "Live Connected", trailing = "Nexus Network")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaActionCard(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "AI Chat",
                            subtitle = "Stream directly with your fleet Ollama / LLM nodes",
                            badge = "LIVE",
                            accentLime = true,
                            onClick = onOpenAiChat,
                        )
                        PaActionCard(
                            icon = Icons.Rounded.Forum,
                            title = "Nexus Chat",
                            subtitle = "Fleet-wide operator & node message channel",
                            badge = "LIVE",
                            accentLime = false,
                            onClick = onOpenNexusChat,
                        )
                        PaActionCard(
                            icon = Icons.Rounded.Hub,
                            title = "Automations & mDNS",
                            subtitle = "Local Wi-Fi peer triggers, Wake-on-LAN & quick actions",
                            badge = "LIVE",
                            accentLime = false,
                            onClick = onOpenAutomations,
                        )
                    }
                }

                // Dummy PA Modules (not yet built on backend)
                item {
                    NexusSectionHeader(title = "Assistant Modules", trailing = "Dummy UI")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaActionCard(
                            icon = Icons.Rounded.Mic,
                            title = "Voice Brief & Dictation",
                            subtitle = "Hands-free capture & morning summary (coming soon)",
                            badge = "DUMMY",
                            accentLime = false,
                            onClick = {},
                        )
                        PaActionCard(
                            icon = Icons.Rounded.EditNote,
                            title = "Journal & Deep Insights",
                            subtitle = "Nightly reflection, mood & habit trends (coming soon)",
                            badge = "DUMMY",
                            accentLime = false,
                            onClick = {},
                        )
                        PaActionCard(
                            icon = Icons.Rounded.Checklist,
                            title = "Planner & Reminders",
                            subtitle = "Daily schedule & priority tasks (coming soon)",
                            badge = "DUMMY",
                            accentLime = false,
                            onClick = {},
                        )
                        PaActionCard(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "Daily Budget",
                            subtitle = "Spend log & receipt scan extraction (coming soon)",
                            badge = "DUMMY",
                            accentLime = false,
                            onClick = onOpenCamera,
                        )
                    }
                }
            }
        }

        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        NexusCircularButton(
            icon = Icons.Rounded.CameraAlt,
            contentDescription = "Open Multipurpose Camera",
            onClick = onOpenCamera,
            hazeState = localHaze,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 12.dp, end = 20.dp),
        )
    }
}

@Composable
private fun PaActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    accentLime: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalNexusPalette.current
    NexusGlassCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.cardElevated,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (accentLime) FzLimeAccent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (badge == "LIVE") palette.onlineDot.copy(alpha = 0.16f) else palette.cardElevated,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (badge == "LIVE") palette.onlineDot else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
