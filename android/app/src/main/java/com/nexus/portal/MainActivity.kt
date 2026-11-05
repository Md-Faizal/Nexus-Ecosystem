package com.nexus.portal

import dev.chrisbanes.haze.*

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.mappls.sdk.core.account.MapplsAccountManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.data.repository.ThemeRepository
import com.nexus.portal.ui.screens.*
import com.nexus.portal.ui.theme.NexusPortalTheme
import com.nexus.portal.ui.theme.PortalTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.content.Intent
import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import com.nexus.portal.services.BackgroundUpdateCheckWorker
import com.nexus.portal.data.repository.UpdateHistoryRepository
import com.nexus.portal.ui.screens.hub.apps.feecalculator.FeeHistoryRepository
import com.nexus.portal.ui.screens.hub.apps.electricitybill.ElectricityBillRepository
import java.util.concurrent.TimeUnit
import java.io.File

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_REQUEST_PERMISSIONS = "com.nexus.portal.ACTION_REQUEST_PERMISSIONS"
        const val EXTRA_PERMISSIONS = "extra_permissions"
    }

    private val _deepLinkState = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // If any telemetry-critical permission was just granted, notify the service
        val anyGranted = results.any { it.value }
        if (anyGranted && com.nexus.portal.services.LocationTimelineService.isRunning.value) {
            // Service is running — it will pick up new permissions on the next command poll cycle (3s)
            android.util.Log.d("MainActivity", "Permissions updated, service will detect on next poll")
        }

        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                              results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            val timelinePrefs = getSharedPreferences(
                com.nexus.portal.services.LocationTimelineService.PREFS_NAME, Context.MODE_PRIVATE
            )
            val isTimelineEnabled = timelinePrefs.getBoolean(
                com.nexus.portal.services.LocationTimelineService.KEY_ENABLED, false
            )
            if (isTimelineEnabled && !com.nexus.portal.services.LocationTimelineService.isRunning.value) {
                android.util.Log.d("MainActivity", "Location permission granted. Auto-starting timeline service.")
                val intent = Intent(applicationContext, com.nexus.portal.services.LocationTimelineService::class.java).apply {
                    action = com.nexus.portal.services.LocationTimelineService.ACTION_START
                }
                ContextCompat.startForegroundService(applicationContext, intent)
            }
        }
    }

    private val _gpxImportUri = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    override fun onResume() {
        super.onResume()
        com.nexus.portal.services.RideTrackerService.isAppInForeground = true
    }

    override fun onPause() {
        com.nexus.portal.services.RideTrackerService.isAppInForeground = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            val navigateTo = intent.getStringExtra("navigate_to")
            intent.removeExtra("navigate_to")
            if (navigateTo != null) {
                _deepLinkState.value = navigateTo
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        
        if (intent.action == ACTION_REQUEST_PERMISSIONS) {
            // Service sent us here because telemetry needs permissions
            val perms = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
            if (perms != null && perms.isNotEmpty()) {
                val toRequest = perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (toRequest.isNotEmpty()) {
                    requestPermissionLauncher.launch(toRequest.toTypedArray())
                }
            }
        } else if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val uri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (uri != null) {
                _gpxImportUri.value = uri
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
            val uri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (uri != null) {
                _gpxImportUri.value = uri
            }
        }
        
        // Register central crash logging engine
        try {
            com.nexus.portal.services.ErrorLogger.register(applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Initialize central theme repository
        try {
            ThemeRepository.init(applicationContext)
            ProfileRepository.init(applicationContext)
            FeeHistoryRepository.init(applicationContext)
            ElectricityBillRepository.init(applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        
        // Initialize Mappls Map SDK with credentials
        try {
            com.mappls.sdk.maps.Mappls.setApiKey("103e28b4435a0b1398148a5d05d4a049")
            com.mappls.sdk.maps.Mappls.getInstance(applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        
        // Dynamic system requests
        if (intent?.action == ACTION_REQUEST_PERMISSIONS) {
            val perms = intent?.getStringArrayExtra(EXTRA_PERMISSIONS)
            if (perms != null && perms.isNotEmpty()) {
                val toRequest = perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (toRequest.isNotEmpty()) {
                    requestPermissionLauncher.launch(toRequest.toTypedArray())
                }
            }
        } else {
            requestInitialPermissions()
        }

        // Initialize Update History and Handle Cleanup
        try {
            UpdateHistoryRepository.init(applicationContext)
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            val currentVersionName = pInfo.versionName ?: "1.0.0"
            val lastKnown = UpdateHistoryRepository.getLastKnownVersionCode()

            if (lastKnown != -1 && currentVersionCode > lastKnown) {
                val updatePrefs = applicationContext.getSharedPreferences("nexus_update_prefs", android.content.Context.MODE_PRIVATE)
                val pendingNotes = updatePrefs.getString("pending_release_notes", "System upgraded to v$currentVersionName") ?: "System upgraded to v$currentVersionName"
                updatePrefs.edit().remove("pending_release_notes").apply()
                UpdateHistoryRepository.registerSuccessfulInstall(currentVersionCode, currentVersionName, pendingNotes)
                
                // Cleanup old updates
                val downloadDir = applicationContext.externalCacheDir ?: applicationContext.cacheDir
                downloadDir?.listFiles()?.forEach {
                    if ((it.name.startsWith("update_") || it.name == "app-debug.apk") && it.name.endsWith(".apk")) {
                        it.delete()
                    }
                }
            } else if (lastKnown == -1) {
                // First run baseline
                UpdateHistoryRepository.registerSuccessfulInstall(currentVersionCode, currentVersionName, "Initial Baseline Installation")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Trigger immediate background update check on app open
        try {
            val immediateCheckRequest = OneTimeWorkRequestBuilder<BackgroundUpdateCheckWorker>().build()
            WorkManager.getInstance(applicationContext).enqueue(immediateCheckRequest)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Schedule periodic update checker service
        try {
            val prefs = applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("enable_background_updates", true)) {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val checkRequest = PeriodicWorkRequestBuilder<BackgroundUpdateCheckWorker>(
                    24, TimeUnit.HOURS
                )
                .setConstraints(constraints)
                .build()
                WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                    "BackgroundUpdateCheck",
                    ExistingPeriodicWorkPolicy.KEEP,
                    checkRequest
                )
            } else {
                WorkManager.getInstance(applicationContext).cancelUniqueWork("BackgroundUpdateCheck")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Schedule periodic Ride Tracker Sync if enabled
        try {
            val rideSettings = com.nexus.portal.managers.RideSettingsManager.getInstance(applicationContext)
            val isAutoSyncEnabled = rideSettings.enableAutoSync.value
            com.nexus.portal.managers.RideTrackerSyncManager.schedulePeriodicSync(applicationContext, isAutoSyncEnabled)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Auto-start battery monitor service if it was left enabled
        try {
            val prefs = getSharedPreferences("battery_monitor_prefs", Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("is_battery_monitor_active", false)
            if (isActive) {
                val intent = Intent(applicationContext, com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryMonitorService::class.java)
                ContextCompat.startForegroundService(applicationContext, intent)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        // Auto-start Location Timeline Service:
        //  - First boot ever: start automatically and record first-boot-done flag.
        //  - Subsequent boots: only restart if the user left it enabled via toggle.
        try {
            val timelinePrefs = getSharedPreferences(
                com.nexus.portal.services.LocationTimelineService.PREFS_NAME, Context.MODE_PRIVATE
            )
            val firstBootDone = timelinePrefs.getBoolean(
                com.nexus.portal.services.LocationTimelineService.KEY_FIRST_BOOT_DONE, false
            )
            val isTimelineEnabled = timelinePrefs.getBoolean(
                com.nexus.portal.services.LocationTimelineService.KEY_ENABLED, false
            )

            val hasLocationPerm = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!firstBootDone) {
                // First-ever launch: enable timeline by default
                timelinePrefs.edit()
                    .putBoolean(com.nexus.portal.services.LocationTimelineService.KEY_FIRST_BOOT_DONE, true)
                    .putBoolean(com.nexus.portal.services.LocationTimelineService.KEY_ENABLED, true)
                    .apply()
                if (hasLocationPerm) {
                    val intent = Intent(applicationContext, com.nexus.portal.services.LocationTimelineService::class.java).apply {
                        action = com.nexus.portal.services.LocationTimelineService.ACTION_START
                    }
                    ContextCompat.startForegroundService(applicationContext, intent)
                }
            } else if (isTimelineEnabled && hasLocationPerm) {
                // Subsequent launches: respect the toggle state and permission
                val intent = Intent(applicationContext, com.nexus.portal.services.LocationTimelineService::class.java).apply {
                    action = com.nexus.portal.services.LocationTimelineService.ACTION_START
                }
                ContextCompat.startForegroundService(applicationContext, intent)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Deep-link navigation routing
        try {
            val navigateTo = intent?.getStringExtra("navigate_to")
            intent?.removeExtra("navigate_to")
            if (navigateTo != null) {
                _deepLinkState.value = navigateTo
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Track last login time (ensuring current login is not shown as last login)
        try {
            val loginPrefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            val currentLogin = loginPrefs.getString("current_login_time", null)
            if (currentLogin != null) {
                loginPrefs.edit().putString("last_login_time", currentLogin).apply()
            }
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy  hh:mm a", java.util.Locale.getDefault())
            val nowStr = sdf.format(java.util.Date())
            loginPrefs.edit().putString("current_login_time", nowStr).apply()
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        setContent {
            val isInitialized by ProfileRepository.isInitialized.collectAsState()
            val activeProfile by ProfileRepository.activeProfile.collectAsState()
            val themeMode by ThemeRepository.themeMode.collectAsState()
            val contentTheme by ThemeRepository.contentTheme.collectAsState()
            val isLiquidGlassEnabled by ThemeRepository.isLiquidGlassEnabled.collectAsState()
            val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }

            com.nexus.portal.ui.liquid.LiquidGlassConfigProvider(context = this@MainActivity) {
                NexusPortalTheme(
                    profileColorHex = activeProfile.themeColorHex,
                    themeMode = themeMode,
                    contentTheme = contentTheme,
                    isLiquidGlassEnabled = isLiquidGlassEnabled,
                    hazeState = if (isLiquidGlassEnabled) hazeState else null
                ) {
                    if (!isInitialized) {
                        val baseMod = Modifier.fillMaxSize()
                        androidx.compose.material3.Surface(
                            modifier = if (isLiquidGlassEnabled) baseMod.then(Modifier.haze(hazeState)) else baseMod,
                            color = if (isLiquidGlassEnabled) androidx.compose.ui.graphics.Color.Transparent else PortalTheme.colors.background
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(color = PortalTheme.colors.primary)
                            }
                        }
                    } else {
                        var actualStartDest = "splash"
                        val isSessionUnlocked by ProfileRepository.isSessionUnlocked.collectAsState()
                        
                        if (!isSessionUnlocked) {
                            actualStartDest = when {
                                activeProfile.id == "guest_auth" -> "auth"
                                !activeProfile.hasPin -> "set_pin"
                                else -> "login"
                            }
                        } else {
                            actualStartDest = "main"
                        }
                        
                        val baseMod = Modifier.fillMaxSize()
                        androidx.compose.material3.Surface(
                            modifier = if (isLiquidGlassEnabled) baseMod.then(Modifier.haze(hazeState)) else baseMod,
                            color = if (isLiquidGlassEnabled) androidx.compose.ui.graphics.Color.Transparent else PortalTheme.colors.background
                        ) {
                            val navController = rememberNavController()
                            
                            val pendingDeepLinkState by _deepLinkState.collectAsState()
                            androidx.compose.runtime.LaunchedEffect(pendingDeepLinkState, isSessionUnlocked) {
                                val route = pendingDeepLinkState
                                if (route != null) {
                                    if (isSessionUnlocked) {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                        }
                                        _deepLinkState.value = null
                                    } else {
                                        ProfileRepository.pendingDeepLink = route
                                        _deepLinkState.value = null
                                    }
                                }
                            }

                            val gpxImportUri by _gpxImportUri.collectAsState()
                            var isImportingGpx = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                            androidx.compose.runtime.LaunchedEffect(gpxImportUri) {
                                gpxImportUri?.let { uri ->
                                    isImportingGpx.value = true
                                    val sessionId = com.nexus.portal.util.GPXImporter.importRide(applicationContext, uri)
                                    isImportingGpx.value = false
                                    _gpxImportUri.value = null
                                    if (sessionId != null) {
                                        android.widget.Toast.makeText(applicationContext, "Ride Imported Successfully!", android.widget.Toast.LENGTH_LONG).show()
                                        navController.navigate("ridetracker/ride/$sessionId")
                                    } else {
                                        android.widget.Toast.makeText(applicationContext, "Failed to import GPX file.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            
                            if (isImportingGpx.value) {
                                androidx.compose.ui.window.Dialog(onDismissRequest = { }) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(color = PortalTheme.colors.primary)
                                    }
                                }
                            }
                            NavHost(navController = navController, startDestination = actualStartDest) {
                                composable("auth") {
                                    ProfileScreen(
                                        onBackClick = null,
                                        onLoginComplete = {
                                            if (!activeProfile.hasPin) {
                                                navController.navigate("set_pin") { popUpTo(0) }
                                            } else {
                                                navController.navigate("login") { popUpTo(0) }
                                            }
                                        }
                                    )
                                }
                                composable("set_pin") {
                                    SetPinScreen(
                                        onPinSet = {
                                            val pending = ProfileRepository.pendingDeepLink
                                            if (pending != null) {
                                                ProfileRepository.pendingDeepLink = null
                                                navController.navigate("main") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                                _deepLinkState.value = pending
                                            } else {
                                                navController.navigate("main") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            }
                                        }
                                    )
                                }
                                composable("login") {
                                    LoginScreen(
                                        onLoginSuccess = {
                                            val pending = ProfileRepository.pendingDeepLink
                                            if (pending != null) {
                                                ProfileRepository.pendingDeepLink = null
                                                navController.navigate("main") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                                _deepLinkState.value = pending
                                            } else {
                                                navController.navigate("main") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            }
                                        },
                                        activity = this@MainActivity
                                    )
                                }
                                composable("main") {
                                    MainScreen(
                                        navController = navController,
                                        activity = this@MainActivity
                                    )
                                }
                                composable("update") {
                                    UpdateScreen(
                                        onBackClick = { navController.popBackStack() },
                                        context = this@MainActivity
                                    )
                                }
                                composable("profiles") {
                                    ProfileSettingsScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onLogout = {
                                            navController.navigate("auth") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("ride_tracker") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.RideTrackerScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onNavigateToHistory = { navController.navigate("ride_history") },
                                        onNavigateToSettings = { navController.navigate("ride_settings") },
                                        onNavigateToGarage = { navController.navigate("ride_garage") },
                                        onNavigateToPerformance = { navController.navigate("performance_tracker") },
                                        context = this@MainActivity
                                    )
                                }
                                composable("performance_tracker") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.PerformanceRecordingScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onNavigateToDetail = { runId -> navController.navigate("performance_detail/$runId") },
                                        onNavigateToCamera = { navController.navigate("performance_camera") },
                                        onNavigateToLeaderboard = { navController.navigate("performance_leaderboard") },
                                        onNavigateToHistory = { navController.navigate("performance_history") }
                                    )
                                }
                                composable("performance_history") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.PerformanceHistoryScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onNavigateToDetail = { runId -> navController.navigate("performance_detail/$runId") }
                                    )
                                }
                                composable("performance_camera") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.PerformanceVideoOverlayScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onNavigateToDetail = { runId ->
                                            navController.navigate("performance_detail/$runId") {
                                                popUpTo("performance_camera") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("performance_detail/{runId}") { backStackEntry ->
                                    val runId = backStackEntry.arguments?.getString("runId") ?: ""
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.PerformanceRunDetailScreen(
                                        runId = runId,
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                                composable("performance_leaderboard") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.PerformanceLeaderboardScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                                composable(
                                    route = "ride_summary?sessionId={sessionId}",
                                    arguments = listOf(
                                        androidx.navigation.navArgument("sessionId") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                        }
                                    )
                                ) { backStackEntry ->
                                    val sessionId = backStackEntry.arguments?.getString("sessionId")
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.RideSummaryScreen(
                                        onBackClick = { navController.popBackStack() },
                                        sessionId = sessionId
                                    )
                                }
                                composable("ride_history") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.RideHistoryScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onRideClick = { sessionId ->
                                            navController.navigate("ride_summary?sessionId=$sessionId")
                                        },
                                        onNavigateToLeaderboard = { navController.navigate("ride_leaderboard") },
                                        onNavigateToGarage = { navController.navigate("ride_garage") }
                                    )
                                }
                                composable("ride_settings") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.RideSettingsScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                                composable("ride_garage") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.GarageScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onNavigateToAddVehicle = { navController.navigate("ride_add_vehicle") },
                                        onNavigateToEditVehicle = { vehicleId ->
                                            navController.navigate("ride_add_vehicle?vehicleId=$vehicleId")
                                        },
                                        onNavigateToLeaderboard = { navController.navigate("ride_leaderboard") },
                                        onNavigateToHistory = { navController.navigate("ride_history") },
                                        onRideClick = { sessionId ->
                                            navController.navigate("ride_summary?sessionId=$sessionId")
                                        }
                                    )
                                }
                                composable("ride_leaderboard") {
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.LeaderboardScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onRideClick = { sessionId ->
                                            navController.navigate("ride_summary?sessionId=$sessionId")
                                        }
                                    )
                                }
                                composable(
                                    route = "ride_add_vehicle?vehicleId={vehicleId}",
                                    arguments = listOf(
                                        androidx.navigation.navArgument("vehicleId") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                        }
                                    )
                                ) { backStackEntry ->
                                    val vehicleId = backStackEntry.arguments?.getString("vehicleId")
                                    com.nexus.portal.ui.screens.hub.apps.ridetracker.AddVehicleScreen(
                                        onBackClick = { navController.popBackStack() },
                                        vehicleId = vehicleId
                                    )
                                }
                                composable("permission_manager") {
                                    com.nexus.portal.ui.screens.hub.apps.permissionmanager.PermissionManagerScreen(
                                        onBackClick = { navController.popBackStack() },
                                        context = this@MainActivity
                                    )
                                }
                                composable("sensor_manager") {
                                    com.nexus.portal.ui.screens.hub.apps.sensormanager.SensorManagerScreen(
                                        onBackClick = { navController.popBackStack() },
                                        context = this@MainActivity
                                    )
                                }
                                composable("battery_monitor") {
                                    com.nexus.portal.ui.screens.hub.apps.batterymonitor.BatteryMonitorScreen(
                                        onBackClick = { 
                                            if (navController.previousBackStackEntry == null) {
                                                getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                                                    .edit().putInt("default_tab", 1).apply()
                                                navController.navigate("main") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            } else {
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }
                                composable("fee_calculator") {
                                    com.nexus.portal.ui.screens.hub.apps.feecalculator.FeeCalculatorScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                                composable("electricity_bill") {
                                    com.nexus.portal.ui.screens.hub.apps.electricitybill.ElectricityBillScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                                composable("mic_tester") {
                                    com.nexus.portal.ui.screens.hub.apps.mictester.MicTesterScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        try {
            com.nexus.portal.services.RideTrackerService.checkAndStartAutoDetectIfConnected(applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        // Android 14+ requires explicit runtime grant for FGS location & microphone subtypes
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add("android.permission.FOREGROUND_SERVICE_LOCATION")
            permissions.add("android.permission.FOREGROUND_SERVICE_MICROPHONE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}
