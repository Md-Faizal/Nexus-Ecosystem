package com.nexus.portal.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class UpdateHistoryItem(
    val version: String,
    val timestamp: Long,
    val releaseNotes: String
)

object UpdateHistoryRepository {
    private const val PREFS_NAME = "nexus_update_history"
    private const val KEY_LAST_KNOWN_VERSION_CODE = "last_known_version_code"
    private const val KEY_HISTORY_ARRAY = "history_array"

    private lateinit var prefs: SharedPreferences

    private val _history = MutableStateFlow<List<UpdateHistoryItem>>(emptyList())
    val history: StateFlow<List<UpdateHistoryItem>> = _history.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHistory()
    }

    private fun loadHistory() {
        val raw = prefs.getString(KEY_HISTORY_ARRAY, "[]") ?: "[]"
        val list = mutableListOf<UpdateHistoryItem>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UpdateHistoryItem(
                        version = obj.getString("version"),
                        timestamp = obj.getLong("timestamp"),
                        releaseNotes = obj.getString("releaseNotes")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _history.value = list
    }

    fun getLastKnownVersionCode(): Int {
        return prefs.getInt(KEY_LAST_KNOWN_VERSION_CODE, -1)
    }

    fun registerSuccessfulInstall(versionCode: Int, versionName: String, releaseNotes: String) {
        prefs.edit().putInt(KEY_LAST_KNOWN_VERSION_CODE, versionCode).apply()
        
        val currentList = _history.value.toMutableList()
        // Avoid duplicate logging if already recorded
        if (currentList.none { it.version == versionName }) {
            currentList.add(0, UpdateHistoryItem(versionName, System.currentTimeMillis(), releaseNotes))
            saveHistory(currentList)
        }
    }

    private fun saveHistory(list: List<UpdateHistoryItem>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("version", item.version)
            obj.put("timestamp", item.timestamp)
            obj.put("releaseNotes", item.releaseNotes)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY_ARRAY, jsonArray.toString()).apply()
        _history.value = list
    }
}
