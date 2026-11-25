package com.nexus.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val NexusDarkColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF06202E),
    surface = Color(0xFF0B0E14),
    surfaceContainer = Color(0xFF141923),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
)

private val NexusLightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    surface = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
)

@Composable
fun NexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> NexusDarkColors
        else -> NexusLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Selective, tasteful frosted-glass chrome ONLY for top-level floating bars/pills
 * (e.g., floating bottom navigation pill or mini status bar).
 * Never apply to scrolling list items to preserve 120Hz frame pacing.
 */
fun Modifier.nexusFloatingGlass(
    hazeState: HazeState,
    containerColor: Color,
): Modifier = this.hazeEffect(
    state = hazeState,
    style = HazeStyle(
        backgroundColor = containerColor.copy(alpha = 0.72f),
        tint = HazeTint(containerColor.copy(alpha = 0.20f)),
    ),
)
