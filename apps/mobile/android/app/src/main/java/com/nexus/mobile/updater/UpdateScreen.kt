package com.nexus.mobile.updater

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusCircularBackButton
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.nexusEdgeFadeBrush
import com.nexus.mobile.ui.components.rememberEdgeFadeAlpha
import com.nexus.mobile.ui.components.resolveColor
import com.nexus.mobile.ui.theme.LocalNexusPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

sealed interface UpdateUiState {
    data class UpToDate(val currentVersionName: String) : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(
        val versionName: String,
        val releaseNotes: String,
        val sha256Preview: String = "",
    ) : UpdateUiState
    data class Downloading(
        val versionName: String,
        val progressFraction: Float,
        val speedLabel: String,
        val etaLabel: String,
    ) : UpdateUiState
    data class ReadyToInstall(val versionName: String, val apkPath: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@Composable
fun UpdateScreen(
    state: UpdateUiState,
    currentVersionName: String,
    currentVersionCode: Int,
    onBackClick: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val localHaze = remember { HazeState() }
    val listState = rememberLazyListState()
    val edgeConfig = LocalNexusEdgeFadeConfig.current
    val topColor = edgeConfig.topColor.resolveColor()
    val topBlurAlpha = if (topColor != null) rememberEdgeFadeAlpha(edgeConfig.topReaction, listState) else 0f

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(localHaze),
            contentPadding = PaddingValues(
                top = 96.dp,
                bottom = 36.dp,
                start = 20.dp,
                end = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Software Update",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Installed: v$currentVersionName (build $currentVersionCode) • Source: faizal.uk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(1.dp, palette.hairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (state) {
                            is UpdateUiState.UpToDate -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = palette.onlineDot,
                                    )
                                    Text(
                                        "You are on the latest release (v${state.currentVersionName})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                OutlinedButton(
                                    onClick = onCheckUpdates,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Check faizal.uk Now")
                                }
                            }
                            UpdateUiState.Checking -> {
                                Text(
                                    "Checking faizal.uk/releases/mobile-latest.json…",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            is UpdateUiState.Available -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                    Text(
                                        "Version ${state.versionName} Available",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = state.releaseNotes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.sha256Preview.isNotBlank()) {
                                    Text(
                                        text = "SHA-256: ${state.sha256Preview}…",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = onDownloadUpdate,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Download & Verify (SHA-256)")
                                }
                            }
                            is UpdateUiState.Downloading -> {
                                Text(
                                    "Downloading v${state.versionName}…",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LinearProgressIndicator(
                                    progress = { state.progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "${(state.progressFraction * 100).toInt()}% • ${state.speedLabel} • ETA ${state.etaLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is UpdateUiState.ReadyToInstall -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = palette.onlineDot,
                                    )
                                    Text(
                                        "v${state.versionName} Verified (SHA-256 OK)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Button(
                                    onClick = { onInstallUpdate(state.apkPath) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Install Verified APK")
                                }
                            }
                            is UpdateUiState.Error -> {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                OutlinedButton(
                                    onClick = onCheckUpdates,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Retry Check")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scroll-linked top gradient mask + Apple Music circular floating back & refresh orbs
        Box(modifier = Modifier.fillMaxWidth()) {
            if (topColor != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = topBlurAlpha }
                        .nexusEdgeFadeBrush(
                            color = topColor,
                            topToBottom = true,
                            maxAlpha = edgeConfig.topMaxAlpha,
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                NexusCircularBackButton(
                    onClick = onBackClick,
                    hazeState = localHaze,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                NexusCircularButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Check for updates",
                    onClick = onCheckUpdates,
                    hazeState = localHaze,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}
