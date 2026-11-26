package com.nexus.mobile.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusFloatingGlass
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import com.nexus.mobile.ui.theme.nexusSpecularSheen
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.roundToInt

enum class NexusTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SERVER("Server", Icons.Rounded.Dns),
    ASSISTANT("PA", Icons.Rounded.AutoAwesome),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

data class DockStatusBanner(
    val title: String,
    val badge: String = "",
    val isWarning: Boolean = false,
    val onClick: () -> Unit = {},
)

/**
 * Scroll-reactive BeatWave + Dual-Island liquid glass floating navigation bar:
 * - Dynamic status banner is HIDDEN by default (`statusBanner = null`) and only appears when triggered.
 * - Bottom-right Profile orb supports:
 *   - Short press: toggles quick profile menu bubbles (Profile, Settings, Updates, Lock, Logout).
 *   - Long press: jumps directly to the Profile page (matching the MacOS companion app).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NexusFloatingDock(
    currentTab: NexusTab,
    onTabSelected: (NexusTab) -> Unit,
    isExpanded: Boolean,
    onExpandRequest: () -> Unit,
    userName: String,
    userRole: String,
    avatarUrl: String?,
    hasPin: Boolean,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdates: () -> Unit,
    onLockNow: () -> Unit,
    onLogout: () -> Unit,
    hazeState: HazeState,
    statusBanner: DockStatusBanner? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var leftIslandWidthPx by remember { mutableFloatStateOf(1f) }
    var isProfileMenuOpen by remember { mutableStateOf(false) }

    var avatarBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) {
            avatarBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = when {
                    avatarUrl.startsWith("http") -> avatarUrl
                    avatarUrl.startsWith("/") -> "https://faizal.uk$avatarUrl"
                    else -> "https://faizal.uk/$avatarUrl"
                }
                URL(resolved).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.onSuccess { avatarBitmap = it }
        }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val edgeConfig = LocalNexusEdgeFadeConfig.current
    val bottomScrimColor = edgeConfig.bottomColor.resolveColor()
    val showBottomScrim = bottomScrimColor != null &&
        (edgeConfig.placement == EdgeFadePlacement.BOTTOM || edgeConfig.placement == EdgeFadePlacement.BOTH)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (showBottomScrim && bottomScrimColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBarBottom + edgeConfig.bottomExtraHeight)
                    .nexusEdgeFadeBrush(
                        color = bottomScrimColor,
                        topToBottom = false,
                        maxAlpha = edgeConfig.bottomMaxAlpha,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 3.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Vertical Pop-up Action Bubbles above the Profile Orb (short press)
        AnimatedVisibility(
            visible = isProfileMenuOpen,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) + fadeIn() + scaleIn(initialScale = 0.85f),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ) + fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 68.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = {
                        isProfileMenuOpen = false
                        onOpenProfile()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = palette.cardElevated.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, palette.hairlineBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${userRole.uppercase()} • View Profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                ProfileMenuBubble(
                    title = "Settings",
                    icon = Icons.Rounded.Settings,
                    containerColor = palette.cardElevated,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        isProfileMenuOpen = false
                        onOpenSettings()
                    },
                )
                ProfileMenuBubble(
                    title = "Software Updates",
                    icon = Icons.Rounded.SystemUpdate,
                    containerColor = palette.cardElevated,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        isProfileMenuOpen = false
                        onOpenUpdates()
                    },
                )
                if (hasPin) {
                    ProfileMenuBubble(
                        title = "Lock App",
                        icon = Icons.Rounded.Lock,
                        containerColor = palette.cardElevated,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            isProfileMenuOpen = false
                            onLockNow()
                        },
                    )
                }
                ProfileMenuBubble(
                    title = "Sign Out",
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {
                        isProfileMenuOpen = false
                        onLogout()
                    },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Dynamic status bar: ONLY shown when an actual trigger provides statusBanner != null
            AnimatedVisibility(
                visible = isExpanded && statusBanner != null,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(220)),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(180)),
            ) {
                if (statusBanner != null) {
                    Surface(
                        onClick = statusBanner.onClick,
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        border = nexusSpecularBorder(palette.isDark, widthDp = 1.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.35f))
                            .clip(RoundedCornerShape(22.dp))
                            .nexusFloatingGlass(hazeState, palette.glassSurface)
                            .nexusSpecularSheen(palette.isDark),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (statusBanner.isWarning) palette.warningDot else palette.onlineDot),
                                )
                                Text(
                                    text = statusBanner.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (statusBanner.badge.isNotBlank()) {
                                Text(
                                    text = statusBanner.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom floating dual-island row with continuous symmetrical expand/collapse morph
            val expandProgress by animateFloatAsState(
                targetValue = if (isExpanded) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
                label = "DockExpandMorph",
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val orbSize = 54.dp
                val gap = 10.dp
                val leftAreaMax = (maxWidth - orbSize - gap).coerceAtLeast(orbSize)
                val leftPillWidth = orbSize + (leftAreaMax - orbSize) * expandProgress
                val centerPillArea = (leftAreaMax - orbSize - gap).coerceAtLeast(0.dp)
                val centerAlpha = ((1f - expandProgress) * 1.4f - 0.4f).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.width(leftAreaMax),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // LEFT / MAIN GLASS PILL (smoothly morphs 54.dp <-> full width)
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = Color.Transparent,
                            border = nexusSpecularBorder(palette.isDark, widthDp = 0.85f),
                            modifier = Modifier
                                .width(leftPillWidth)
                                .height(58.dp)
                                .shadow(5.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = 0.22f))
                                .clip(RoundedCornerShape(30.dp))
                                .nexusFloatingGlass(hazeState, palette.glassSurface)
                                .nexusSpecularSheen(palette.isDark)
                                .onGloballyPositioned { leftIslandWidthPx = it.size.width.toFloat() }
                                .pointerInput(isExpanded) {
                                    if (isExpanded) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isDragging = true
                                                dragOffsetPx = offset.x
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                                val tabs = NexusTab.entries
                                                val tabW = leftIslandWidthPx / tabs.size
                                                val idx = (dragOffsetPx / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onTabSelected(tabs[idx])
                                            },
                                            onDragCancel = { isDragging = false },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                dragOffsetPx = change.position.x
                                            },
                                        )
                                    }
                                },
                        ) {
                            if (expandProgress > 0.45f) {
                                val tabs = NexusTab.entries
                                val currentIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
                                val targetIdx = if (isDragging && leftIslandWidthPx > 0f) {
                                    (dragOffsetPx / (leftIslandWidthPx / tabs.size)).coerceIn(0f, tabs.lastIndex.toFloat())
                                } else {
                                    currentIndex.toFloat()
                                }
                                val animatedIdx by animateFloatAsState(
                                    targetValue = targetIdx,
                                    animationSpec = spring(
                                        dampingRatio = if (isDragging) Spring.DampingRatioNoBouncy else 0.78f,
                                        stiffness = if (isDragging) Spring.StiffnessHigh else Spring.StiffnessMediumLow,
                                    ),
                                    label = "DockIndicator",
                                )
                                val contentAlpha = ((expandProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp, vertical = 5.dp)
                                        .graphicsLayer { alpha = contentAlpha },
                                ) {
                                    val tabWidth = maxWidth / tabs.size
                                    Box(
                                        modifier = Modifier
                                            .offset(x = tabWidth * animatedIdx)
                                            .width(tabWidth)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        tabs.forEachIndexed { idx, tab ->
                                            val selected = animatedIdx.roundToInt() == idx
                                            val tint = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        isProfileMenuOpen = false
                                                        onTabSelected(tab)
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label,
                                                    tint = tint,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = tab.label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = tint,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onExpandRequest() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = currentTab.icon,
                                        contentDescription = currentTab.label,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }

                        // COLLAPSED CENTER MINI-PILL (smoothly fades & shrinks away on expand)
                        if (centerAlpha > 0.02f && centerPillArea > 24.dp) {
                            Spacer(Modifier.width(gap * (1f - expandProgress)))
                            Surface(
                                onClick = onExpandRequest,
                                shape = RoundedCornerShape(26.dp),
                                color = Color.Transparent,
                                border = nexusSpecularBorder(palette.isDark, widthDp = 1.1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .graphicsLayer { alpha = centerAlpha }
                                    .shadow(6.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.25f))
                                    .clip(RoundedCornerShape(26.dp))
                                    .nexusFloatingGlass(hazeState, palette.glassSurface)
                                    .nexusSpecularSheen(palette.isDark),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                    AnimatedContent(
                                        targetState = currentTab.label,
                                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        label = "CollapsedTabTitle",
                                    ) { title ->
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // RIGHT PROFILE ORB (Short Press = Quick Menu, Long Press = Profile Page)
                val profileInteraction = remember { MutableInteractionSource() }
                val isProfilePressed by profileInteraction.collectIsPressedAsState()
                val profileScale by animateFloatAsState(
                    targetValue = if (isProfilePressed) 0.88f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "ProfileOrbScale",
                )

                Surface(
                    shape = CircleShape,
                    color = if (isProfileMenuOpen) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = nexusSpecularBorder(palette.isDark, widthDp = 0.9f),
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer {
                            scaleX = profileScale
                            scaleY = profileScale
                        }
                        .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f))
                        .clip(CircleShape)
                        .then(
                            if (isProfileMenuOpen) {
                                Modifier
                            } else {
                                Modifier
                                    .nexusFloatingGlass(hazeState, palette.glassSurface)
                                    .nexusSpecularSheen(palette.isDark)
                            },
                        )
                        .combinedClickable(
                            interactionSource = profileInteraction,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isProfileMenuOpen = !isProfileMenuOpen
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isProfileMenuOpen = false
                                onOpenProfile()
                            },
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (isProfileMenuOpen) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Profile Menu",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        } else if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap!!,
                                contentDescription = "$userName Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = userName.trim().take(1).uppercase().ifEmpty { "N" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun ProfileMenuBubble(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val palette = LocalNexusPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "BubbleScale",
    )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = containerColor.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, palette.hairlineBorder),
        modifier = Modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}
