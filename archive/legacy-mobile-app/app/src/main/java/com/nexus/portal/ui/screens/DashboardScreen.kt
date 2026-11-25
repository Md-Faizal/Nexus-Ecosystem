package com.nexus.portal.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.data.model.UserProfile
import com.nexus.portal.ui.components.ScrollContainerContent
import com.nexus.portal.ui.components.GlassmorphicSurface
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.components.ScrollContainerContent
import com.nexus.portal.ui.theme.PortalTheme
import com.nexus.portal.util.LocationHelper
import com.nexus.portal.util.WeatherInfo
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.annotations.MarkerOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.os.Bundle
import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import com.nexus.portal.ui.screens.hub.maps.YouDashboardScreen
import com.nexus.portal.ui.screens.hub.maps.FullTimelineScreen
import com.nexus.portal.data.local.timeline.TimelineLocationPoint
import androidx.compose.foundation.rememberScrollState as rememberTimelineScrollState
import androidx.compose.foundation.verticalScroll as verticalTimelineScroll

import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.nexus.portal.ui.theme.LocalBackgroundBackdrop
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenMenu: () -> Unit
) {
    val activeProfile by ProfileRepository.activeProfile.collectAsState()
    val primaryColor = PortalTheme.colors.primary
    val context = LocalContext.current

    val dashboardPrefs = remember { context.getSharedPreferences("dashboard_cached_prefs", Context.MODE_PRIVATE) }
    val cachedLat = dashboardPrefs.getFloat("cached_lat", 0f).toDouble()
    val cachedLon = dashboardPrefs.getFloat("cached_lon", 0f).toDouble()
    val cachedCity = dashboardPrefs.getString("cached_city", null)
    val cachedTemp = dashboardPrefs.getString("cached_temp", null)
    val cachedDesc = dashboardPrefs.getString("cached_desc", null)

    val initialWeatherInfo = remember(cachedCity, cachedTemp, cachedDesc) {
        if (cachedCity != null && cachedTemp != null && cachedDesc != null) {
            WeatherInfo(cachedLat, cachedLon, cachedCity, cachedTemp, cachedDesc)
        } else {
            null
        }
    }

    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(initialWeatherInfo) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var showFullTimeline by remember { mutableStateOf(false) }

    var networkType by remember {
        mutableStateOf(dashboardPrefs.getString("cached_net_status", "ONLINE") ?: "ONLINE")
    }

    LaunchedEffect(weatherInfo) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val capabilities = cm?.getNetworkCapabilities(activeNetwork)
        val freshNetType = if (capabilities != null) {
            when {
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G/4G"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "ONLINE"
            }
        } else {
            "OFFLINE"
        }
        if (freshNetType != networkType) {
            networkType = freshNetType
            dashboardPrefs.edit().putString("cached_net_status", freshNetType).apply()
        }
    }

    val lastLoginTime = remember {
        val loginPrefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        loginPrefs.getString("last_login_time", "First Session") ?: "First Session"
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var isLocationEnabled by remember { mutableStateOf(true) }
    var recenterJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var smallMapInstance by remember { mutableStateOf<MapplsMap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                isLocationEnabled = locationManager?.let {
                    it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                } ?: false
                
                if (locationPermissionGranted && isLocationEnabled) {
                    coroutineScope.launch {
                        val freshWeather = LocationHelper.getCurrentLocationAndWeather(context)
                        if (freshWeather != null && freshWeather != weatherInfo) {
                            weatherInfo = freshWeather
                            dashboardPrefs.edit().apply {
                                putFloat("cached_lat", freshWeather.lat.toFloat())
                                putFloat("cached_lon", freshWeather.lon.toFloat())
                                putString("cached_city", freshWeather.city)
                                putString("cached_temp", freshWeather.temperature)
                                putString("cached_desc", freshWeather.description)
                                apply()
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    LaunchedEffect(locationPermissionGranted) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        isLocationEnabled = locationManager?.let {
            it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } ?: false

        if (locationPermissionGranted && isLocationEnabled) {
            val freshWeather = LocationHelper.getCurrentLocationAndWeather(context)
            if (freshWeather != null && freshWeather != weatherInfo) {
                weatherInfo = freshWeather
                dashboardPrefs.edit().apply {
                    putFloat("cached_lat", freshWeather.lat.toFloat())
                    putFloat("cached_lon", freshWeather.lon.toFloat())
                    putString("cached_city", freshWeather.city)
                    putString("cached_temp", freshWeather.temperature)
                    putString("cached_desc", freshWeather.description)
                    apply()
                }
            }
        }
    }

    val dashboardBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalBackgroundBackdrop provides dashboardBackdrop) {
        Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(dashboardBackdrop)) {
            TechGridBackground()
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header with Hamburger Launcher
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val backdrop = com.nexus.portal.ui.theme.LocalBackgroundBackdrop.current
                    if (backdrop != null) {
                        com.kyant.backdrop.catalog.components.LiquidButton(
                            onClick = onOpenMenu,
                            backdrop = backdrop,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu Drawer",
                                tint = PortalTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "NEXUS COCKPIT",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Homepage",
                            color = primaryColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                
                // Dynamic City Status
                GlassmorphicSurface(
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(20.dp),
                    cornerRadius = 20.dp,
                    tint = primaryColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = weatherInfo?.city?.uppercase() ?: "ONLINE",
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut())
                            },
                            label = "CityStatusFlick"
                        ) { targetCity ->
                            Text(
                                text = targetCity,
                                color = primaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Premium Node Status Panel
            ScrollContainerContent(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeProfile.profilePictureUrl != null) {
                        coil.compose.AsyncImage(
                            model = activeProfile.profilePictureUrl,
                            contentDescription = "Profile Picture",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(activeProfile.themeColorHex))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeProfile.avatarInitial,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        val firstName = activeProfile.name.split(" ").firstOrNull() ?: ""
                        val isFemale = firstName.endsWith("a", ignoreCase = true) || 
                                       firstName.equals("Mary", ignoreCase = true) || 
                                       firstName.equals("Jane", ignoreCase = true)
                        val title = if (isFemale) "Ms." else "Mr."

                        Text(
                            text = "WELCOME $title",
                            color = primaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeProfile.name.uppercase(),
                            color = PortalTheme.colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Last Login: $lastLoginTime",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniGauge("WEATHER", weatherInfo?.temperature ?: "--", primaryColor)
                    MetricMiniGauge("CONDITIONS", weatherInfo?.description?.uppercase() ?: "UNKNOWN", primaryColor)
                    MetricMiniGauge("NET STATUS", networkType, primaryColor)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "LOCATION",
                color = PortalTheme.colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            // Dynamic Map Box with faded edges
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(180.dp)
                    .clickable { isMapExpanded = true },
                shape = RoundedCornerShape(16.dp),
                cornerRadius = 16.dp
            ) {
                if (locationPermissionGranted) {
                    val lat = weatherInfo?.lat ?: 37.7749
                    val lon = weatherInfo?.lon ?: -122.4194
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var mapInitError by remember { mutableStateOf<String?>(null) }
                    val mapView = remember {
                        try {
                            MapView(context)
                        } catch (t: Throwable) {
                            android.util.Log.e("DashboardScreen", "Failed to initialize MapView due to credentials mismatch", t)
                            mapInitError = t.message ?: t.toString()
                            null
                        }
                    }
                    
                    if (mapView != null) {
                        DisposableEffect(lifecycleOwner, mapView) {
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                                    Lifecycle.Event.ON_START -> mapView.onStart()
                                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                                    else -> {}
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }
                        
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.getMapAsync(object : OnMapReadyCallback {
                                    override fun onMapReady(map: MapplsMap) {
                                        smallMapInstance = map
                                        map.cameraPosition = com.mappls.sdk.maps.camera.CameraPosition.Builder()
                                            .target(LatLng(lat, lon))
                                            .zoom(12.0)
                                            .build()
                                        map.clear()
                                        map.addMarker(MarkerOptions().position(LatLng(lat, lon)))

                                        // Auto-recenter to current location after 6 seconds of camera gesture inactivity
                                        map.addOnCameraMoveStartedListener { reason ->
                                            // 1 represents REASON_API_GESTURE (user dragged or zoomed)
                                            if (reason == 1) {
                                                recenterJob?.cancel()
                                                recenterJob = coroutineScope.launch {
                                                    kotlinx.coroutines.delay(6000)
                                                    val currentLat = weatherInfo?.lat ?: 37.7749
                                                    val currentLon = weatherInfo?.lon ?: -122.4194
                                                    map.animateCamera(
                                                        com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(
                                                            LatLng(currentLat, currentLon), 12.0
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    override fun onMapError(p0: Int, p1: String?) {}
                                })
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(PortalTheme.colors.surfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = "Map Error",
                                    tint = PortalTheme.colors.textSecondary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (mapInitError != null) "Map offline: $mapInitError" else "Map integration offline (API Keys Mismatch)",
                                    color = PortalTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }

                    // iOS style detail expand arrow pointing right — positioned on the left
                    IconButton(
                        onClick = { isMapExpanded = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(36.dp)
                            .drawBackdrop(
                                backdrop = dashboardBackdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(6f.dp.toPx())
                                    lens(12f.dp.toPx(), 12f.dp.toPx())
                                },
                                onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Expand Map",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // GPS Current Location auto-center/zoom button
                    IconButton(
                        onClick = {
                            val currentLat = weatherInfo?.lat ?: 37.7749
                            val currentLon = weatherInfo?.lon ?: -122.4194
                            smallMapInstance?.animateCamera(
                                com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(
                                    LatLng(currentLat, currentLon), 12.0
                                )
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .drawBackdrop(
                                backdrop = dashboardBackdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(6f.dp.toPx())
                                    lens(12f.dp.toPx(), 12f.dp.toPx())
                                },
                                onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Current Location",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Awaiting Location Data...", color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "SYSTEM CONTROLLERS",
                color = PortalTheme.colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            // Modern Quick Info grid
            val storageFree = remember {
                try {
                    val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                    val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
                    val gigabytes = bytesAvailable / (1024f * 1024f * 1024f)
                    "${"%.1f".format(gigabytes)} GB Free"
                } catch (e: Exception) {
                    "Unknown"
                }
            }

            val batteryPercent = remember {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val capacity = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
                "$capacity%"
            }

            val batteryStatus = remember {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val isCharging = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    bm?.isCharging == true
                } else {
                    false
                }
                if (isCharging) "Charging" else "On Battery"
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Storage Node
                GlassmorphicSurface(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.3f)
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Storage, contentDescription = "Storage Status", tint = primaryColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Storage Node", color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(storageFree, color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                }
                
                // Card 2: Battery Status
                GlassmorphicSurface(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.3f)
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = "Battery Status", tint = primaryColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Battery level", color = PortalTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("$batteryPercent ($batteryStatus)", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }
    }

    if (isMapExpanded) {
        Dialog(
            onDismissRequest = { isMapExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
             Box(modifier = Modifier.fillMaxSize().background(PortalTheme.colors.background)) {
                var expandedMapInstance by remember { mutableStateOf<MapplsMap?>(null) }
                var searchQuery by remember { mutableStateOf("") }

                val lat = weatherInfo?.lat ?: 37.7749
                val lon = weatherInfo?.lon ?: -122.4194
                val lifecycleOwner = LocalLifecycleOwner.current
                var mapInitErrorExpanded by remember { mutableStateOf<String?>(null) }
                val mapView = remember {
                    try {
                        MapView(context)
                    } catch (t: Throwable) {
                        android.util.Log.e("DashboardScreen", "Failed to initialize MapView due to credentials mismatch", t)
                        mapInitErrorExpanded = t.message ?: t.toString()
                        null
                    }
                }
                
                if (mapView != null) {
                    DisposableEffect(lifecycleOwner, mapView) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                                Lifecycle.Event.ON_START -> mapView.onStart()
                                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                Lifecycle.Event.ON_STOP -> mapView.onStop()
                                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.getMapAsync(object : OnMapReadyCallback {
                                override fun onMapReady(map: MapplsMap) {
                                    expandedMapInstance = map
                                    map.cameraPosition = com.mappls.sdk.maps.camera.CameraPosition.Builder()
                                        .target(LatLng(lat, lon))
                                        .zoom(15.0)
                                        .build()
                                    map.clear()
                                    map.addMarker(MarkerOptions().position(LatLng(lat, lon)))
                                }
                                override fun onMapError(p0: Int, p1: String?) {}
                            })
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(PortalTheme.colors.surfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Map Error",
                                tint = PortalTheme.colors.textSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = if (mapInitErrorExpanded != null) "Map offline: $mapInitErrorExpanded" else "Map integration offline (API Keys Mismatch)",
                                color = PortalTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }

                // Expanded view header containing close button and search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 64.dp), // Leaves 64.dp space at the right side for the default Compass button
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button located in the left top corner of the search bar
                    IconButton(
                        onClick = { isMapExpanded = false },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PortalTheme.colors.surfaceCard)
                            .border(1.dp, PortalTheme.colors.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Map",
                            tint = PortalTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Cyberpunk glassmorphic search bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PortalTheme.colors.surfaceCard)
                            .border(1.dp, PortalTheme.colors.border, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = PortalTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = PortalTheme.colors.textPrimary,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search location...",
                                            color = PortalTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }

                // GPS Current Location auto-center/zoom button for expanded map
                IconButton(
                    onClick = {
                        val currentLat = weatherInfo?.lat ?: 37.7749
                        val currentLon = weatherInfo?.lon ?: -122.4194
                        expandedMapInstance?.animateCamera(
                            com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLat, currentLon), 15.0
                            )
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp, end = 16.dp) // Placed cleanly above the bottom Nav Bar
                        .size(48.dp)
                        .background(PortalTheme.colors.surfaceCard, CircleShape)
                        .border(1.dp, PortalTheme.colors.border, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Current Location",
                        tint = PortalTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // ─── Map Tab State ──────────────────────────────────────
                var selectedMapTab by remember { mutableStateOf("Explore") }

                // Bottom panel: tab bar + conditional content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    // Tab content (Above the tab bar)
                    when (selectedMapTab) {
                        "You" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.8f) // Cover most of the screen/map
                                    .background(PortalTheme.colors.background)
                            ) {
                                com.nexus.portal.ui.screens.hub.maps.YouDashboardScreen(
                                    onExploreTimelineClick = { showFullTimeline = true }
                                )
                            }
                        }
                        "Contribute" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(PortalTheme.colors.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Contribute features coming soon",
                                    color = PortalTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        // Explore: no panel, map takes full screen
                        else -> {}
                    }

                    // Tab bar (At the very bottom)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PortalTheme.colors.surfaceCard)
                            .border(
                                width = 1.dp,
                                color = PortalTheme.colors.border,
                                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BottomNavItem(Icons.Default.LocationOn,  "Explore",   selectedMapTab == "Explore"   ) { selectedMapTab = "Explore" }
                        BottomNavItem(Icons.Default.Person,       "You",       selectedMapTab == "You"       ) { selectedMapTab = "You" }
                        BottomNavItem(Icons.Default.AddCircleOutline, "Contribute", selectedMapTab == "Contribute") { selectedMapTab = "Contribute" }
                    }
                }

                // ─── Full Timeline Overlay ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = showFullTimeline,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    BackHandler { showFullTimeline = false }
                    
                    Box(modifier = Modifier.fillMaxSize().background(PortalTheme.colors.background.copy(alpha = 0.9f))) {
                        com.nexus.portal.ui.screens.hub.maps.FullTimelineScreen(
                            onBackClick = { showFullTimeline = false },
                            onDrawRouteForDay = { points ->
                                expandedMapInstance?.let { map ->
                                    try {
                                        map.clear()
                                        val currentLat = weatherInfo?.lat ?: 37.7749
                                        val currentLon = weatherInfo?.lon ?: -122.4194
                                        map.addMarker(MarkerOptions().position(LatLng(currentLat, currentLon)))
                                        points.filter { it.isSignificantPlace || it.placeLabel.isNotBlank() }
                                            .forEach { p ->
                                                map.addMarker(MarkerOptions().position(LatLng(p.latitude, p.longitude)))
                                            }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onCenterMapOnPoints = { points ->
                                if (points.isNotEmpty()) {
                                    val midIdx = points.size / 2
                                    val midPoint = points[midIdx]
                                    expandedMapInstance?.animateCamera(
                                        com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(
                                            LatLng(midPoint.latitude, midPoint.longitude), 13.0
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (!isLocationEnabled) {
        Dialog(
            onDismissRequest = { /* Force action, do not dismiss */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.95f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .border(1.dp, PortalTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        tint = Color(0xFFFF3366),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Location Services Disabled",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To locate your cockpit node, center geographical maps, and download live weather telemetry, please enable device location services.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PortalTheme.colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Enable Location", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) PortalTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) PortalTheme.colors.primary else PortalTheme.colors.textSecondary)
        }
        Text(label, color = if (selected) PortalTheme.colors.textPrimary else PortalTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun MetricMiniGauge(title: String, value: String, accentColor: Color) {
    Column {
        Text(text = title, color = PortalTheme.colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                    slideOutVertically { height -> -height } + fadeOut())
            },
            label = "GaugeValueFlick"
        ) { targetValue ->
            Text(text = targetValue, color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
