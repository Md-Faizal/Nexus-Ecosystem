package com.nexus.portal.ui.screens.hub.apps.electricitybill

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.repository.ProfileRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ElectricityBillRecord(
    val id: String,
    val timestamp: Long,
    val units: Double,
    val loadKw: Double,
    val hasSmartMeter: Boolean,
    val digitalPayment: Boolean,
    val energyCharge: Double,
    val fixedCharge: Double,
    val meterRent: Double,
    val electricityDuty: Double,
    val grossTotal: Double,
    val rebate: Double,
    val netPayable: Double,
    val lastUpdated: Long,
    val isDeleted: Boolean = false
)

object ElectricityBillRepository {
    private val _history = MutableStateFlow<List<ElectricityBillRecord>>(emptyList())
    val history: StateFlow<List<ElectricityBillRecord>> = _history.asStateFlow()

    private const val PREFS_NAME = "electricity_bill_prefs"
    private const val KEY_HISTORY = "history_records"
    private const val MAX_RECORDS = 50

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHistory(prefs)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun addRecord(context: Context, record: ElectricityBillRecord) {
        val currentList = _history.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == record.id }
        if (index != -1) {
            currentList[index] = record
        } else {
            currentList.add(0, record)
        }
        val trimmedList = currentList.take(MAX_RECORDS)
        _history.value = trimmedList
        saveHistory(context, trimmedList)
        
