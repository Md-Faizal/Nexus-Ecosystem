package com.nexus.mobile.ui.automate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.nexus.mobile.automation.MdnsAutomationEngine
import com.nexus.mobile.automation.MdnsDiscoveryManager
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusGlassCard
import com.nexus.mobile.ui.components.NexusModalTopBar
import com.nexus.mobile.ui.components.NexusSectionHeader
import com.nexus.mobile.ui.components.NexusStatusDot
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomateViewModel @Inject constructor(
    val mdnsManager: MdnsDiscoveryManager,
    val engine: MdnsAutomationEngine,
) : ViewModel()

@Composable
fun AutomateScreen(
    onBack: () -> Unit,
    viewModel: AutomateViewModel = hiltViewModel(),
) {
    val peers by viewModel.mdnsManager.peers.collectAsState()
    val isScanning by viewModel.mdnsManager.isScanning.collectAsState()
    val rules by viewModel.engine.rules.collectAsState()
    val events by viewModel.engine.events.collectAsState()
    val palette = LocalNexusPalette.current
    val scope = rememberCoroutineScope()

    var wolMac by remember { mutableStateOf("AA:BB:CC:DD:EE:FF") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NexusModalTopBar(
            title = "Automations",
            subtitle = if (isScanning) "mDNS browsing _nexus._tcp." else "mDNS paused",
            onBack = onBack,
            trailingContent = {
                NexusCircularButton(
                    icon = Icons.Rounded.Radar,
                    contentDescription = "Toggle mDNS scan",
                    tint = if (isScanning) FzLimeAccent else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        if (isScanning) viewModel.mdnsManager.stopDiscovery()
                        else viewModel.mdnsManager.startDiscovery()
                    },
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!actionFeedback.isNullOrBlank()) {
                item {
                    NexusGlassCard(onClick = { actionFeedback = null }) {
                        Text(
                            text = actionFeedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = FzLimeAccent,
                        )
                    }
                }
            }

            // Live mDNS LAN Peers
            item {
                NexusSectionHeader(title = "mDNS LAN Peers", trailing = "${peers.size} found")
                Spacer(Modifier.height(8.dp))
                if (peers.isEmpty()) {
                    NexusGlassCard {
                        Text(
                            text = "No _nexus._tcp. peers discovered on this Wi-Fi yet. Start a desktop agent on the same LAN to auto-switch to LocalLan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(peers, key = { it.serviceName }) { peer ->
                NexusGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            NexusStatusDot(color = palette.onlineDot)
                            Column {
                                Text(peer.nodeId, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${peer.host}:${peer.port} • ${peer.role}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                scope.launch {
                                    val res = viewModel.engine.pingTcp(peer.host, peer.port)
                                    actionFeedback = res.fold(
                                        onSuccess = { ms -> "TCP ping ${peer.nodeId}: ${ms}ms" },
                                        onFailure = { e -> "Ping failed: ${e.message}" },
                                    )
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = palette.cardElevated,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Ping", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Convenience Quick Actions (Wake-on-LAN & Notification Test)
            item {
                NexusSectionHeader(title = "Convenience Actions")
                Spacer(Modifier.height(8.dp))
                NexusGlassCard {
                    Text("Wake-on-LAN Magic Packet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.cardElevated,
                            border = nexusSpecularBorder(palette.isDark, 0.75f),
                            modifier = Modifier.weight(1f),
                        ) {
                            BasicTextField(
                                value = wolMac,
                                onValueChange = { wolMac = it },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                ),
                                cursorBrush = SolidColor(FzLimeAccent),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        Surface(
                            onClick = {
                                scope.launch {
                                    val res = viewModel.engine.sendWakeOnLan(wolMac)
                                    actionFeedback = res.fold(
                                        onSuccess = { "Magic packet sent to $wolMac" },
                                        onFailure = { e -> e.message ?: "WoL failed" },
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = FzLimeAccent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
                                Text("Wake", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = {
                            viewModel.engine.postNotification(
                                title = "Nexus Automation Ready",
                                body = "FZ notification channel active (${peers.size} LAN peers)",
                            )
                            actionFeedback = "Test notification posted"
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.cardElevated,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Test FZ Notification", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // mDNS & Schedule Rules
            item {
                NexusSectionHeader(title = "mDNS & Convenience Rules")
            }

            items(rules, key = { it.id }) { rule ->
                NexusGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(rule.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(rule.triggerSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(rule.actionSummary, style = MaterialTheme.typography.labelSmall, color = FzLimeAccent)
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { viewModel.engine.toggleRule(rule.id) },
                        )
                    }
                }
            }

            if (events.isNotEmpty()) {
                item {
                    NexusSectionHeader(title = "Recent Events")
                }
                items(events, key = { it.id }) { ev ->
                    NexusGlassCard {
                        Text(ev.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(ev.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
