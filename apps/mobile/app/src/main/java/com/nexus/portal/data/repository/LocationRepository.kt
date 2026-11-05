package com.nexus.portal.data.repository

import android.location.Location
import kotlinx.coroutines.flow.Flow
import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Industrial Standard Location Repository using Kotlin Flows.
 * Abstracts the Android LocationManager away from services and viewmodels.
 */
interface LocationRepository {
    val locationFlow: Flow<Location>
    fun startTracking(intervalMs: Long = 1000L, priority: Int = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, minDistanceMeters: Float = 1f)
    fun stopTracking()

    // New client-scoped APIs: allow multiple consumers to request different tracking
    // characteristics without stomping each other. Each client must provide a unique
    // `clientId` and call `stopTrackingClient(clientId)` when done.
    fun startTrackingClient(clientId: String, intervalMs: Long = 1000L, priority: Int = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, minDistanceMeters: Float = 1f)
    fun stopTrackingClient(clientId: String)
}



class DefaultLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var isTrackingEnabled = false

    private val _locationFlow = kotlinx.coroutines.flow.MutableSharedFlow<Location>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val locationFlow: Flow<Location> = _locationFlow.asSharedFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _locationFlow.tryEmit(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(intervalMs: Long, priority: Int, minDistanceMeters: Float) {
        // Backwards-compat convenience: register as a single shared legacy client
        startTrackingClient("legacy", intervalMs, priority, minDistanceMeters)
    }

    override fun stopTracking() {
        stopTrackingClient("legacy")
    }

    private data class RequestSpec(val intervalMs: Long, val priority: Int, val minDistance: Float)

    private val clientRequests = mutableMapOf<String, RequestSpec>()
    private var mergedRequestSpec: RequestSpec? = null

    @Synchronized
    override fun startTrackingClient(clientId: String, intervalMs: Long, priority: Int, minDistanceMeters: Float) {
        clientRequests[clientId] = RequestSpec(intervalMs, priority, minDistanceMeters)
        recomputeAndApply()
    }

    @Synchronized
    override fun stopTrackingClient(clientId: String) {
        clientRequests.remove(clientId)
        recomputeAndApply()
    }

    @Synchronized
    private fun recomputeAndApply() {
        if (clientRequests.isEmpty()) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) { }
            mergedRequestSpec = null
            return
        }

        // Choose the most aggressive (smallest interval), highest accuracy (prefer HIGH_ACCURACY), and smallest minDistance
        val interval = clientRequests.values.minOf { it.intervalMs }
        val minDistance = clientRequests.values.minOf { it.minDistance }
        val priority = when {
            clientRequests.values.any { it.priority == Priority.PRIORITY_HIGH_ACCURACY } -> Priority.PRIORITY_HIGH_ACCURACY
            clientRequests.values.any { it.priority == Priority.PRIORITY_BALANCED_POWER_ACCURACY } -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> clientRequests.values.first().priority
        }

        val spec = RequestSpec(interval, priority, minDistance)
        if (spec == mergedRequestSpec) return
        mergedRequestSpec = spec

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { }

        val request = LocationRequest.Builder(spec.priority, spec.intervalMs)
            .setMinUpdateDistanceMeters(spec.minDistance)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }
}
