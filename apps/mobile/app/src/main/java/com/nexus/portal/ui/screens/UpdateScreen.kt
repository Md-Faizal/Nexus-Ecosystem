package com.nexus.portal.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nexus.portal.services.BackgroundUpdateWorker
import com.nexus.portal.services.BackgroundUpdateCheckWorker
import com.nexus.portal.ui.components.GlassmorphicButton
import com.nexus.portal.ui.components.GlassmorphicCard
import com.nexus.portal.ui.components.NeonProgressBar
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import com.nexus.portal.ui.theme.toColor
import com.nexus.portal.data.repository.ProfileRepository
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.nexus.portal.ui.theme.LocalBackgroundBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import com.nexus.portal.ui.components.GlassmorphicSurface
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.nexus.portal.core.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.nexus.portal.data.repository.UpdateHistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown

@Volatile
var isUpdateScreenActive: Boolean = false

enum class ScenarioType {
    CACHED,
    PAUSED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(onBackClick: () -> Unit, context: Context) {
    val primaryColor = PortalTheme.colors.primary
    
    val workManager = remember { WorkManager.getInstance(context) }
    var updateUrl by remember { mutableStateOf("https://nexus.faizal.uk/downloads/app-debug.apk") }
    
    val downloadPrefs = remember { context.getSharedPreferences("nexus_download_prefs", Context.MODE_PRIVATE) }
    var activeDownloadVersion by remember { mutableStateOf(downloadPrefs.getString("active_download_version", null)) }
    
    val savedWorkIdStr = downloadPrefs.getString("active_work_id", null)
    var workId by remember {
        mutableStateOf(
            try {
                if (savedWorkIdStr != null) java.util.UUID.fromString(savedWorkIdStr) else null
            } catch (e: Exception) {
                null
            }
        )
    }
    
    // Live WorkManager flow observer
    val workInfo = workId?.let {
        workManager.getWorkInfoByIdFlow(it).collectAsState(initial = null)
    }?.value

    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to query server") }
    var downloadSpeedMBps by remember { mutableStateOf(0f) }
    var downloadEtaSecs by remember { mutableStateOf(0) }
    var downloadedMB by remember { mutableStateOf(0f) }
    var totalSizeMB by remember { mutableStateOf(0f) }
    var elapsedSecs by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    var backendUrlInput by remember { mutableStateOf(NetworkConfig.backendUrl) }
    var serverVersionCode by remember { mutableStateOf<Int?>(null) }
    var serverVersionName by remember { mutableStateOf<String?>(null) }
    var serverReleaseNotes by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    
    // Multi-version check state
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showScenarioDialog by remember { mutableStateOf(false) }
    var scenarioDialogType by remember { mutableStateOf<ScenarioType?>(null) }
    var scenarioVersion by remember { mutableStateOf("") }
    var scenarioCachedFile by remember { mutableStateOf<File?>(null) }
    
    var cachedApks by remember { mutableStateOf(listOf<File>()) }
    var selectedCacheFile by remember { mutableStateOf<File?>(null) }
    val scrollState = rememberScrollState()

    fun refreshCachedApks() {
        val downloadDir = context.externalCacheDir ?: context.cacheDir
        cachedApks = downloadDir?.listFiles { file ->
            (file.name.startsWith("update_") || file.name == "app-debug.apk") && file.extension == "apk"
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        selectedCacheFile = cachedApks.firstOrNull()
    }

    val currentVersionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val history by UpdateHistoryRepository.history.collectAsState()

    val isRunning = workInfo?.state == WorkInfo.State.RUNNING
    val currentDownloadStatus = remember(workInfo, activeDownloadVersion) {
        if (isRunning) {
            "DOWNLOADING"
        } else if (activeDownloadVersion != null) {
            downloadPrefs.getString("status_$activeDownloadVersion", "IDLE") ?: "IDLE"
        } else {
            "IDLE"
        }
    }

    // Dynamic state listener from background thread updates
    LaunchedEffect(workInfo, activeDownloadVersion) {
        if (workInfo != null) {
            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    progress = workInfo.progress.getFloat("progress", 0f)
                    totalSizeMB = workInfo.progress.getFloat("totalSizeMB", 0f)
                    downloadedMB = workInfo.progress.getFloat("downloadedMB", 0f)
                    downloadSpeedMBps = workInfo.progress.getFloat("speedMBps", 0f)
                    downloadEtaSecs = workInfo.progress.getInt("etaSecs", 0)
                    elapsedSecs = workInfo.progress.getInt("elapsedSecs", 0)
                    statusText = "Downloading System Update..."
                }
                WorkInfo.State.SUCCEEDED -> {
                    statusText = "Ready to Install"
                    progress = 1.0f
                    activeDownloadVersion = null
                    refreshCachedApks()
                }
                WorkInfo.State.FAILED -> {
                    val status = downloadPrefs.getString("status_$activeDownloadVersion", "FAILED")
                    statusText = if (status == "PAUSED") "Download Paused" else "Update Download Failed"
                    activeDownloadVersion?.let { ver ->
                        progress = downloadPrefs.getFloat("progress_$ver", 0f)
                        downloadedMB = downloadPrefs.getFloat("downloadedMB_$ver", 0f)
                        totalSizeMB = downloadPrefs.getFloat("totalSizeMB_$ver", 0f)
                    }
                }
                else -> {}
            }
        } else if (activeDownloadVersion != null) {
            val status = downloadPrefs.getString("status_$activeDownloadVersion", "IDLE")
            statusText = when (status) {
                "PAUSED" -> "Download Paused"
                "FAILED" -> "Update Download Failed"
                else -> "Ready"
            }
            progress = downloadPrefs.getFloat("progress_$activeDownloadVersion", 0f)
            downloadedMB = downloadPrefs.getFloat("downloadedMB_$activeDownloadVersion", 0f)
            totalSizeMB = downloadPrefs.getFloat("totalSizeMB_$activeDownloadVersion", 0f)
        }
    }

