package com.nexus.portal.ui.screens.hub.apps.batterymonitor

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class BatteryPoint(val timestamp: Long, val mA: Int)

enum class SessionType { CHARGE, DISCHARGE }

data class BatterySession(
    val id: String,
    val type: SessionType,
    val startPercent: Int,
    val endPercent: Int,
    val startTime: Long,
    val endTime: Long,
    val pluggedState: String
)

object BatteryRepository {
    private val _liveData = MutableStateFlow<List<BatteryPoint>>(emptyList())
    val liveData: StateFlow<List<BatteryPoint>> = _liveData.asStateFlow()

    private val _sessions = MutableStateFlow<List<BatterySession>>(emptyList())
    val sessions: StateFlow<List<BatterySession>> = _sessions.asStateFlow()

    private val _currentMa = MutableStateFlow(0)
    val currentMa: StateFlow<Int> = _currentMa.asStateFlow()

    private val _activeSession = MutableStateFlow<BatterySession?>(null)
    val activeSession: StateFlow<BatterySession?> = _activeSession.asStateFlow()

    private const val MAX_HISTORY_POINTS = 7200 // 2 hours if 1 point per second
    private const val PREFS_NAME = "battery_monitor_prefs"
    private const val KEY_SESSIONS = "battery_sessions"
    private const val KEY_LIVE_DATA = "battery_live_data"
    
    private var applicationContext: Context? = null
    private var lastSaveTime = 0L

    fun init(context: Context) {
        applicationContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSessions(prefs)
        loadLiveData(prefs)
    }

    fun addPoint(mA: Int) {
        _currentMa.value = mA
        val now = System.currentTimeMillis()
        val currentList = _liveData.value.toMutableList()
        currentList.add(BatteryPoint(now, mA))
        
        // Trim history to 2 hours
        val twoHoursAgo = now - (2 * 60 * 60 * 1000)
        val filteredList = currentList.filter { it.timestamp > twoHoursAgo }
        _liveData.value = filteredList
        
        if (now - lastSaveTime > 30_000) {
            lastSaveTime = now
            applicationContext?.let { saveLiveData(it, filteredList) }
        }
    }

    fun updateActiveSession(session: BatterySession?) {
        _activeSession.value = session
    }

    fun addSession(context: Context, session: BatterySession) {
        val currentList = _sessions.value.toMutableList()
        currentList.add(0, session) // Add to top
        // Keep last 20 sessions
        val trimmedList = currentList.take(20)
        _sessions.value = trimmedList
        
        saveSessions(context, trimmedList)
    }

    private fun loadSessions(prefs: SharedPreferences) {
        val jsonStr = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<BatterySession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BatterySession(
                        id = obj.getString("id"),
                        type = SessionType.valueOf(obj.getString("type")),
                        startPercent = obj.getInt("startPercent"),
                        endPercent = obj.getInt("endPercent"),
                        startTime = obj.getLong("startTime"),
                        endTime = obj.getLong("endTime"),
                        pluggedState = obj.getString("pluggedState")
                    )
                )
            }
            _sessions.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSessions(context: Context, list: List<BatterySession>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            list.forEach { session ->
                val obj = JSONObject()
                obj.put("id", session.id)
                obj.put("type", session.type.name)
                obj.put("startPercent", session.startPercent)
                obj.put("endPercent", session.endPercent)
                obj.put("startTime", session.startTime)
                obj.put("endTime", session.endTime)
                obj.put("pluggedState", session.pluggedState)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadLiveData(prefs: SharedPreferences) {
        val jsonStr = prefs.getString(KEY_LIVE_DATA, "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<BatteryPoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(BatteryPoint(obj.getLong("t"), obj.getInt("ma")))
            }
            _liveData.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLiveData(context: Context, list: List<BatteryPoint>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            list.forEach { point ->
                val obj = JSONObject()
                obj.put("t", point.timestamp)
                obj.put("ma", point.mA)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_LIVE_DATA, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearLiveData() {
        _liveData.value = emptyList()
        applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_LIVE_DATA)?.apply()
    }
}
