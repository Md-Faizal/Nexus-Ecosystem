package com.nexus.mobile.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

sealed interface UpdateUiState {
    data class UpToDate(val currentVersionName: String) : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(
        val versionName: String,
        val releaseNotes: String,
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
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Software Update",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (state) {
                    is UpdateUiState.UpToDate -> {
                        Text("You're up to date (v${state.currentVersionName})", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onCheckUpdates) {
                            Text("Check for Updates")
                        }
                    }
                    UpdateUiState.Checking -> {
                        Text("Checking faizal.uk for updates...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateUiState.Available -> {
                        Text("Update Available: v${state.versionName}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.releaseNotes, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onDownloadUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("Download & Verify (SHA-256)")
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Text("Downloading v${state.versionName}...", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${state.speedLabel} • ETA ${state.etaLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Text("v${state.versionName} Verified (SHA-256 OK)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onInstallUpdate(state.apkPath) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Install Update")
                        }
                    }
                    is UpdateUiState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onCheckUpdates) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
