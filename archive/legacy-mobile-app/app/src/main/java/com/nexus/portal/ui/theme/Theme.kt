package com.nexus.portal.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nexus.portal.data.repository.ThemeMode
import com.nexus.portal.data.repository.ContentTheme
import dev.chrisbanes.haze.HazeState
import com.kyant.backdrop.backdrops.LayerBackdrop
// Structure holding dynamic custom layout color tokens
data class PortalColors(
    val primary: Color,
    val background: Color,
    val surfaceCard: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val gridColor: Color
)

// Global CompositionLocal provider hook for custom semantic color tokens
val LocalPortalColors = staticCompositionLocalOf {
    PortalColors(
        primary = Color(0xFF00FFCC),
        background = Color(0xFF08080C),
        surfaceCard = Color(0xFF12121A),
        border = Color(0x26FFFFFF),
        textPrimary = Color(0xFFE2E2E9),
        textSecondary = Color(0xFF8A8A93),
        gridColor = Color(0x08FFFFFF)
    )
}

val LocalContentTheme = staticCompositionLocalOf {
    ContentTheme.STANDARD
}

val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalBackgroundBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
// Ease of access object
object PortalTheme {
    val colors: PortalColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPortalColors.current

    val contentTheme: ContentTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalContentTheme.current
}

@Composable
fun NexusPortalTheme(
    profileColorHex: String,
    themeMode: ThemeMode,
    contentTheme: ContentTheme,
    isLiquidGlassEnabled: Boolean = false,
    hazeState: HazeState? = null,
    appBackdrop: LayerBackdrop? = null,
    backgroundBackdrop: LayerBackdrop? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Determine target primary accent color based on theme overrides
    val targetPrimary = when (contentTheme) {
        ContentTheme.SNOOPY -> Color(0xFFFFB800)      // Woodstock yellow/gold accent
        ContentTheme.DARTH_VADER -> Color(0xFFFF0D0D)  // Sith glowing laser red accent
        ContentTheme.STANDARD -> profileColorHex.toColor() // Dynamic profile color
    }

    // Dynamic target assignments matching light vs dark and theme specs
    val targetBg = when (contentTheme) {
        ContentTheme.SNOOPY -> if (isDark) Color(0xFF1E1C1A) else Color(0xFFFFFDF5)
        ContentTheme.DARTH_VADER -> if (isDark) Color(0xFF030303) else Color(0xFFE2E4E9)
        ContentTheme.STANDARD -> if (isDark) BackgroundDark else BackgroundLight
    }

    val targetSurface = when (contentTheme) {
        ContentTheme.SNOOPY -> if (isDark) Color(0xFF2B2824) else Color(0xFFFFFFFF)
        ContentTheme.DARTH_VADER -> if (isDark) Color(0xFF0F0F12) else Color(0xFFFFFFFF)
        ContentTheme.STANDARD -> if (isDark) SurfaceCardDark else SurfaceCardLight
    }

    val targetBorder = when (contentTheme) {
        ContentTheme.SNOOPY -> if (isDark) Color(0x22FFB800) else Color(0x1F6B4C00)
        ContentTheme.DARTH_VADER -> if (isDark) Color(0x3DFF0D0D) else Color(0x26FF0D0D)
        ContentTheme.STANDARD -> if (isDark) SurfaceCardBorder else SurfaceCardBorderLight
    }

    val targetTextPrimary = when (contentTheme) {
        ContentTheme.SNOOPY -> if (isDark) Color(0xFFF7F4EB) else Color(0xFF332A15)
        ContentTheme.DARTH_VADER -> if (isDark) Color(0xFFF5F5FA) else Color(0xFF111115)
        ContentTheme.STANDARD -> if (isDark) TextPrimary else TextPrimaryLight
    }

    val targetTextSecondary = when (contentTheme) {
        ContentTheme.SNOOPY -> if (isDark) Color(0xFF9E9585) else Color(0xFF7A6E55)
        ContentTheme.DARTH_VADER -> if (isDark) Color(0xFF8A8A9E) else Color(0xFF555566)
        ContentTheme.STANDARD -> if (isDark) TextSecondary else TextSecondaryLight
    }

    val targetGridColor = when (contentTheme) {
        ContentTheme.SNOOPY -> if (isDark) Color(0x15FFB800) else Color(0x156B4C00)
        ContentTheme.DARTH_VADER -> if (isDark) Color(0x1AFF0D0D) else Color(0x1AFF0D0D)
        ContentTheme.STANDARD -> if (isDark) Color(0x08FFFFFF) else Color(0x0A000000)
    }

    // Smooth 600ms animated transitions
    val animSpec = tween<Color>(durationMillis = 600, easing = FastOutSlowInEasing)

    val animatedPrimary = animateColorAsState(targetValue = targetPrimary, animationSpec = animSpec, label = "primary")
    val animatedBg = animateColorAsState(targetValue = targetBg, animationSpec = animSpec, label = "bg")
    val animatedSurface = animateColorAsState(targetValue = targetSurface, animationSpec = animSpec, label = "surface")
    val animatedBorder = animateColorAsState(targetValue = targetBorder, animationSpec = animSpec, label = "border")
    val animatedTextPrimary = animateColorAsState(targetValue = targetTextPrimary, animationSpec = animSpec, label = "textPrimary")
    val animatedTextSecondary = animateColorAsState(targetValue = targetTextSecondary, animationSpec = animSpec, label = "textSecondary")
    val animatedGridColor = animateColorAsState(targetValue = targetGridColor, animationSpec = animSpec, label = "gridColor")

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = animatedPrimary.value,
            background = animatedBg.value,
            surface = animatedSurface.value,
            onBackground = animatedTextPrimary.value,
            onSurface = animatedTextPrimary.value
        )
    } else {
        lightColorScheme(
            primary = animatedPrimary.value,
            background = animatedBg.value,
            surface = animatedSurface.value,
            onBackground = animatedTextPrimary.value,
            onSurface = animatedTextPrimary.value
        )
    }

    val portalColors = PortalColors(
        primary = animatedPrimary.value,
        background = animatedBg.value,
        surfaceCard = animatedSurface.value,
        border = animatedBorder.value,
        textPrimary = animatedTextPrimary.value,
        textSecondary = animatedTextSecondary.value,
        gridColor = animatedGridColor.value
    )

    CompositionLocalProvider(
        LocalPortalColors provides portalColors,
        LocalContentTheme provides contentTheme,
        LocalLiquidGlassEnabled provides isLiquidGlassEnabled,
        LocalHazeState provides hazeState,
        LocalAppBackdrop provides appBackdrop,
        LocalBackgroundBackdrop provides backgroundBackdrop
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
