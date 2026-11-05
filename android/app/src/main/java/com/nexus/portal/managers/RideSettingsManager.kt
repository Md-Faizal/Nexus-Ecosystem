package com.nexus.portal.managers

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RideSettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ride_tracker_settings", Context.MODE_PRIVATE)

    private val _isMetric = MutableStateFlow(prefs.getBoolean("isMetric", true))
    val isMetric: StateFlow<Boolean> = _isMetric.asStateFlow()

    private val _recordGForce = MutableStateFlow(prefs.getBoolean("recordGForce", true))
    val recordGForce: StateFlow<Boolean> = _recordGForce.asStateFlow()

    private val _recordLean = MutableStateFlow(prefs.getBoolean("recordLean", true))
    val recordLean: StateFlow<Boolean> = _recordLean.asStateFlow()

    private val _autoCenterMap = MutableStateFlow(prefs.getBoolean("autoCenterMap", true))
    val autoCenterMap: StateFlow<Boolean> = _autoCenterMap.asStateFlow()

    private val _showKilometerMarkers = MutableStateFlow(prefs.getBoolean("showKilometerMarkers", true))
    val showKilometerMarkers: StateFlow<Boolean> = _showKilometerMarkers.asStateFlow()

    private val _enableAutoSync = MutableStateFlow(prefs.getBoolean("enableAutoSync", true))
    val enableAutoSync: StateFlow<Boolean> = _enableAutoSync.asStateFlow()

    private val _gForceSensitivity = MutableStateFlow(prefs.getFloat("gForceSensitivity", 1.0f))
    val gForceSensitivity: StateFlow<Float> = _gForceSensitivity.asStateFlow()

    private val _participateInLeaderboard = MutableStateFlow(prefs.getBoolean("participateInLeaderboard", false))
    val participateInLeaderboard: StateFlow<Boolean> = _participateInLeaderboard.asStateFlow()

    private val _defaultRideVisibilityPublic = MutableStateFlow(prefs.getBoolean("defaultRideVisibilityPublic", false))
    val defaultRideVisibilityPublic: StateFlow<Boolean> = _defaultRideVisibilityPublic.asStateFlow()

    fun setMetric(value: Boolean) {
        prefs.edit().putBoolean("isMetric", value).apply()
        _isMetric.value = value
    }

    fun setRecordGForce(value: Boolean) {
        prefs.edit().putBoolean("recordGForce", value).apply()
        _recordGForce.value = value
    }

    fun setRecordLean(value: Boolean) {
        prefs.edit().putBoolean("recordLean", value).apply()
        _recordLean.value = value
    }

    fun setAutoCenterMap(value: Boolean) {
        prefs.edit().putBoolean("autoCenterMap", value).apply()
        _autoCenterMap.value = value
    }

    fun setShowKilometerMarkers(value: Boolean) {
        prefs.edit().putBoolean("showKilometerMarkers", value).apply()
        _showKilometerMarkers.value = value
    }

    fun setEnableAutoSync(value: Boolean) {
        prefs.edit().putBoolean("enableAutoSync", value).apply()
        _enableAutoSync.value = value
    }

    fun setGForceSensitivity(value: Float) {
        prefs.edit().putFloat("gForceSensitivity", value).apply()
        _gForceSensitivity.value = value
    }

    fun setParticipateInLeaderboard(value: Boolean) {
        prefs.edit().putBoolean("participateInLeaderboard", value).apply()
        _participateInLeaderboard.value = value
    }

    fun setDefaultRideVisibilityPublic(value: Boolean) {
        prefs.edit().putBoolean("defaultRideVisibilityPublic", value).apply()
        _defaultRideVisibilityPublic.value = value
    }
    
    // For syncing
    fun getSettingsJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("isMetric", _isMetric.value)
            put("recordGForce", _recordGForce.value)
            put("recordLean", _recordLean.value)
            put("autoCenterMap", _autoCenterMap.value)
            put("showKilometerMarkers", _showKilometerMarkers.value)
            put("enableAutoSync", _enableAutoSync.value)
            put("gForceSensitivity", _gForceSensitivity.value)
            put("participateInLeaderboard", _participateInLeaderboard.value)
            put("defaultRideVisibilityPublic", _defaultRideVisibilityPublic.value)
        }
    }
    private fun parseBool(json: org.json.JSONObject, key: String): Boolean? {
        if (!json.has(key) || json.isNull(key)) return null
        val v = json.get(key)
        if (v is Int) return v == 1
        if (v is Boolean) return v
        return null
    }

    fun updateSettingsFromJson(json: org.json.JSONObject) {
        parseBool(json, "isMetric")?.let { setMetric(it) }
        parseBool(json, "recordGForce")?.let { setRecordGForce(it) }
        parseBool(json, "recordLean")?.let { setRecordLean(it) }
        parseBool(json, "autoCenterMap")?.let { setAutoCenterMap(it) }
        parseBool(json, "showKilometerMarkers")?.let { setShowKilometerMarkers(it) }
        parseBool(json, "enableAutoSync")?.let { setEnableAutoSync(it) }
        parseBool(json, "participateInLeaderboard")?.let { setParticipateInLeaderboard(it) }
        parseBool(json, "defaultRideVisibilityPublic")?.let { setDefaultRideVisibilityPublic(it) }
        if (json.has("gForceSensitivity") && !json.isNull("gForceSensitivity")) {
            try {
                setGForceSensitivity(json.getDouble("gForceSensitivity").toFloat())
            } catch (_: Exception) {}
        }
    }
    companion object {
        @Volatile
        private var INSTANCE: RideSettingsManager? = null

        fun getInstance(context: Context): RideSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RideSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
