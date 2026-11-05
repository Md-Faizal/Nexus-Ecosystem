package com.nexus.portal.ui.theme

import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF08080C)
val SurfaceCardDark = Color(0xFF12121A)
val SurfaceCardBorder = Color(0x26FFFFFF)

val TextPrimary = Color(0xFFE2E2E9)
val TextSecondary = Color(0xFF8A8A93)

// Premium Light Theme Palette
val BackgroundLight = Color(0xFFF4F6F9)       // Sleek light grey-blue backdrop
val SurfaceCardLight = Color(0xFFFFFFFF)      // Pure white card surfaces
val SurfaceCardBorderLight = Color(0x1F000000) // Soft dark-aligned borders

val TextPrimaryLight = Color(0xFF1A1A1E)      // Charcoal/slate text
val TextSecondaryLight = Color(0xFF6E6E73)    // Muted grey text

fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color(0xFF00FFCC) // Default Cyan Neon
    }
}
