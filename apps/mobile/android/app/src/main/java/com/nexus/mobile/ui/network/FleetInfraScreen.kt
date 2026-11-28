package com.nexus.mobile.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusGlassCard
import com.nexus.mobile.ui.components.NexusModalTopBar
import com.nexus.mobile.ui.components.NexusSectionHeader
import com.nexus.mobile.ui.components.NexusStatusDot
import com.nexus.mobile.ui.theme.LocalNexusPalette

/**
 * Granular Fleet & Infra Control sub-page:
 * Keeps Server & Apps uncluttered while exposing per-service controls,
 * load balancer routes, failover targets, and Better Stack uptime.
 */
@Composable
fun FleetInfraScreen(
    state: ServerAppsUiState,
    onRefresh: () -> Unit,
    onControlService: (serviceId: String, action: String) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalNexusPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NexusModalTopBar(
            title = "Fleet & Infra",
            subtitle = "Load Balancers • Failovers • Uptime ${state.uptimePercentLabel}",
            onBack = onBack,
            trailingContent = {
                NexusCircularButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh Infra",
                    onClick = onRefresh,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NexusSectionHeader(
                    title = "Better Stack & Healthcheck",
                    trailing = "${state.runningServicesCount}/${state.services.size} healthy",
                )
                Spacer(Modifier.height(8.dp))
                NexusGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Cluster Availability", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Primary Ingress (my-mac) -> Standby (cos) -> Aux (pro)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = state.uptimePercentLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FzLimeAccent,
                        )
                    }
                }
            }

            item {
                NexusSectionHeader(title = "Services & Failover Routes")
            }

            if (state.services.isEmpty()) {
                item {
                    NexusGlassCard {
                        Text(
                            text = "No services reported yet. Tap refresh to query /api/services.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.services, key = { it.id }) { svc ->
                val isRunning = svc.status.equals("running", ignoreCase = true)
                NexusGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        ) {
                            NexusStatusDot(color = if (isRunning) palette.onlineDot else palette.warningDot)
                            Column {
                                Text(
                                    text = svc.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${svc.node} • :${svc.port} • ${svc.servingState.ifBlank { svc.status }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (svc.trafficRoute.isNotBlank()) {
                                    Text(
                                        text = svc.trafficRoute,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { onControlService(svc.id, "restart") },
                            shape = RoundedCornerShape(999.dp),
                            color = palette.cardElevated,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart", modifier = Modifier.size(14.dp))
                                Text("Restart", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
