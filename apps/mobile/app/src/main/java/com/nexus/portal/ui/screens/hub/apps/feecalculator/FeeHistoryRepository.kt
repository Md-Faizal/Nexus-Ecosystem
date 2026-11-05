package com.nexus.portal.ui.screens.hub.apps.feecalculator

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class FeeCalculationRecord(
    val id: String,
    val timestamp: Long,
    val totalBalance: Double,
    val amountToLeave: Double,
    val feePercent: Double,
    val amountToEnter: Double,
    val feeAmount: Double,
    val totalDeducted: Double
)

object FeeHistoryRepository {
    private val _history = MutableStateFlow<List<FeeCalculationRecord>>(emptyList())
    val history: StateFlow<List<FeeCalculationRecord>> = _history.asStateFlow()

    private const val PREFS_NAME = "fee_calc_prefs"
    private const val KEY_HISTORY = "history_records"
    private const val MAX_RECORDS = 50

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHistory(prefs)
    }

    fun addRecord(context: Context, record: FeeCalculationRecord) {
        val currentList = _history.value.toMutableList()
        currentList.add(0, record)
        val trimmedList = currentList.take(MAX_RECORDS)
        _history.value = trimmedList
        saveHistory(context, trimmedList)
    }

    fun deleteRecord(context: Context, id: String) {
        val currentList = _history.value.toMutableList()
        currentList.removeAll { it.id == id }
        _history.value = currentList
        saveHistory(context, currentList)
    }

    private fun loadHistory(prefs: SharedPreferences) {
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<FeeCalculationRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FeeCalculationRecord(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        totalBalance = obj.getDouble("totalBalance"),
                        amountToLeave = obj.getDouble("amountToLeave"),
                        feePercent = obj.getDouble("feePercent"),
                        amountToEnter = obj.getDouble("amountToEnter"),
                        feeAmount = obj.getDouble("feeAmount"),
                        totalDeducted = obj.getDouble("totalDeducted")
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistory(context: Context, list: List<FeeCalculationRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            list.forEach { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("timestamp", r.timestamp)
                obj.put("totalBalance", r.totalBalance)
                obj.put("amountToLeave", r.amountToLeave)
                obj.put("feePercent", r.feePercent)
                obj.put("amountToEnter", r.amountToEnter)
                obj.put("feeAmount", r.feeAmount)
                obj.put("totalDeducted", r.totalDeducted)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
