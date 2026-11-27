package com.nexus.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusFloatingGlass
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import com.nexus.mobile.ui.theme.nexusSpecularSheen
import dev.chrisbanes.haze.HazeState

/**
 * Apple Music-inspired circular floating action / back button.
 */
@Composable
fun NexusCircularButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    hazeState: HazeState? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val palette = LocalNexusPalette.current
    val haptic = LocalHapticFeedback.current

    val glassMod = if (hazeState != null) {
        Modifier.nexusFloatingGlass(hazeState, palette.glassSurface)
    } else {
        Modifier
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = CircleShape,
        color = if (hazeState != null) Color.Transparent else palette.glassSurface.copy(alpha = 0.78f),
        border = nexusSpecularBorder(palette.isDark, widthDp = 0.75f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(glassMod)
            .nexusSpecularSheen(palette.isDark),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun NexusCircularBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    NexusCircularButton(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Back",
        onClick = onClick,
        modifier = modifier,
        hazeState = hazeState,
    )
}
