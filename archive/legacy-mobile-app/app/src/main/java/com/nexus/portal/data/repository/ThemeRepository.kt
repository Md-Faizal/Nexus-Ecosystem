package com.nexus.portal.data.repository

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

enum class ContentTheme {
    STANDARD, SNOOPY, DARTH_VADER
}

object ThemeRepository {
    private const val PREFS_NAME = "nexus_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_CONTENT_THEME = "content_theme"
    private const val KEY_LIQUID_GLASS = "liquid_glass_enabled"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _contentTheme = MutableStateFlow(ContentTheme.STANDARD)
    val contentTheme: StateFlow<ContentTheme> = _contentTheme.asStateFlow()

    private val _isLiquidGlassEnabled = MutableStateFlow(true)
    val isLiquidGlassEnabled: StateFlow<Boolean> = _isLiquidGlassEnabled.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val savedMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _themeMode.value = try {
            ThemeMode.valueOf(savedMode)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val savedContent = prefs.getString(KEY_CONTENT_THEME, ContentTheme.STANDARD.name) ?: ContentTheme.STANDARD.name
        _contentTheme.value = try {
            ContentTheme.valueOf(savedContent)
        } catch (e: Exception) {
            ContentTheme.STANDARD
        }

        _isLiquidGlassEnabled.value = prefs.getBoolean(KEY_LIQUID_GLASS, true)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setContentTheme(context: Context, theme: ContentTheme) {
        _contentTheme.value = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONTENT_THEME, theme.name).apply()
        
        // Trigger live tone feedback based on selection
        triggerThemeSound(theme)
    }

    fun setLiquidGlassEnabled(context: Context, isEnabled: Boolean) {
        _isLiquidGlassEnabled.value = isEnabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIQUID_GLASS, isEnabled).apply()
    }

    private fun triggerThemeSound(theme: ContentTheme) {
        Thread {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                when (theme) {
                    ContentTheme.STANDARD -> {
                        // Quick high clean cyber bleep
                        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 80)
                    }
                    ContentTheme.SNOOPY -> {
                        // Cheery cartoon double-bounce chirp
                        toneGen.startTone(ToneGenerator.TONE_SUP_PIP, 80)
                        Thread.sleep(120)
                        toneGen.startTone(ToneGenerator.TONE_SUP_PIP, 100)
                    }
                    ContentTheme.DARTH_VADER -> {
                        // Deep sith threat-level low frequency warnings (sound of a respirator alarm)
                        toneGen.startTone(ToneGenerator.TONE_CDMA_LOW_L, 200)
                        Thread.sleep(250)
                        toneGen.startTone(ToneGenerator.TONE_CDMA_LOW_L, 250)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
