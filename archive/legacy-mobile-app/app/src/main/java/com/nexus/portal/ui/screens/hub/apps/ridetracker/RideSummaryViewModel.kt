package com.nexus.portal.ui.screens.hub.apps.ridetracker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.local.ridetracker.RideDatabase
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import com.nexus.portal.data.local.ridetracker.RideSession
import com.nexus.portal.data.local.ridetracker.VehicleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class RideSummaryUiState(
    val isLoading: Boolean = true,
    val session: RideSession? = null,
    val points: List<RideLocationPoint> = emptyList(),
    val isOwnRide: Boolean = true,
    val sessionVehicle: VehicleEntity? = null,
    val error: String? = null
)

@HiltViewModel
class RideSummaryViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RideSummaryUiState())
    val uiState: StateFlow<RideSummaryUiState> = _uiState.asStateFlow()

    fun loadSession(sessionId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val dao = RideDatabase.getDatabase(context).rideDao()
            val vehicleDao = RideDatabase.getDatabase(context).vehicleDao()

            val targetSession = withContext(Dispatchers.IO) {
                if (sessionId != null) {
                    dao.getSessionById(sessionId)
                } else {
                    dao.getLatestSession()
                }
            }

            if (targetSession != null) {
                var vehicle: VehicleEntity? = null
                if (targetSession.vehicleId != null) {
                    vehicle = withContext(Dispatchers.IO) {
                        vehicleDao.getVehicleById(targetSession.vehicleId)
                    }
                }
                
                withContext(Dispatchers.IO) {
                    dao.getLocationsForSession(targetSession.id).collect { points ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            session = targetSession,
                            points = points,
                            isOwnRide = true,
                            sessionVehicle = vehicle
                        )
                    }
                }
            } else if (sessionId != null) {
                // Fetch from cloud
                val cloudState = fetchSharedSession(sessionId)
                _uiState.value = cloudState ?: _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load session."
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "No session found.")
            }
        }
    }

    private suspend fun fetchSharedSession(sessionId: String): RideSummaryUiState? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${NetworkConfig.backendUrl}/api/ridetracker/shared/$sessionId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optBoolean("success", false)) {
                    val sObj = json.getJSONObject("session")
                    val parsedSession = RideSession(
                        id = sObj.getString("id"),
                        startTime = sObj.optLong("startTime"),
                        endTime = if (sObj.isNull("endTime")) null else sObj.optLong("endTime"),
                        totalDistanceMeters = sObj.optDouble("totalDistanceMeters").toFloat(),
                        maxSpeedKmh = sObj.optDouble("maxSpeedKmh").toFloat(),
                        averageSpeedKmh = sObj.optDouble("averageSpeedKmh").toFloat(),
                        maxLeanAngle = sObj.optDouble("maxLeanAngle").toFloat(),
                        peakLatG = sObj.optDouble("peakLatG").toFloat(),
                        flowRating = sObj.optInt("flowRating"),
                        vehicleType = sObj.optString("vehicleType"),
                        deviceName = sObj.optString("deviceName", "Unknown Device"),
                        vehicleId = if (sObj.isNull("vehicleId")) null else sObj.optString("vehicleId"),
                        isAutoRecorded = sObj.optInt("isAutoRecorded", 0) == 1,
                        connectionLabel = if (sObj.isNull("connectionLabel")) null else sObj.optString("connectionLabel"),
                        lastUpdated = sObj.optLong("lastUpdated", 0L),
                        isDeleted = sObj.optInt("isDeleted", 0) == 1,
                        isPublic = sObj.optInt("isPublic", 1) == 1
                    )

                    val pListObj = json.getJSONArray("points")
                    val parsedPoints = mutableListOf<RideLocationPoint>()
                    for (i in 0 until pListObj.length()) {
                        val pObj = pListObj.getJSONObject(i)
                        parsedPoints.add(
                            RideLocationPoint(
                                id = pObj.getString("id"),
                                sessionId = pObj.getString("sessionId"),
                                latitude = pObj.optDouble("latitude"),
                                longitude = pObj.optDouble("longitude"),
                                altitude = pObj.optDouble("altitude"),
                                speedMetersPerSec = pObj.optDouble("speedMetersPerSec").toFloat(),
                                leanAngle = pObj.optDouble("leanAngle").toFloat(),
                                gForce = pObj.optDouble("gForce").toFloat(),
                                accelLateral = pObj.optDouble("accelLateral").toFloat(),
                                accelLongitudinal = pObj.optDouble("accelLongitudinal").toFloat(),
                                timestamp = pObj.optLong("timestamp")
                            )
                        )
                    }
                    
                    return@withContext RideSummaryUiState(
                        isLoading = false,
                        session = parsedSession,
                        points = parsedPoints,
                        isOwnRide = false
                    )
                }
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    fun togglePublicStatus() {
        val currentSession = _uiState.value.session ?: return
        if (!_uiState.value.isOwnRide) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val updated = currentSession.copy(isPublic = !currentSession.isPublic, lastUpdated = System.currentTimeMillis())
            RideDatabase.getDatabase(context).rideDao().updateSession(updated)
            com.nexus.portal.managers.RideTrackerSyncManager.syncWithCloud(context)
        }
    }
}
