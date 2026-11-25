package com.nexus.portal.ui.screens.hub.apps.ridetracker

import com.mappls.sdk.maps.geometry.LatLng
import com.nexus.portal.data.local.ridetracker.RideLocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MapMathHelper {
    
    /**
     * Finds the closest point in the track to the touched Map location.
     * Uses simple Euclidean distance on coordinates to avoid heavy Map projection calls.
     * Runs on Dispatchers.Default for performance.
     */
    suspend fun findClosestPoint(
        touchLatLng: LatLng,
        points: List<RideLocationPoint>
    ): RideLocationPoint? = withContext(Dispatchers.Default) {
        if (points.isEmpty()) return@withContext null
        
        var closestPt: RideLocationPoint? = null
        var minDistSq = Double.MAX_VALUE
        
        for (pt in points) {
            val dx = pt.longitude - touchLatLng.longitude
            val dy = pt.latitude - touchLatLng.latitude
            val distSq = dx * dx + dy * dy
            if (distSq < minDistSq) {
                minDistSq = distSq
                closestPt = pt
            }
        }
        
        // Return null if it's wildly far away (e.g., ~1km equivalent depending on latitude)
        if (minDistSq > 0.0001) return@withContext null
        
        return@withContext closestPt
    }
}
