package com.nexus.mobile.ui.theme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusEdgeFadeConfig
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

enum class ThemePreference(val key: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Pitch Black"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String): ThemePreference = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@Immutable
data class NexusPalette(
    val isDark: Boolean,
    val hairlineBorder: Color,
    val cardSurface: Color,
    val cardElevated: Color,
    val onlineDot: Color,
    val warningDot: Color,
    val dangerAccent: Color,
    val glassSurface: Color,
)

val LocalNexusPalette = staticCompositionLocalOf {
    NexusPalette(
        isDark = true,
        hairlineBorder = Color.White.copy(alpha = 0.08f),
        cardSurface = Color(0xFF121316),
        cardElevated = Color(0xFF1A1C20),
        onlineDot = Color(0xFF22C55E),
        warningDot = Color(0xFFF59E0B),
        dangerAccent = Color(0xFFEF4444),
        glassSurface = Color(0xFF141519),
    )
}

private val NexusDarkColors = darkColorScheme(
    primary = Color(0xFFF8FAFC),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF24262B),
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF031926),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFF121316),
    surfaceContainerHigh = Color(0xFF1A1C20),
    onSurfaceVariant = Color(0xFF8E95A2),
    outline = Color(0xFF262930),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF3B1214),
    onErrorContainer = Color(0xFFFCA5A5),
)

private val NexusLightColors = lightColorScheme(
    primary = Color(0xFF0F172A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceContainer = Color(0xFFF4F5F7),
    surfaceContainerHigh = Color(0xFFEBECEF),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

@Composable
fun NexusTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> NexusDarkColors
        else -> NexusLightColors
    }

    val palette = if (darkTheme) {
        NexusPalette(
            isDark = true,
            hairlineBorder = Color.White.copy(alpha = 0.08f),
            cardSurface = Color(0xFF121316),
            cardElevated = Color(0xFF1A1C20),
            onlineDot = Color(0xFF22C55E),
            warningDot = Color(0xFFF59E0B),
            dangerAccent = Color(0xFFEF4444),
            glassSurface = Color(0xFF141519),
        )
    } else {
        NexusPalette(
            isDark = false,
            hairlineBorder = Color.Black.copy(alpha = 0.08f),
            cardSurface = Color(0xFFF4F5F7),
            cardElevated = Color(0xFFEBECEF),
            onlineDot = Color(0xFF16A34A),
            warningDot = Color(0xFFD97706),
            dangerAccent = Color(0xFFDC2626),
            glassSurface = Color(0xFFF8FAFC),
        )
    }

    CompositionLocalProvider(
        LocalNexusPalette provides palette,
        LocalNexusEdgeFadeConfig provides NexusEdgeFadeConfig(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/**
 * Apple Music-style scroll-linked top blur alpha:
 * 0f while at top / banner is visible, smoothly fades to 1f once scrolled past the first header/item.
 */
@Composable
fun rememberScrollBlurAlpha(
    listState: LazyListState,
    triggerItemIndex: Int = 1,
    triggerOffsetPx: Int = 80,
): State<Float> {
    val shouldShow by remember(listState, triggerItemIndex, triggerOffsetPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex >= triggerItemIndex ||
                listState.firstVisibleItemScrollOffset > triggerOffsetPx
        }
    }
    return animateFloatAsState(
        targetValue = if (shouldShow) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "TopBlurAlpha",
    )
}

/**
 * Apple Music-style translucent vertical gradient mask (opacity fade, no text blur distortion).
 * Color-matched to [containerColor] so scrolling content fades smoothly behind system bars.
 */
fun Modifier.nexusEdgeGradientScrim(
    containerColor: Color,
    topToBottom: Boolean,
): Modifier = this.background(
    brush = Brush.verticalGradient(
        colors = if (topToBottom) {
            listOf(
                containerColor.copy(alpha = 0.88f),
                containerColor.copy(alpha = 0.52f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.Transparent,
                containerColor.copy(alpha = 0.52f),
                containerColor.copy(alpha = 0.88f),
            )
        },
    ),
)

/**
 * Selective frosted glass effect strictly for floating pill/orb chrome.
 */
fun Modifier.nexusFloatingGlass(
    hazeState: HazeState,
    containerColor: Color,
): Modifier = this.hazeEffect(
    state = hazeState,
    style = HazeStyle(
        backgroundColor = containerColor.copy(alpha = 0.68f),
        tint = HazeTint(containerColor.copy(alpha = 0.18f)),
        blurRadius = 26.dp,
    ),
)

/**
 * Subtle directional glass rim border matching Apple Music's understated frosted chrome.
 */
fun nexusSpecularBorder(isDark: Boolean, widthDp: Float = 0.85f): BorderStroke = BorderStroke(
    width = widthDp.dp,
    brush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.08f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.55f),
                Color.Black.copy(alpha = 0.05f),
                Color.Black.copy(alpha = 0.09f),
            )
        },
    ),
)

/**
 * Whisper-subtle top-edge sheen inside glass pills (barely perceptible, never milky).
 */
fun Modifier.nexusSpecularSheen(isDark: Boolean): Modifier = this.background(
    brush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.035f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.Transparent,
            )
        },
    ),
)
