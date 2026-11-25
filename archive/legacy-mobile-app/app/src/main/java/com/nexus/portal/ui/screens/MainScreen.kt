package com.nexus.portal.ui.screens

import dev.chrisbanes.haze.*

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.navigation.NavController
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.data.model.UserProfile
import com.nexus.portal.ui.components.GlassmorphicSurface
import com.nexus.portal.ui.theme.PortalTheme
import com.nexus.portal.ui.theme.toColor
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.isSystemInDarkTheme
import com.nexus.portal.ui.theme.LocalAppBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.abs
import kotlin.math.sign

data class TabItem(
    val title: String,
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    activity: FragmentActivity
) {
    val activeProfile by ProfileRepository.activeProfile.collectAsState()
    val profiles by ProfileRepository.authenticatedProfiles.collectAsState()
    val primaryColor = PortalTheme.colors.primary
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val appBackdrop = rememberLayerBackdrop()

    val prefs = activity.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
    var selectedTab by remember { mutableStateOf(prefs.getInt("default_tab", 0)) }
    var switchingProfileId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        prefs.edit().putInt("default_tab", 0).apply()
    }

    val tabs = listOf(
        TabItem("Home", Icons.Default.Home, "Home"),
        TabItem("Hub", Icons.Default.NetworkCheck, "Integration Hub"),
        TabItem("Settings", Icons.Default.Settings, "Settings")
    )

    var isHideDockOnScrollEnabled by remember { mutableStateOf(prefs.getBoolean("hide_dock_on_scroll", false)) }

    val secretPrefs = activity.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val offsetAnimation = remember { Animatable(0f) }
    val dockPanelOffset by remember(density) {
        derivedStateOf {
            val fraction = (offsetAnimation.value / 1000f).coerceIn(-1f, 1f)
            with(density) {
                4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }
    var showDebugBorder by remember { mutableStateOf(secretPrefs.getBoolean("show_debug_border", false)) }

    DisposableEffect(secretPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_debug_border") {
                showDebugBorder = secretPrefs.getBoolean("show_debug_border", false)
            }
        }
        secretPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            secretPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // One-time notification channel disable prompt
    var showNotifChannelPrompt by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val setupDone = secretPrefs.getBoolean("notif_channel_setup_done", false)
        if (!setupDone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showNotifChannelPrompt = true
        }
    }
    
    if (showNotifChannelPrompt) {
        AlertDialog(
            onDismissRequest = {
                showNotifChannelPrompt = false
                secretPrefs.edit().putBoolean("notif_channel_setup_done", true).apply()
            },
            containerColor = PortalTheme.colors.surfaceCard,
            titleContentColor = PortalTheme.colors.textPrimary,
            textContentColor = PortalTheme.colors.textSecondary,
            title = {
                Text(
                    text = "\uD83D\uDD14 Hide Background Notification",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Nexus Portal runs a background service for location timeline tracking. " +
                            "Android requires a persistent notification for this, but you can hide it completely.\n\n" +
                            "Tap the button below to open the notification channel settings, then turn OFF the \"Location Timeline (Silent)\" channel.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifChannelPrompt = false
                        secretPrefs.edit().putBoolean("notif_channel_setup_done", true).apply()
                        try {
                            val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, "location_timeline_silent_channel")
                            }
                            activity.startActivity(channelIntent)
                        } catch (_: Exception) {
                            // Fallback: open app notification settings
                            try {
                                val appIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                                }
                                activity.startActivity(appIntent)
                            } catch (_: Exception) { }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotifChannelPrompt = false
                        secretPrefs.edit().putBoolean("notif_channel_setup_done", true).apply()
                    }
                ) {
                    Text("Skip", color = PortalTheme.colors.textSecondary)
                }
            }
        )
    }

    DisposableEffect(prefs, secretPrefs) {
        val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "hide_dock_on_scroll") {
                isHideDockOnScrollEnabled = sharedPreferences.getBoolean(key, false)
            }
        }
        val secretListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "show_debug_border") {
                showDebugBorder = sharedPreferences.getBoolean(key, false)
            } else if (key == "notif_channel_setup_done") {
                val setupDone = sharedPreferences.getBoolean(key, false)
                if (!setupDone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    showNotifChannelPrompt = true
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        secretPrefs.registerOnSharedPreferenceChangeListener(secretListener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
            secretPrefs.unregisterOnSharedPreferenceChangeListener(secretListener)
        }
    }

    var isDockVisible by remember { mutableStateOf(true) }
    
    val nestedScrollConnection = remember(isHideDockOnScrollEnabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isHideDockOnScrollEnabled) {
                    if (available.y < -5f) {
                        isDockVisible = false
                    } else if (available.y > 5f) {
                        isDockVisible = true
                    }
                } else {
                    if (!isDockVisible) isDockVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(selectedTab) {
        offsetAnimation.animateTo(
            selectedTab.toFloat() * 1000f,
            spring(1f, 300f, 0.5f)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                val isLightTheme = !isSystemInDarkTheme()
                val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.15f) else Color(0xFF121212).copy(alpha = 0.15f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBackdrop(
                            backdrop = appBackdrop,
                            shape = { RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp) },
                            effects = {
                                vibrancy()
                                blur(20f.dp.toPx())
                                lens(100f.dp.toPx(), 100f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.2f))
                            ),
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                    // Header & Profile Identities
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "NEXUS SYSTEM OPERATOR",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Identity Core",
                            color = PortalTheme.colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // Active Profile Info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBackdrop(
                                    backdrop = appBackdrop,
                                    shape = { RoundedCornerShape(16.dp) },
                                    effects = {
                                        vibrancy()
                                        blur(8f.dp.toPx())
                                        lens(24f.dp.toPx(), 24f.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f))
                                    }
                                )
                                .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        navController.navigate("profiles")
                                    }
                                }
                                .padding(14.dp)
                        ) {
                            if (activeProfile.profilePictureUrl != null) {
                                coil.compose.AsyncImage(
                                    model = activeProfile.profilePictureUrl,
                                    contentDescription = "Profile Picture",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeProfile.avatarInitial,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeProfile.name,
                                    color = PortalTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = activeProfile.statusText,
                                    color = PortalTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                        Text(
                            text = "PROFILES",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Identity Node Switchers
                        profiles.forEach { profile ->
                            val isSelected = profile.id == activeProfile.id
                            val isSwitching = profile.id == switchingProfileId
                            val pColor = profile.themeColorHex.toColor()
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .then(
                                        if (isSelected || isSwitching) {
                                            Modifier.drawBackdrop(
                                                backdrop = appBackdrop,
                                                shape = { RoundedCornerShape(12.dp) },
                                                effects = {
                                                    vibrancy()
                                                    blur(6f.dp.toPx())
                                                },
                                                onDrawSurface = {
                                                    drawRect(
                                                        if (isSwitching) primaryColor.copy(alpha = 0.2f)
                                                        else if (isLightTheme) Color.Black.copy(alpha = 0.05f)
                                                        else Color.White.copy(alpha = 0.08f)
                                                    )
                                                }
                                            )
                                        } else Modifier
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected || isSwitching) pColor.copy(alpha = 0.3f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (!isSelected && !isSwitching) {
                                            switchingProfileId = profile.id
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(300)
                                                ProfileRepository.selectProfile(profile.id)
                                                ProfileRepository.lockSession()
                                                drawerState.close()
                                                navController.navigate("login") {
                                                    popUpTo("main") { inclusive = true }
                                                }
                                                switchingProfileId = null
                                            }
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (profile.profilePictureUrl != null) {
                                    coil.compose.AsyncImage(
                                        model = profile.profilePictureUrl,
                                        contentDescription = "Profile Picture",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(pColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.avatarInitial,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = profile.name,
                                        color = PortalTheme.colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = profile.statusText,
                                        color = PortalTheme.colors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Lower settings, standalone logs & logout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    ) {
                        HorizontalDivider(color = PortalTheme.colors.border, modifier = Modifier.padding(bottom = 16.dp))

                        // Navigation link: Update updates node
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        navController.navigate("update")
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Updates", tint = primaryColor)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "System Update Check", color = PortalTheme.colors.textPrimary, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Secure logout button (Liquid Glass red capsule)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .drawBackdrop(
                                    backdrop = appBackdrop,
                                    shape = { RoundedCornerShape(12.dp) },
                                    effects = {
                                        vibrancy()
                                        blur(8f.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(Color(0xFFFF3366).copy(alpha = 0.15f))
                                    }
                                )
                                .border(1.dp, Color(0xFFFF3366), RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        drawerState.close()
                                        navController.navigate("login") {
                                            popUpTo("main") { inclusive = true }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color(0xFFFF3366))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Logout Identity Node", color = Color(0xFFFF3366), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    }
                }
            }
        }
    ) {
        CompositionLocalProvider(LocalAppBackdrop provides appBackdrop) {
            Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = isDockVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + expandVertically() + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .padding(bottom = 40.dp, start = 16.dp, end = 16.dp)
                    ) {
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedTab },
                            onTabSelected = { selectedTab = it },
                            backdrop = appBackdrop,
                            tabsCount = tabs.size,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                LiquidBottomTab(
                                    onClick = { selectedTab = index },
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (selectedTab == index) primaryColor else PortalTheme.colors.textSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    AnimatedVisibility(
                                        visible = selectedTab == index,
                                        enter = fadeIn() + scaleIn(),
                                        exit = fadeOut() + scaleOut()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .width(16.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(primaryColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = PortalTheme.colors.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .layerBackdrop(appBackdrop)
                    .padding(
                        top = innerPadding.calculateTopPadding()
                    )
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = if (showDebugBorder) 1.5.dp else 0.dp,
                            color = if (showDebugBorder) Color(0xFFFF3366) else Color.Transparent
                        ),
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> DashboardScreen(
                            onOpenMenu = {
                                coroutineScope.launch { drawerState.open() }
                            }
                        )
                        1 -> com.nexus.portal.ui.screens.hub.HubScreen(
                            navController = navController,
                            context = activity
                        )
                        2 -> SettingsScreen(
                            activity = activity
                        )
                    }
                }
            }
        }
        }
    }
}
