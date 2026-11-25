package com.nexus.portal.ui.screens.hub

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.nexus.portal.ui.theme.LocalBackgroundBackdrop
import com.nexus.portal.ui.theme.LocalAppBackdrop
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap

data class SubAppItem(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val route: String
)

@Composable
fun HubScreen(navController: NavController, context: Context) {
    val primaryColor = PortalTheme.colors.primary
    val prefs = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE)

    val defaultSubApps = listOf(
        SubAppItem(
            id = "ride_tracker",
            name = "Ride Tracker",
            description = "Track your rides on two or four-wheelers with live GPS and accelerometer telemetry.",
            imageUrl = "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=800&q=80", // Tech/bike aesthetic
            route = "ride_tracker"
        ),
        SubAppItem(
            id = "permission_manager",
            name = "Permission Manager",
            description = "Manage system hooks, floating overlays, and notification interceptor permissions.",
            imageUrl = "https://images.unsplash.com/photo-1555949963-aa79dcee981c?w=800&q=80", // Code aesthetic
            route = "permission_manager"
        ),
        SubAppItem(
            id = "sensor_manager",
            name = "Sensor Manager",
            description = "View real-time accelerometer and gravitational oscilloscope data available on the device.",
            imageUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&q=80", // Hardware/chip aesthetic
            route = "sensor_manager"
        ),
        SubAppItem(
            id = "battery_monitor",
            name = "Battery Monitor",
            description = "Advanced persistent background battery telemetry with charging graphs and history.",
            imageUrl = "https://images.unsplash.com/photo-1601524909162-ae8725290836?w=800&q=80", // Battery aesthetic
            route = "battery_monitor"
        )
    )

    var subApps by remember {
        val savedOrder = prefs.getString("hub_app_order", null)?.split(",")
        if (savedOrder != null && savedOrder.isNotEmpty()) {
            val orderedList = mutableListOf<SubAppItem>()
            for (id in savedOrder) {
                defaultSubApps.find { it.id == id }?.let { orderedList.add(it) }
            }
            for (item in defaultSubApps) {
                if (!orderedList.contains(item)) orderedList.add(item)
            }
            mutableStateOf<List<SubAppItem>>(orderedList)
        } else {
            mutableStateOf<List<SubAppItem>>(defaultSubApps)
        }
    }

    LaunchedEffect(subApps) {
        prefs.edit().putString("hub_app_order", subApps.joinToString(",") { it.id }).apply()
    }

    val reorderState = rememberReorderableLazyListState(onMove = { from, to ->
        subApps = subApps.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    })

    var sortMode by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val isLightTheme = !isSystemInDarkTheme()
    val searchBarLayer = rememberGraphicsLayer()
    val searchContentColor = remember(isLightTheme) {
        androidx.compose.animation.Animatable(if (isLightTheme) Color.Black else Color.White)
    }

    LaunchedEffect(searchBarLayer, isLightTheme) {
        val buffer = IntArray(25)
        while (isActive) {
            try {
                if (searchBarLayer.size.width > 0 && searchBarLayer.size.height > 0) {
                    val imageBitmap = searchBarLayer.toImageBitmap()
                    val thumbnail = Bitmap.createScaledBitmap(imageBitmap.asAndroidBitmap(), 5, 5, false)
                        .copy(Bitmap.Config.ARGB_8888, false)
                        .asImageBitmap()
                    thumbnail.readPixels(buffer)
                    val averageLuminance =
                        buffer.sumOf { argb ->
                            val r = (argb shr 16 and 0xFF) / 255f
                            val g = (argb shr 8 and 0xFF) / 255f
                            val b = (argb and 0xFF) / 255f
                            0.2126 * r + 0.7152 * g + 0.0722 * b
                        } / buffer.size

                    val targetColor = if (averageLuminance > 0.5f) Color.Black else Color.White
                    searchContentColor.animateTo(targetColor, tween(300))
                }
            } catch (e: Exception) {
                // Ignore if layout not ready
            }
            kotlinx.coroutines.delay(250) // optimized delay to prevent scroll lag
        }
    }

    val filteredApps = subApps.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) 
    }.let { list ->
        when (sortMode) {
            1 -> list.sortedBy { it.name }
            2 -> list.sortedByDescending { it.name }
            else -> list
        }
    }

    val hubBackdrop = rememberLayerBackdrop()
    val cardsBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalBackgroundBackdrop provides hubBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalTheme.colors.background)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PortalTheme.colors.background)
                    .layerBackdrop(hubBackdrop)
            ) {
                TechGridBackground()
            }

        val isHeaderVisible by remember {
            derivedStateOf {
                reorderState.listState.firstVisibleItemIndex == 0 && reorderState.listState.firstVisibleItemScrollOffset == 0
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400)),
                exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Header
                    Text(
                        text = "SUB APPS HUB",
                        color = PortalTheme.colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Launch individual Nexus Apps",
                        color = PortalTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = reorderState.listState,
                    modifier = Modifier
                        .reorderable(reorderState)
                        .fillMaxSize()
                        .layerBackdrop(cardsBackdrop),
                    verticalArrangement = Arrangement.spacedBy(16f.dp),
                    contentPadding = PaddingValues(top = 80f.dp, bottom = 130f.dp) // Increased padding for bottom nav bar and top space for search bar
                ) {
                    items(filteredApps, key = { it.id }) { app ->
                        val isDragEnabled = sortMode == 0 && searchQuery.isEmpty()
                        
                        ReorderableItem(reorderState, key = app.id, defaultDraggingModifier = Modifier) { isDragging ->
                            val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")
                            val elevation by animateFloatAsState(if (isDragging) 16f else 0f, label = "elevation")
                            
                            SubAppCard(
                                app = app,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                                    .shadow(elevation.dp, RoundedCornerShape(24.dp))
                                    .then(if (isDragEnabled) Modifier.detectReorderAfterLongPress(reorderState) else Modifier),
                                onClick = { navController.navigate(app.route) }
                            )
                        }
                    }
                }
                
                // Floating Search + Sort Row (separated objects)
                // Use combined backdrop to prevent parent-child cyclic rendering loops but capture scrolled cards
                val searchBackdrop = rememberCombinedBackdrop(hubBackdrop, cardsBackdrop)
                val isLightTheme = !isSystemInDarkTheme()
                val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.15f) else Color(0xFF121212).copy(alpha = 0.15f)

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 8.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Bar Bubble
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .then(
                                if (searchBackdrop != null) Modifier.drawBackdrop(
                                    backdrop = searchBackdrop,
                                    shape = { CircleShape },
                                    effects = {
                                        vibrancy()
                                        blur(20f.dp.toPx())
                                        lens(24f.dp.toPx(), 24f.dp.toPx())
                                    },
                                    onDrawSurface = { drawRect(containerColor) },
                                    onDrawBackdrop = { drawBackdrop ->
                                        drawBackdrop()
                                        searchBarLayer.record { drawBackdrop() }
                                    }
                                )
                                else Modifier.background(PortalTheme.colors.surfaceCard, CircleShape)
                            )
                            .border(1.dp, PortalTheme.colors.border.copy(alpha = 0.4f), CircleShape)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = searchContentColor.value.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = searchContentColor.value, fontSize = 16.sp),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search apps...", color = searchContentColor.value.copy(alpha = 0.6f), fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = searchContentColor.value.copy(alpha = 0.6f),
                                modifier = Modifier.clickable { searchQuery = "" }
                            )
                        }
                    }

                    // Sort Button Bubble
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .then(
                                if (searchBackdrop != null) Modifier.drawBackdrop(
                                    backdrop = searchBackdrop,
                                    shape = { CircleShape },
                                    effects = {
                                        vibrancy()
                                        blur(20f.dp.toPx())
                                        lens(24f.dp.toPx(), 24f.dp.toPx())
                                    },
                                    onDrawSurface = { drawRect(containerColor) }
                                )
                                else Modifier.background(PortalTheme.colors.surfaceCard, CircleShape)
                            )
                            .border(1.dp, PortalTheme.colors.border.copy(alpha = 0.4f), CircleShape)
                            .clip(CircleShape)
                            .clickable { sortMode = (sortMode + 1) % 3 },
                        contentAlignment = Alignment.Center
                    ) {
                        val rotateAngle by animateFloatAsState(
                            targetValue = if (sortMode == 1) 0f else if (sortMode == 2) 180f else 90f,
                            animationSpec = tween(300),
                            label = "sort_rotation"
                        )
                        Icon(
                            imageVector = if (sortMode == 0) Icons.Default.Sort else if (sortMode == 1) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = "Sort Apps",
                            tint = if (sortMode != 0) PortalTheme.colors.primary else searchContentColor.value.copy(alpha = 0.6f),
                            modifier = Modifier.graphicsLayer { rotationZ = if (sortMode == 0) 0f else rotateAngle }
                        )
                    }
                }
            }
        }

        // Vertical Liquid Slider — full height, rightmost edge, linked to manual scroll
        if (filteredApps.isNotEmpty()) {
            val listState = reorderState.listState
            val totalItems = filteredApps.size
            val coroutineScope = rememberCoroutineScope()

            // Accurate scroll percentage from list state
            val scrollPercent by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isEmpty() || totalItems == 0) 0f
                    else {
                        val first = visibleItems.first()
                        val totalScrollRange = (totalItems * first.size).toFloat() - layoutInfo.viewportSize.height
                        if (totalScrollRange <= 0) 0f
                        else {
                            val currentScroll = (listState.firstVisibleItemIndex * first.size) + listState.firstVisibleItemScrollOffset
                            (currentScroll / totalScrollRange).coerceIn(0f, 1f)
                        }
                    }
                }
            }

            var sliderValue by remember { mutableFloatStateOf(0f) }
            var isDraggingSlider by remember { mutableStateOf(false) }

            // Always sync manual scroll → slider (unless slider is being dragged)
            LaunchedEffect(scrollPercent) {
                if (!isDraggingSlider) {
                    sliderValue = scrollPercent
                }
            }

            // Full-height box pinned to the absolute rightmost edge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(34.dp)
                    .fillMaxHeight()
                    .padding(top = 88.dp, bottom = 28.dp, end = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // BoxWithConstraints lets us read the actual height so requiredWidth matches it
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val trackLength = maxHeight
                    LiquidSlider(
                        value = { sliderValue },
                        onValueChange = { newValue ->
                            isDraggingSlider = true
                            sliderValue = newValue
                            val targetIndex = (newValue * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                                kotlinx.coroutines.delay(300)
                                isDraggingSlider = false
                            }
                        },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = hubBackdrop,
                        modifier = Modifier
                            .requiredWidth(trackLength)
                            .align(Alignment.Center)
                            .rotate(90f)
                            .graphicsLayer(scaleX = 0.76f, scaleY = 0.76f)
                    )
                }
            }
        }


    }
    }
}

@Composable
fun SubAppCard(app: SubAppItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val primaryColor = PortalTheme.colors.primary
    val backdrop = LocalBackgroundBackdrop.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (backdrop != null) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(32.dp) },
                    effects = {
                        vibrancy()
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    }
                )
                else Modifier.background(PortalTheme.colors.surfaceCard, RoundedCornerShape(32.dp))
            )
            .padding(20.dp)
    ) {
        Column {
            // Big Title Picture Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                coil.compose.AsyncImage(
                    model = app.imageUrl,
                    contentDescription = app.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient overlay for better text visibility if needed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(androidx.compose.ui.graphics.Color.Transparent, PortalTheme.colors.surfaceCard.copy(alpha = 0.8f)),
                                startY = 100f
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Details
            Text(
                text = app.name,
                color = PortalTheme.colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.description,
                color = PortalTheme.colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Open Button
            if (backdrop != null) {
                LiquidButton(
                    onClick = onClick,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = Color.White.copy(0.3f)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "OPEN APP",
                        style = androidx.compose.ui.text.TextStyle(
                            color = PortalTheme.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            } else {
                com.nexus.portal.ui.components.GlassmorphicButton(
                    text = "OPEN APP",
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
            }
        }
    }
}