        // Trigger auto-sync backup in the background
        GlobalScope.launch(Dispatchers.IO) {
            try {
                syncWithCloud(context)
            } catch (e: Exception) {
                Log.e("ElectricityBillSync", "Auto-sync failed: ${e.message}")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun deleteRecord(context: Context, id: String) {
        val currentList = _history.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            // Soft delete by marking isDeleted and updating lastUpdated
            val r = currentList[index]
            currentList[index] = r.copy(isDeleted = true, lastUpdated = System.currentTimeMillis())
            _history.value = currentList
            saveHistory(context, currentList)
            
            // Trigger auto-sync in background to delete on backend
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    syncWithCloud(context)
                } catch (e: Exception) {
                    Log.e("ElectricityBillSync", "Auto-sync deletion failed: ${e.message}")
                }
            }
        }
    }

    // Industrial Standard Auto Sync Dual-Way Reconciliation
    suspend fun syncWithCloud(context: Context): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileRepository.activeProfile.value.id
            val deviceId = ProfileRepository.deviceId

            if (profileId.isEmpty()) {
                return@withContext Pair(false, "No active profile session")
            }

            // 1. Fetch from cloud first to reconcile changes
            val fetchUrl = URL("${NetworkConfig.electricityBillSyncUrl}?profileId=$profileId")
            val fetchConnection = fetchUrl.openConnection() as HttpURLConnection
            fetchConnection.requestMethod = "GET"
            fetchConnection.connectTimeout = 8000
            fetchConnection.readTimeout = 8000

            val cloudRecords = mutableListOf<ElectricityBillRecord>()
            if (fetchConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(fetchConnection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                fetchConnection.disconnect()

                val json = JSONObject(response.toString())
                if (json.optBoolean("success", false)) {
                    val arr = json.getJSONArray("records")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        cloudRecords.add(
                            ElectricityBillRecord(
                                id = obj.getString("id"),
                                timestamp = obj.getLong("timestamp"),
                                units = obj.getDouble("units"),
                                loadKw = obj.getDouble("loadKw"),
                                hasSmartMeter = obj.getBoolean("hasSmartMeter"),
                                digitalPayment = obj.getBoolean("digitalPayment"),
                                energyCharge = obj.getDouble("energyCharge"),
                                fixedCharge = obj.getDouble("fixedCharge"),
                                meterRent = obj.getDouble("meterRent"),
                                electricityDuty = obj.getDouble("electricityDuty"),
                                grossTotal = obj.getDouble("grossTotal"),
                                rebate = obj.getDouble("rebate"),
                                netPayable = obj.getDouble("netPayable"),
                                lastUpdated = obj.getLong("lastUpdated"),
                                isDeleted = obj.getBoolean("isDeleted")
                            )
                        )
                    }
                }
            } else {
                fetchConnection.disconnect()
            }

            // 2. Reconcile records locally (LWW strategy)
            val localList = _history.value.toMutableList()
            var localChanged = false

            cloudRecords.forEach { cloudRec ->
                val localIndex = localList.indexOfFirst { it.id == cloudRec.id }
                if (localIndex == -1) {
                    // Entirely new record found in cloud
                    localList.add(cloudRec)
                    localChanged = true
                } else {
                    val localRec = localList[localIndex]
                    if (cloudRec.lastUpdated > localRec.lastUpdated) {
                        // Overwrite with newer cloud details
                        localList[localIndex] = cloudRec
                        localChanged = true
                    }
                }
            }

            if (localChanged) {
                val sortedAndTrimmed = localList.sortedByDescending { it.timestamp }.take(MAX_RECORDS)
                _history.value = sortedAndTrimmed
                saveHistory(context, sortedAndTrimmed)
            }

            // 3. Upload fully merged list back to cloud
            val payload = JSONObject()
            payload.put("profileId", profileId)
            payload.put("deviceId", deviceId)

            val recordsArr = JSONArray()
            _history.value.forEach { r ->
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("timestamp", r.timestamp)
                    put("units", r.units)
                    put("loadKw", r.loadKw)
                    put("hasSmartMeter", r.hasSmartMeter)
                    put("digitalPayment", r.digitalPayment)
                    put("energyCharge", r.energyCharge)
                    put("fixedCharge", r.fixedCharge)
                    put("meterRent", r.meterRent)
                    put("electricityDuty", r.electricityDuty)
                    put("grossTotal", r.grossTotal)
                    put("rebate", r.rebate)
                    put("netPayable", r.netPayable)
                    put("lastUpdated", r.lastUpdated)
                    put("isDeleted", r.isDeleted)
                }
                recordsArr.put(obj)
            }
            payload.put("records", recordsArr)

            val pushUrl = URL(NetworkConfig.electricityBillSyncUrl)
            val pushConnection = pushUrl.openConnection() as HttpURLConnection
            pushConnection.requestMethod = "POST"
            pushConnection.doOutput = true
            pushConnection.setRequestProperty("Content-Type", "application/json")
            pushConnection.connectTimeout = 8000
            pushConnection.readTimeout = 8000

            OutputStreamWriter(pushConnection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = pushConnection.responseCode
            pushConnection.disconnect()

            if (code == HttpURLConnection.HTTP_OK) {
                Log.d("ElectricityBillSync", "Cloud reconciliation sync complete")
                return@withContext Pair(true, null)
            } else {
                Log.e("ElectricityBillSync", "Cloud sync push failed: $code")
                return@withContext Pair(false, "Push failed: HTTP $code")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ElectricityBillSync", "Sync execution exception: ${e.message}")
            return@withContext Pair(false, e.message ?: "Connection Exception")
        }
    }

    private fun loadHistory(prefs: SharedPreferences) {
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ElectricityBillRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ElectricityBillRecord(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        units = obj.getDouble("units"),
                        loadKw = obj.getDouble("loadKw"),
                        hasSmartMeter = obj.getBoolean("hasSmartMeter"),
                        digitalPayment = obj.getBoolean("digitalPayment"),
                        energyCharge = obj.optDouble("energyCharge", 0.0),
                        fixedCharge = obj.optDouble("fixedCharge", 0.0),
                        meterRent = obj.optDouble("meterRent", 0.0),
                        electricityDuty = obj.optDouble("electricityDuty", 0.0),
                        grossTotal = obj.optDouble("grossTotal", 0.0),
                        rebate = obj.optDouble("rebate", 0.0),
                        netPayable = obj.getDouble("netPayable"),
                        lastUpdated = obj.optLong("lastUpdated", obj.getLong("timestamp")),
                        isDeleted = obj.optBoolean("isDeleted", false)
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistory(context: Context, list: List<ElectricityBillRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray()
            list.forEach { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("timestamp", r.timestamp)
                obj.put("units", r.units)
                obj.put("loadKw", r.loadKw)
                obj.put("hasSmartMeter", r.hasSmartMeter)
                obj.put("digitalPayment", r.digitalPayment)
                obj.put("energyCharge", r.energyCharge)
                obj.put("fixedCharge", r.fixedCharge)
                obj.put("meterRent", r.meterRent)
                obj.put("electricityDuty", r.electricityDuty)
                obj.put("grossTotal", r.grossTotal)
                obj.put("rebate", r.rebate)
                obj.put("netPayable", r.netPayable)
                obj.put("lastUpdated", r.lastUpdated)
                obj.put("isDeleted", r.isDeleted)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
