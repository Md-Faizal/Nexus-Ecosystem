package com.nexus.portal.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.data.repository.ThemeMode
import com.nexus.portal.data.repository.ThemeRepository
import com.nexus.portal.data.repository.ContentTheme
import com.nexus.portal.ui.components.GlassmorphicCard
import com.nexus.portal.ui.components.ScrollContainerContent
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import com.nexus.portal.ui.theme.toColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.nexus.portal.ui.components.LiquidSwitch
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.nexus.portal.ui.theme.LocalBackgroundBackdrop
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    activity: FragmentActivity
) {
    val colors = PortalTheme.colors
    val primaryColor = colors.primary // Animated brand accent color
    
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var backendUrlInput by remember { mutableStateOf(NetworkConfig.backendUrl) }
    var pingStatus by remember { mutableStateOf("UNTESTED") } // UNTESTED, PINGING, SUCCESS, FAILED
    var caughtErrorsCount by remember { mutableStateOf(0) }
    


    // Toggle states
    var enforceBiometrics by remember { mutableStateOf(true) }
    var autoPurgeCache by remember { mutableStateOf(false) }
    var developerTraces by remember { mutableStateOf(true) }

    val currentThemeMode by ThemeRepository.themeMode.collectAsState()
    val currentContentTheme by ThemeRepository.contentTheme.collectAsState()
    val isLiquidGlassEnabled by ThemeRepository.isLiquidGlassEnabled.collectAsState()

    // Query remote server details on mount
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${NetworkConfig.backendUrl}/api/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val raw = connection.inputStream.bufferedReader().use { it.readText() }
                    val obj = org.json.JSONObject(raw)
                    caughtErrorsCount = obj.optInt("caughtErrors", 0)
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Ignore, keep count 0
            }
        }
    }

    fun executePingTest() {
        pingStatus = "PINGING"
        coroutineScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val url = URL("$backendUrlInput/api/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    success = true
                    // Instantly lock config to successful URL
                    NetworkConfig.backendUrl = backendUrlInput
                    // Sync profile database
                    ProfileRepository.syncWithBackend()
                }
                connection.disconnect()
            } catch (e: Exception) {
                success = false
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    pingStatus = "SUCCESS"
                    Toast.makeText(activity, "Proxy handshake verified successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    pingStatus = "FAILED"
                    Toast.makeText(activity, "Proxy handshake failed! Server unreachable.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun purgeRemoteErrors() {
        coroutineScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val url = URL("${NetworkConfig.backendUrl}/api/errors")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.connectTimeout = 4000
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    success = true
                    caughtErrorsCount = 0
                }
                connection.disconnect()
            } catch (e: Exception) {
                success = false
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(activity, "Remote crash telemetry database purged cleanly!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "Failed to purge database. Check connection.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val settingsBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .layerBackdrop(settingsBackdrop)
        ) {
            TechGridBackground()
        }

        CompositionLocalProvider(LocalBackgroundBackdrop provides settingsBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Title Header with secret settings trigger
            Text(
                text = "NEXUS SYSTEM SETTINGS",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clickable {
                        nexusTitleClickCount++
                        if (nexusTitleClickCount == 10) {
                            onSecretSettingsOpen()
                            nexusTitleClickCount = 0
                        } else if (nexusTitleClickCount >= 7) {
                            showSecretSettingsHint = true
                        }
                    }
            )
            
            if (showSecretSettingsHint && nexusTitleClickCount < 10) {
                Text(
                    text = "Secret Settings: ${10 - nexusTitleClickCount} clicks remaining...",
                    color = Color.Yellow.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                Text(
                    text = "Configure operational endpoints & biometrics",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Portal Custom Content Themes Card
            Text(
                text = "PORTAL SYSTEM THEMES",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Select active system node environment overrides",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val contentThemes = listOf(
                        Triple(ContentTheme.STANDARD, "Standard Core", Icons.Default.SportsEsports),
                        Triple(ContentTheme.SNOOPY, "Snoopy Node", Icons.Default.Pets),
                        Triple(ContentTheme.DARTH_VADER, "Darth Vader", Icons.Default.Shield)
                    )

                    contentThemes.forEach { (theme, name, icon) ->
                        val isSelected = currentContentTheme == theme
                        val cardBg = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent
                        val cardBorder = if (isSelected) primaryColor else colors.border
                        val iconColor = if (isSelected) primaryColor else colors.textSecondary
                        val textColor = if (isSelected) colors.textPrimary else colors.textSecondary

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable { ThemeRepository.setContentTheme(activity, theme) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = name,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Visual Environment Theme Card (Light / Dark / System Default)
            Text(
                text = "PORTAL VISUAL ENVIRONMENT",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Select system aesthetic node levels",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val themes = listOf(
                        Triple(ThemeMode.DARK, "Dark Mode", Icons.Default.NightsStay),
                        Triple(ThemeMode.LIGHT, "Light Mode", Icons.Default.WbSunny),
                        Triple(ThemeMode.SYSTEM, "Device Theme", Icons.Default.Android)
                    )

                    themes.forEach { (mode, name, icon) ->
                        val isSelected = currentThemeMode == mode
                        val cardBg = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent
                        val cardBorder = if (isSelected) primaryColor else colors.border
                        val iconColor = if (isSelected) primaryColor else colors.textSecondary
                        val textColor = if (isSelected) colors.textPrimary else colors.textSecondary

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable { ThemeRepository.setThemeMode(activity, mode) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = name,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 12.dp))
                
                SettingsToggleRow(
                    label = "Liquid Glass Effect (Backdrop)",
                    checked = isLiquidGlassEnabled,
                    accentColor = primaryColor
                ) {
                    ThemeRepository.setLiquidGlassEnabled(activity, it)
                }
            }

            // Liquid Glass Configuration Section (Visible only when enabled)
            if (isLiquidGlassEnabled) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "LIQUID GLASS CUSTOMISATION",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                )

                ScrollContainerContent(
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    val config = com.nexus.portal.ui.liquid.LocalLiquidGlassConfig.current
                    val updateConfig = com.nexus.portal.ui.liquid.LocalLiquidGlassConfigUpdater.current

                    // Blur Radius
                    Text(text = "Blur Radius: ${config.blurRadius.toInt()}px", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    com.nexus.portal.ui.components.GlassmorphicSlider(
                        value = config.blurRadius,
                        onValueChange = { updateConfig(config.copy(blurRadius = it)) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Vibrancy
                    Text(text = "Vibrancy: ${(config.vibrancyStrength * 100).toInt()}%", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    com.nexus.portal.ui.components.GlassmorphicSlider(
                        value = config.vibrancyStrength,
                        onValueChange = { updateConfig(config.copy(vibrancyStrength = it)) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Lens Radius X
                    Text(text = "Lens Radius X: ${config.lensRadiusX.toInt()}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    com.nexus.portal.ui.components.GlassmorphicSlider(
                        value = config.lensRadiusX,
                        onValueChange = { updateConfig(config.copy(lensRadiusX = it)) },
                        valueRange = 10f..300f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Lens Radius Y
                    Text(text = "Lens Radius Y: ${config.lensRadiusY.toInt()}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    com.nexus.portal.ui.components.GlassmorphicSlider(
                        value = config.lensRadiusY,
                        onValueChange = { updateConfig(config.copy(lensRadiusY = it)) },
                        valueRange = 10f..300f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Corner Radius
                    Text(text = "Corner Radius: ${config.cornerRadius.toInt()}dp", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    com.nexus.portal.ui.components.GlassmorphicSlider(
                        value = config.cornerRadius,
                        onValueChange = { updateConfig(config.copy(cornerRadius = it)) },
                        valueRange = 0f..64f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "WALLPAPER SETTINGS",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                var currentWallpaper by remember { mutableStateOf(prefs.getString("wallpaper_uri", null)) }

                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri: android.net.Uri? ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) {}
                        prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
                        currentWallpaper = uri.toString()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Custom Background Wallpaper", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = if (currentWallpaper != null) "Wallpaper Active (Overrides Tech Grid)" else "No Wallpaper Set (Showing Tech Grid)", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.nexus.portal.ui.components.GlassmorphicButton(
                        text = "Select Image",
                        onClick = { launcher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    )

                    if (currentWallpaper != null) {
                        com.nexus.portal.ui.components.GlassmorphicButton(
                            text = "Clear Image",
                            onClick = {
                                prefs.edit().remove("wallpaper_uri").apply()
                                currentWallpaper = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 1. Connection Endpoint Card
            Text(
                text = "CONNECTION NODE ENDPOINTS",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    label = { Text("Proxy Backend Endpoint", color = colors.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ping Indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val indicatorColor = when (pingStatus) {
                            "SUCCESS" -> Color.Green
                            "FAILED" -> Color.Red
                            "PINGING" -> Color.Yellow
                            else -> Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (pingStatus) {
                                "SUCCESS" -> "Handshake Verified"
                                "FAILED" -> "Handshake Failed"
                                "PINGING" -> "Pinging..."
                                else -> "Unverified Node"
                            },
                            color = indicatorColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Action buttons
                    Row {
                        com.nexus.portal.ui.components.GlassmorphicButton(
                            text = "Test Ping",
                            onClick = { executePingTest() },
                            modifier = Modifier.height(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Hardware and Synced Data Node Card
            Text(
                text = "SECURE TELEMETRY NODES",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                SettingsInfoRow("Telemetry Logs Caught", "$caughtErrorsCount Entries", primaryColor)
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))
                SettingsInfoRow("Active Encryption Strength", "ECC / AES-256", primaryColor)
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))
                SettingsInfoRow("System Architecture", "Android 14 (API 34)", primaryColor)

                Spacer(modifier = Modifier.height(16.dp))

                com.nexus.portal.ui.components.GlassmorphicButton(
                    text = "Purge Telemetry Database",
                    onClick = { purgeRemoteErrors() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Active Background Processes Manager Card
            var isMasterToggleEnabled by remember { mutableStateOf(true) }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE BACKGROUND PROCESSES",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                LiquidSwitch(
                    checked = isMasterToggleEnabled,
                    onCheckedChange = { checked ->
                        isMasterToggleEnabled = checked
                        val am = activity.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        if (!checked) {
                            val runningServices = am.getRunningServices(Int.MAX_VALUE).filter { it.service.packageName == activity.packageName }
                            runningServices.forEach { info ->
                                val serviceName = info.service.shortClassName.substringAfterLast('.')
                                if (serviceName != "NotificationListener" && serviceName != "CustomAccessibilityService") {
                                    val intent = android.content.Intent().apply { component = info.service }
                                    activity.stopService(intent)
                                    if (serviceName == "BatteryMonitorService") {
                                        activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit().putBoolean("is_battery_monitor_active", false).apply()
                                    }
                                }
                            }
                        } else {
                            // Resume core services if previously turned off
                            try {
                                val bmIntent = android.content.Intent(activity, Class.forName("com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryMonitorService"))
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    activity.startForegroundService(bmIntent)
                                } else {
                                    activity.startService(bmIntent)
                                }
                                activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("is_battery_monitor_active", true).apply()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                )
            }

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                var runningServicesList by remember { mutableStateOf(emptyList<android.app.ActivityManager.RunningServiceInfo>()) }
                
                LaunchedEffect(Unit) {
                    while(true) {
                        val am = activity.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        runningServicesList = am.getRunningServices(Int.MAX_VALUE).filter { 
                            it.service.packageName == activity.packageName 
                        }
                        // Update master toggle based on running services
                        val dynamicServicesRunning = runningServicesList.any { 
                            it.service.shortClassName.substringAfterLast('.') !in listOf("NotificationListener", "CustomAccessibilityService", "SystemForegroundService") 
                        }
                        isMasterToggleEnabled = dynamicServicesRunning
                        kotlinx.coroutines.delay(2000)
                    }
                }
                
                val knownServices = listOf("BatteryMonitorService", "RideTrackerService", "FloatingOverlayService")
                val runningNames = runningServicesList.map { it.service.shortClassName.substringAfterLast('.') }
                val allDisplayServices = (knownServices + runningNames.filter { it != "SystemForegroundService" }).distinct()
                
                if (allDisplayServices.isEmpty()) {
                    Text(
                        text = "No active background services running.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    allDisplayServices.forEachIndexed { index, serviceName ->
                        val isRunning = runningNames.contains(serviceName)
                        val isSystemBound = serviceName == "NotificationListener" || serviceName == "CustomAccessibilityService"
                        
                        val reason = when (serviceName) {
                            "BatteryMonitorService" -> "Recording telemetry (1s interval)"
                            "NotificationListener" -> "Listening to status bar notifications"
                            "CustomAccessibilityService" -> "Automating UI actions"
                            "FloatingOverlayService" -> "Displaying screen overlays"
                            "RideTrackerService" -> "Tracking GPS location in background"
                            else -> "Running arbitrary background tasks"
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = serviceName, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = reason, color = colors.textSecondary, fontSize = 11.sp)
                            }
                            if (isSystemBound) {
                                Text(
                                    text = if (isRunning) "BOUND" else "INACTIVE",
                                    color = if (isRunning) Color(0xFF00FF00) else colors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            } else {
                                LiquidSwitch(
                                    checked = isRunning,
                                    onCheckedChange = { checked ->
                                        try {
                                            val serviceClass = Class.forName("com.nexus.portal." + 
                                                if (serviceName == "BatteryMonitorService") "ui.screens.hub.apps.batterymonitor.$serviceName"
                                                else "services.$serviceName"
                                            )
                                            val intent = android.content.Intent(activity, serviceClass)
                                            
                                            if (checked) {
                                                if (serviceName == "RideTrackerService") intent.action = "START_TRACKING"
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && serviceName != "FloatingOverlayService") {
                                                    activity.startForegroundService(intent)
                                                } else {
                                                    activity.startService(intent)
                                                }
                                                if (serviceName == "BatteryMonitorService") {
                                                    activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE)
                                                        .edit().putBoolean("is_battery_monitor_active", true).apply()
                                                }
                                            } else {
                                                activity.stopService(intent)
                                                if (serviceName == "BatteryMonitorService") {
                                                    activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE)
                                                        .edit().putBoolean("is_battery_monitor_active", false).apply()
                                                }
                                            }
                                            
                                            // Force list update
                                            val am = activity.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                                            runningServicesList = am.getRunningServices(Int.MAX_VALUE).filter { 
                                                it.service.packageName == activity.packageName 
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                )
                            }
                        }
                        if (index < allDisplayServices.size - 1) {
                            Divider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Autostart & Persistence Manager Card
            var isAutostartMasterEnabled by remember { 
                mutableStateOf(
                    activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE).getBoolean("is_battery_monitor_active", false) ||
                    activity.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE).getBoolean("enable_background_updates", true)
                ) 
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUTOSTART & PERSISTENCE",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                LiquidSwitch(
                    checked = isAutostartMasterEnabled,
                    onCheckedChange = { checked ->
                        isAutostartMasterEnabled = checked
                        
                        activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("is_battery_monitor_active", checked).apply()
                            
                        activity.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("enable_background_updates", checked).apply()
                            
                        if (checked) {
                            val constraints = androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .setRequiresBatteryNotLow(true)
                                .build()
                            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.nexus.portal.services.BackgroundUpdateCheckWorker>(24, java.util.concurrent.TimeUnit.HOURS)
                                .setConstraints(constraints)
                                .build()
                            androidx.work.WorkManager.getInstance(activity).enqueueUniquePeriodicWork("BackgroundUpdateCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
                        } else {
                            androidx.work.WorkManager.getInstance(activity).cancelUniqueWork("BackgroundUpdateCheck")
                        }
                    }
                )
            }

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                var batteryBootRevival by remember(isAutostartMasterEnabled) { mutableStateOf(activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE).getBoolean("is_battery_monitor_active", false)) }
                var bgUpdateChecker by remember(isAutostartMasterEnabled) { mutableStateOf(activity.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE).getBoolean("enable_background_updates", true)) }

                LaunchedEffect(batteryBootRevival, bgUpdateChecker) {
                    isAutostartMasterEnabled = batteryBootRevival || bgUpdateChecker
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Battery Monitor Boot Revival", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "Automatically restarts telemetry engine on device reboot or crash", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    LiquidSwitch(
                        checked = batteryBootRevival,
                        onCheckedChange = { checked ->
                            batteryBootRevival = checked
                            activity.getSharedPreferences("battery_monitor_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("is_battery_monitor_active", checked).apply()
                        }
                    )
                }

                Divider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Background Update Checker", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "Periodically checks for app updates via WorkManager in the background", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    LiquidSwitch(
                        checked = bgUpdateChecker,
                        onCheckedChange = { checked ->
                            bgUpdateChecker = checked
                            activity.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("enable_background_updates", checked).apply()
                            
                            if (checked) {
                                val constraints = androidx.work.Constraints.Builder()
                                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                    .setRequiresBatteryNotLow(true)
                                    .build()
                                val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.nexus.portal.services.BackgroundUpdateCheckWorker>(24, java.util.concurrent.TimeUnit.HOURS)
                                    .setConstraints(constraints)
                                    .build()
                                androidx.work.WorkManager.getInstance(activity).enqueueUniquePeriodicWork("BackgroundUpdateCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
                            } else {
                                androidx.work.WorkManager.getInstance(activity).cancelUniqueWork("BackgroundUpdateCheck")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            // 5. General Security Toggles Card
            Text(
                text = "SECURITY PREFERENCES",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                SettingsToggleRow("Biometrics Locker Enforcement", enforceBiometrics, primaryColor) { enforceBiometrics = it }
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))
                SettingsToggleRow("Automatic Session Purges", autoPurgeCache, primaryColor) { autoPurgeCache = it }
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))
                SettingsToggleRow("Developer Stack Tracing Logs", developerTraces, primaryColor) { developerTraces = it }
                Divider(color = colors.border, modifier = Modifier.padding(vertical = 10.dp))
                
                val prefs = activity.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
                var hideDockOnScroll by remember { mutableStateOf(prefs.getBoolean("hide_dock_on_scroll", false)) }
                SettingsToggleRow("Hide Dock On Scroll", hideDockOnScroll, primaryColor) {
                    hideDockOnScroll = it
                    prefs.edit().putBoolean("hide_dock_on_scroll", it).apply()
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
      }
    }
}

@Composable
fun SettingsInfoRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = "Toggle to adjust operational levels", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
        }
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedColor = accentColor,
            uncheckedColor = PortalTheme.colors.surfaceCard
        )
    }
}