    fun triggerCheckForUpdates() {
        isChecking = true
        statusText = "Connecting to telemetry server..."
        NetworkConfig.backendUrl = backendUrlInput.trim().removeSuffix("/")

        scope.launch {
            val responseData = withContext(Dispatchers.IO) {
                try {
                    val url = URL(NetworkConfig.updateCheckUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 6000
                    connection.readTimeout = 6000
                    
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()
                        JSONObject(response.toString())
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            isChecking = false
            if (responseData != null) {
                val sc = responseData.optInt("versionCode", 1)
                val sn = responseData.optString("versionName", "1.0.0")
                val notes = responseData.optString("releaseNotes", "")
                val apk = responseData.optString("apkUrl", "")

                serverVersionCode = sc
                serverVersionName = sn
                serverReleaseNotes = notes
                updateUrl = apk

                if (isVersionNewer(currentVersionName, sn)) {
                    updateAvailable = true
                    
                    // Check scenario #7: Intermediate cached v2 vs server v3
                    var foundIntermediate = false
                    val metadataPrefs = context.getSharedPreferences("nexus_apk_metadata", Context.MODE_PRIVATE)
                    
                    // 1. Check cached files
                    for (file in cachedApks) {
                        val metaStr = metadataPrefs.getString(file.name, null)
                        var cachedVer = "Unknown"
                        if (metaStr != null) {
                            try {
                                cachedVer = JSONObject(metaStr).optString("version", "Unknown")
                            } catch (e: Exception) {}
                        }
                        if (cachedVer == "Unknown") {
                            val parts = file.name.removeSuffix(".apk").split("_")
                            if (parts.size >= 3) {
                                cachedVer = parts[1]
                            }
                        }
                        
                        if (isVersionNewer(currentVersionName, cachedVer) && isVersionNewer(cachedVer, sn)) {
                            scenarioDialogType = ScenarioType.CACHED
                            scenarioVersion = cachedVer
                            scenarioCachedFile = file
                            showScenarioDialog = true
                            foundIntermediate = true
                            break
                        }
                    }
                    
                    // 2. Check paused download
                    if (!foundIntermediate && activeDownloadVersion != null) {
                        val pausedVer = activeDownloadVersion!!
                        if (isVersionNewer(currentVersionName, pausedVer) && isVersionNewer(pausedVer, sn)) {
                            scenarioDialogType = ScenarioType.PAUSED
                            scenarioVersion = pausedVer
                            showScenarioDialog = true
                            foundIntermediate = true
                        }
                    }
                    
                    if (!foundIntermediate) {
                        showUpdateDialog = true
                        statusText = "New Update Available!"
                    } else {
                        statusText = "Newer System Update v$sn Available"
                    }
                } else {
                    updateAvailable = false
                    statusText = "Nexus Portal is up to date."
                }
            } else {
                statusText = "Fail: Unable to resolve update check."
                updateAvailable = false
            }
        }
    }

    DisposableEffect(Unit) {
        isUpdateScreenActive = true
        onDispose {
            isUpdateScreenActive = false
        }
    }

    // Auto-fetch updates when screen is composed
    LaunchedEffect(Unit) {
        // Clear active check notification on open
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(com.nexus.portal.services.BackgroundUpdateCheckWorker.CHECK_NOTIFICATION_ID)
        
        triggerCheckForUpdates()
        refreshCachedApks()
    }

    val updateBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalTheme.colors.background)
                .layerBackdrop(updateBackdrop)
        ) {
            TechGridBackground()
        }
        
        CompositionLocalProvider(LocalBackgroundBackdrop provides updateBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "BACKGROUND UPDATER",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Background update toggle
            var enableBackgroundUpdates by remember {
                mutableStateOf(
                    context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                        .getBoolean("enable_background_updates", true)
                )
            }
            
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BACKGROUND CHECKER",
                            color = PortalTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Check for updates & notify in background",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    com.nexus.portal.ui.components.LiquidSwitch(
                        checked = enableBackgroundUpdates,
                        onCheckedChange = { isChecked ->
                            enableBackgroundUpdates = isChecked
                            context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("enable_background_updates", isChecked)
                                .apply()
                            
                            val wm = WorkManager.getInstance(context)
                            if (isChecked) {
                                val periodicCheckRequest = androidx.work.PeriodicWorkRequestBuilder<BackgroundUpdateCheckWorker>(
                                    15, java.util.concurrent.TimeUnit.MINUTES
                                ).build()
                                wm.enqueueUniquePeriodicWork(
                                    "BackgroundUpdateCheck",
                                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                                    periodicCheckRequest
                                )
                            } else {
                                wm.cancelUniqueWork("BackgroundUpdateCheck")
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Configuration Panel
            GlassmorphicCard {
                Text(
                    text = "TARGET UPDATE SERVER",
                    color = primaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    label = { Text("Backend URL Address", color = PortalTheme.colors.textSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        focusedLabelColor = primaryColor,
                        unfocusedBorderColor = PortalTheme.colors.border,
                        unfocusedLabelColor = PortalTheme.colors.textSecondary,
                        focusedTextColor = PortalTheme.colors.textPrimary,
                        unfocusedTextColor = PortalTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                GlassmorphicButton(
                    text = if (isChecking) "CONNECTING TELEMETRY..." else "CHECK FOR UPDATES",
                    glowingColor = primaryColor,
                    onClick = { triggerCheckForUpdates() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status Display Card
            GlassmorphicCard {
                Text(
                    text = "UPDATE PROGRESS HUB",
                    color = primaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Local Version:", color = PortalTheme.colors.textSecondary, fontSize = 14.sp)
                    Text("v$currentVersionName", color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Status Feed:", color = PortalTheme.colors.textSecondary, fontSize = 14.sp)
                    Text(statusText, color = if (updateAvailable) Color(0xFFFF3366) else PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                if (serverVersionName != null && updateAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = PortalTheme.colors.border)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NEW VERSION DETECTED: v$serverVersionName",
                        color = primaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notes: $serverReleaseNotes",
                        color = PortalTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                // Dynamic progress bar & controls
                val showProgressHub = (isRunning || activeDownloadVersion != null)
                if (showProgressHub) {
                    val displayVersion = activeDownloadVersion ?: serverVersionName ?: "System Update"
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "VERSION: v$displayVersion (${currentDownloadStatus})",
                        color = primaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    NeonProgressBar(progress = progress, color = primaryColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Stats section (refined spacing/position)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PortalTheme.colors.surfaceCard.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, PortalTheme.colors.border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SPEED", color = PortalTheme.colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRunning) String.format(Locale.US, "%.2f MB/s", downloadSpeedMBps) else "0.00 MB/s",
                                color = primaryColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                        
                        Divider(
                            modifier = Modifier.height(24.dp).width(1.dp),
                            color = PortalTheme.colors.border.copy(alpha = 0.5f)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ETA", color = PortalTheme.colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            val etaText = if (isRunning && downloadEtaSecs > 0) {
                                if (downloadEtaSecs >= 60) "${downloadEtaSecs / 60}m ${downloadEtaSecs % 60}s" else "${downloadEtaSecs}s"
                            } else {
                                "--"
                            }
                            Text(etaText, color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Divider(
                            modifier = Modifier.height(24.dp).width(1.dp),
                            color = PortalTheme.colors.border.copy(alpha = 0.5f)
                        )

                        Column(
                            modifier = Modifier.weight(1.5f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DOWNLOADED", color = PortalTheme.colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f / %.1f MB", downloadedMB, totalSizeMB),
                                color = PortalTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Divider(
                            modifier = Modifier.height(24.dp).width(1.dp),
                            color = PortalTheme.colors.border.copy(alpha = 0.5f)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ELAPSED", color = PortalTheme.colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            val elapsedText = if (isRunning) {
                                if (elapsedSecs >= 60) "${elapsedSecs / 60}m ${elapsedSecs % 60}s" else "${elapsedSecs}s"
                            } else {
                                "--"
                            }
                            Text(elapsedText, color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Robust download controls (Pause, Resume, Cancel)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isRunning) {
                            com.kyant.backdrop.catalog.components.LiquidButton(
                                onClick = {
                                    scope.launch {
                                        activeDownloadVersion?.let { ver ->
                                            downloadPrefs.edit().putBoolean("download_paused_$ver", true).apply()
                                            workId?.let { id ->
                                                workManager.cancelWorkById(id)
                                            }
                                        }
                                    }
                                },
                                backdrop = updateBackdrop,
                                modifier = Modifier.weight(1f),
                                surfaceColor = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text("PAUSE", color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            com.kyant.backdrop.catalog.components.LiquidButton(
                                onClick = {
                                    scope.launch {
                                        activeDownloadVersion?.let { ver ->
                                            downloadPrefs.edit().putBoolean("download_paused_$ver", false).apply()
                                            val url = downloadPrefs.getString("active_download_url", updateUrl) ?: updateUrl
                                            val notes = downloadPrefs.getString("active_download_release_notes", serverReleaseNotes) ?: serverReleaseNotes
                                            
                                            val request = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
                                                .setInputData(workDataOf(
                                                    "update_url" to url,
                                                    "version_name" to ver,
                                                    "release_notes" to notes
                                                ))
                                                .build()
                                            workId = request.id
                                            downloadPrefs.edit().putString("active_work_id", request.id.toString()).apply()
                                            workManager.enqueue(request)
                                        }
                                    }
                                },
                                backdrop = updateBackdrop,
                                modifier = Modifier.weight(1f),
                                surfaceColor = primaryColor.copy(alpha = 0.15f)
                            ) {
                                Text("RESUME", color = primaryColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        com.kyant.backdrop.catalog.components.LiquidButton(
                            onClick = {
                                scope.launch {
                                    activeDownloadVersion?.let { ver ->
                                        downloadPrefs.edit()
                                            .putBoolean("download_paused_$ver", false)
                                            .remove("active_download_version")
                                            .remove("status_$ver")
                                            .remove("progress_$ver")
                                            .remove("downloadedMB_$ver")
                                            .remove("totalSizeMB_$ver")
                                            .apply()
                                        
                                        workId?.let { id ->
                                            workManager.cancelWorkById(id)
                                        }
                                        
                                        // Delete temp file
                                        val downloadDir = context.externalCacheDir ?: context.cacheDir
                                        val safeVer = ver.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                        val tempFile = File(downloadDir, "update_${safeVer}_temp.apk")
                                        if (tempFile.exists()) {
                                            tempFile.delete()
                                        }
                                        
                                        activeDownloadVersion = null
                                        statusText = "Download cancelled."
                                        progress = 0f
                                    }
                                }
                            },
                            backdrop = updateBackdrop,
                            modifier = Modifier.weight(1f),
                            surfaceColor = Color(0xFFFF3366).copy(alpha = 0.2f)
                        ) {
                            Text("CANCEL", color = Color(0xFFFF3366), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassmorphicCard {
                Text(
                    text = "CACHED APK STORAGE",
                    color = primaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (cachedApks.isEmpty()) {
                    Text(
                        text = "No downloaded APKs are currently available.",
                        color = PortalTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    cachedApks.forEach { file ->
                        val modifiedDate = remember(file.lastModified()) {
                            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                        }
                        val metaPrefs = remember { context.getSharedPreferences("nexus_apk_metadata", Context.MODE_PRIVATE) }
                        val metaStr = remember(file.name) { metaPrefs.getString(file.name, null) }
                        var apkVersion by remember(file.name) { mutableStateOf("Unknown") }
                        var apkReleaseNotes by remember(file.name) { mutableStateOf("No release notes available") }
                        
                        LaunchedEffect(metaStr) {
                            if (metaStr != null) {
                                try {
                                    val json = JSONObject(metaStr)
                                    apkVersion = json.optString("version", "Unknown")
                                    apkReleaseNotes = json.optString("releaseNotes", "No release notes available")
                                } catch (e: Exception) {
                                    val parts = file.name.removeSuffix(".apk").split("_")
                                    if (parts.size >= 3) {
                                        apkVersion = parts[1]
                                    }
                                }
                            } else {
                                val parts = file.name.removeSuffix(".apk").split("_")
                                if (parts.size >= 3) {
                                    apkVersion = parts[1]
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Version: $apkVersion", color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(file.name, color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                                    Text(
                                        text = "Saved: $modifiedDate",
                                        color = PortalTheme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Notes: $apkReleaseNotes",
                                        color = PortalTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        selectedCacheFile = file
                                        launchApkInstaller(context, file)
                                    }) {
                                        Text("Install", color = primaryColor)
                                    }
                                    TextButton(onClick = {
                                        if (file.delete()) {
                                            metaPrefs.edit().remove(file.name).apply()
                                            refreshCachedApks()
                                        }
                                    }) {
                                        Text("Delete", color = Color.Red)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = PortalTheme.colors.border, thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Update History
            if (history.isNotEmpty()) {
                GlassmorphicCard {
                    Text(
                        text = "SYSTEM UPDATE HISTORY",
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        history.forEach { item ->
                            val dateStr = remember(item.timestamp) {
                                SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                            }
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("v${item.version}", color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(dateStr, color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.releaseNotes, color = PortalTheme.colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = PortalTheme.colors.border)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dynamic Action Button
            val hasCachedUpdate = cachedApks.isNotEmpty()
            val buttonText = when {
                workInfo?.state == WorkInfo.State.RUNNING -> "DOWNLOADING..."
                statusText == "Ready to Install" || hasCachedUpdate -> "INSTALL CACHED UPDATE"
                updateAvailable -> "DOWNLOAD SYSTEM UPDATE"
                else -> "CHECK FOR UPDATE"
            }
            
            val isButtonEnabled = workInfo?.state != WorkInfo.State.RUNNING && (statusText == "Ready to Install" || hasCachedUpdate || updateAvailable)

            GlassmorphicButton(
                text = buttonText,
                glowingColor = if (isButtonEnabled) primaryColor else PortalTheme.colors.textSecondary,
                onClick = {
                    when {
                        workInfo?.state == WorkInfo.State.RUNNING -> {
                            // Download is active; no action here.
                        }
                        statusText == "Ready to Install" || hasCachedUpdate -> {
                            launchApkInstaller(context, selectedCacheFile)
                        }
                        updateAvailable -> {
                            val ver = serverVersionName ?: "Unknown"
                            val notes = serverReleaseNotes ?: "No release notes"
                            
                            downloadPrefs.edit()
                                .putString("active_download_version", ver)
                                .putString("active_download_url", updateUrl)
                                .putString("active_download_release_notes", notes)
                                .putString("status_$ver", "DOWNLOADING")
                                .putBoolean("download_paused_$ver", false)
                                .apply()

                            context.getSharedPreferences("nexus_update_prefs", Context.MODE_PRIVATE)
                                .edit().putString("pending_release_notes", notes).apply()
                            
                            val request = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
                                .setInputData(workDataOf(
                                    "update_url" to updateUrl,
                                    "version_name" to ver,
                                    "release_notes" to notes
                                ))
                                .build()
                            workId = request.id
                            downloadPrefs.edit().putString("active_work_id", request.id.toString()).apply()
                            workManager.enqueue(request)
                            activeDownloadVersion = ver
                        }
                        else -> triggerCheckForUpdates()
                    }
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Floating action button for one click up/down scrolling
        val showFab by remember {
            derivedStateOf {
                scrollState.maxValue > 0
            }
        }
        
        if (showFab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp, end = 24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                val isScrolledDown = scrollState.value > scrollState.maxValue / 2
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (isScrolledDown) {
                                scrollState.animateScrollTo(0)
                            } else {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    },
                    containerColor = primaryColor,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (isScrolledDown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isScrolledDown) "Scroll to top" else "Scroll to bottom",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (showUpdateDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showUpdateDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val isLightTheme = !isSystemInDarkTheme()
                val containerColor =
                    if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
                    else Color(0xFF121212).copy(0.4f)

                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 360.dp)
                        .drawBackdrop(
                            backdrop = updateBackdrop,
                            shape = { RoundedCornerShape(36.dp) },
                            effects = {
                                vibrancy()
                                blur(if (isLightTheme) 24f.dp.toPx() else 16f.dp.toPx())
                                lens(36f.dp.toPx(), 72f.dp.toPx(), depthEffect = true)
                            },
                            highlight = { com.kyant.backdrop.highlight.Highlight.Plain },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .shadow(24.dp, RoundedCornerShape(36.dp))
                        .border(1.2.dp, PortalTheme.colors.border.copy(alpha = 0.45f), RoundedCornerShape(36.dp))
                        .padding(28.dp)
                ) {
                    Text(
                        text = "SYSTEM UPDATE AVAILABLE",
                        color = primaryColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Text("New Version:", color = PortalTheme.colors.textSecondary, fontSize = 15.sp)
                             Text(
                                 text = "v${serverVersionName ?: ""}",
                                 color = PortalTheme.colors.textPrimary,
                                 fontWeight = FontWeight.Bold,
                                 fontSize = 15.sp
                             )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = PortalTheme.colors.border)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Release Notes:", color = PortalTheme.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = serverReleaseNotes ?: "No release notes provided.",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(0.08f))
                                .border(1.dp, PortalTheme.colors.border.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                .clickable { showUpdateDialog = false }
                                .height(44.dp)
                                .weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOT NOW",
                                color = PortalTheme.colors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Download button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(primaryColor)
                                .clickable {
                                    showUpdateDialog = false
                                    val ver = serverVersionName ?: "Unknown"
                                    val notes = serverReleaseNotes ?: "No release notes"
                                    
                                    downloadPrefs.edit()
                                        .putString("active_download_version", ver)
                                        .putString("active_download_url", updateUrl)
                                        .putString("active_download_release_notes", notes)
                                        .putString("status_$ver", "DOWNLOADING")
                                        .putBoolean("download_paused_$ver", false)
                                        .apply()

                                    context.getSharedPreferences("nexus_update_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("pending_release_notes", notes).apply()

                                    val request = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
                                        .setInputData(workDataOf(
                                            "update_url" to updateUrl,
                                            "version_name" to ver,
                                            "release_notes" to notes
                                        ))
                                        .build()
                                    workId = request.id
                                    downloadPrefs.edit().putString("active_work_id", request.id.toString()).apply()
                                    workManager.enqueue(request)
                                    activeDownloadVersion = ver
                                }
                                .height(48.dp)
                                .weight(1.3f)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "DOWNLOAD NOW",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        if (showScenarioDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showScenarioDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val isLightTheme = !isSystemInDarkTheme()
                val containerColor =
                    if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
                    else Color(0xFF121212).copy(0.4f)

                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 360.dp)
                        .drawBackdrop(
                            backdrop = updateBackdrop,
                            shape = { RoundedCornerShape(36.dp) },
                            effects = {
                                vibrancy()
                                blur(if (isLightTheme) 24f.dp.toPx() else 16f.dp.toPx())
                                lens(36f.dp.toPx(), 72f.dp.toPx(), depthEffect = true)
                            },
                            highlight = { com.kyant.backdrop.highlight.Highlight.Plain },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .shadow(24.dp, RoundedCornerShape(36.dp))
                        .border(1.2.dp, PortalTheme.colors.border.copy(alpha = 0.45f), RoundedCornerShape(36.dp))
                        .padding(28.dp)
                ) {
                    Text(
                        text = "NEWER UPDATE AVAILABLE",
                        color = primaryColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column {
                        Text(
                            text = "A newer version v${serverVersionName ?: ""} is now available on the server. However, you currently have version v$scenarioVersion ${if (scenarioDialogType == ScenarioType.CACHED) "downloaded & cached" else "partially downloaded"}.",
                            color = PortalTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = PortalTheme.colors.border)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Please choose an action:",
                            color = PortalTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Confirm option (Resume/Install)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(primaryColor)
                                .clickable {
                                    showScenarioDialog = false
                                    if (scenarioDialogType == ScenarioType.CACHED) {
                                        launchApkInstaller(context, scenarioCachedFile)
                                    } else {
                                        activeDownloadVersion?.let { ver ->
                                            downloadPrefs.edit().putBoolean("download_paused_$ver", false).apply()
                                            val url = downloadPrefs.getString("active_download_url", updateUrl) ?: updateUrl
                                            val notes = downloadPrefs.getString("active_download_release_notes", serverReleaseNotes) ?: serverReleaseNotes
                                            
                                            val request = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
                                                .setInputData(workDataOf(
                                                    "update_url" to url,
                                                    "version_name" to ver,
                                                    "release_notes" to notes
                                                ))
                                                .build()
                                            workId = request.id
                                            downloadPrefs.edit().putString("active_work_id", request.id.toString()).apply()
                                            workManager.enqueue(request)
                                        }
                                    }
                                }
                                .height(44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (scenarioDialogType == ScenarioType.CACHED) "INSTALL PENDING v$scenarioVersion" else "RESUME PENDING v$scenarioVersion",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Discard & Download option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFFFF3366).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFFFF3366).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                                .clickable {
                                    showScenarioDialog = false
                                    scope.launch {
                                        if (scenarioDialogType == ScenarioType.CACHED) {
                                            scenarioCachedFile?.let { file ->
                                                val metaPrefs = context.getSharedPreferences("nexus_apk_metadata", Context.MODE_PRIVATE)
                                                if (file.delete()) {
                                                    metaPrefs.edit().remove(file.name).apply()
                                                }
                                            }
                                            refreshCachedApks()
                                        } else {
                                            val downloadDir = context.externalCacheDir ?: context.cacheDir
                                            val safeVer = scenarioVersion.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                            val tempFile = File(downloadDir, "update_${safeVer}_temp.apk")
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                            
                                            downloadPrefs.edit()
                                                .remove("active_download_version")
                                                .remove("status_$scenarioVersion")
                                                .remove("progress_$scenarioVersion")
                                                .remove("downloadedMB_$scenarioVersion")
                                                .remove("totalSizeMB_$scenarioVersion")
                                                .apply()
                                        }

                                        val ver = serverVersionName ?: "Unknown"
                                        val notes = serverReleaseNotes ?: "No release notes"
                                        
                                        downloadPrefs.edit()
                                            .putString("active_download_version", ver)
                                            .putString("active_download_url", updateUrl)
                                            .putString("active_download_release_notes", notes)
                                            .putString("status_$ver", "DOWNLOADING")
                                            .putBoolean("download_paused_$ver", false)
                                            .apply()

                                        context.getSharedPreferences("nexus_update_prefs", Context.MODE_PRIVATE)
                                            .edit().putString("pending_release_notes", notes).apply()

                                        val request = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
                                            .setInputData(workDataOf(
                                                "update_url" to updateUrl,
                                                "version_name" to ver,
                                                "release_notes" to notes
                                            ))
                                            .build()
                                        workId = request.id
                                        downloadPrefs.edit().putString("active_work_id", request.id.toString()).apply()
                                        workManager.enqueue(request)
                                        activeDownloadVersion = ver
                                    }
                                }
                                .height(44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DISCARD & DOWNLOAD NEW v${serverVersionName ?: ""}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // Later option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(0.08f))
                                .border(1.dp, PortalTheme.colors.border.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                .clickable { showScenarioDialog = false }
                                .height(44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LATER",
                                color = PortalTheme.colors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
  }
}

fun launchApkInstaller(context: Context, file: File? = null) {
    try {
        // On Android 8.0 (API 26) and above, check if we have permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkFile = file ?: run {
            val downloadDir = context.externalCacheDir ?: context.cacheDir
            downloadDir.listFiles()?.filter {
                (it.name.startsWith("update_") || it.name == "app-debug.apk") && it.name.endsWith(".apk")
            }?.maxByOrNull { it.lastModified() }
        }

        if (apkFile != null && apkFile.exists()) {
            val apkUri = FileProvider.getUriForFile(
                context,
                "com.nexus.portal.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun isVersionNewer(currentName: String, serverName: String): Boolean {
    try {
        val cleanCurrent = currentName.trim().lowercase().removePrefix("v").replace(Regex("[^0-9.]"), "")
        val cleanServer = serverName.trim().lowercase().removePrefix("v").replace(Regex("[^0-9.]"), "")
        
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val serverParts = cleanServer.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLen = maxOf(currentParts.size, serverParts.size)
        for (i in 0 until maxLen) {
            val currVal = currentParts.getOrNull(i) ?: 0
            val servVal = serverParts.getOrNull(i) ?: 0
            if (servVal > currVal) return true
            if (servVal < currVal) return false
        }
    } catch (e: Exception) {
    }
    
    return false
}
