package com.nexus.portal.ui.liquid

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Data class holding all customisable Liquid Glass effect parameters.
 * The default values match the library defaults but can be overridden via Settings.
 */
data class LiquidGlassConfig(
    var blurRadius: Float = 16f,
    var vibrancyStrength: Float = 0.4f,
    var lensRadiusX: Float = 100f,
    var lensRadiusY: Float = 100f,
    var cornerRadius: Float = 12f,
    var tintColor: Color = Color.White.copy(alpha = 0.15f),
    var enabled: Boolean = true
)

// CompositionLocal that provides the current config to all Liquid Glass components.
val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }
// CompositionLocal that provides a function to update the config
val LocalLiquidGlassConfigUpdater = compositionLocalOf<(LiquidGlassConfig) -> Unit> { {} }

/**
 * Helper composable that reads persisted preferences and provides the config to its content.
 */
@Composable
fun LiquidGlassConfigProvider(
    context: Context,
    content: @Composable () -> Unit
) {
    // Load from SharedPreferences (fallback to defaults)
    val prefs = context.getSharedPreferences("liquid_glass_prefs", Context.MODE_PRIVATE)
    var config by remember {
        mutableStateOf(
            LiquidGlassConfig(
                blurRadius = prefs.getFloat("blurRadius", 16f),
                vibrancyStrength = prefs.getFloat("vibrancyStrength", 0.4f),
                lensRadiusX = prefs.getFloat("lensRadiusX", 100f),
                lensRadiusY = prefs.getFloat("lensRadiusY", 100f),
                cornerRadius = prefs.getFloat("cornerRadius", 12f),
                tintColor = Color(prefs.getInt("tintColor", 0x26FFFFFF)), // ARGB stored as int
                enabled = prefs.getBoolean("enabled", true)
            )
        )
    }

    // Expose a function for child composables to update the config and persist it.
    val updateConfig: (LiquidGlassConfig) -> Unit = { newConfig ->
        config = newConfig
        with(prefs.edit()) {
            putFloat("blurRadius", newConfig.blurRadius)
            putFloat("vibrancyStrength", newConfig.vibrancyStrength)
            putFloat("lensRadiusX", newConfig.lensRadiusX)
            putFloat("lensRadiusY", newConfig.lensRadiusY)
            putFloat("cornerRadius", newConfig.cornerRadius)
            putInt("tintColor", newConfig.tintColor.toArgb())
            putBoolean("enabled", newConfig.enabled)
            apply()
        }
    }

    // Provide the config via CompositionLocal
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiquidGlassConfig provides config,
        LocalLiquidGlassConfigUpdater provides updateConfig
    ) {
        content()
    }
}
