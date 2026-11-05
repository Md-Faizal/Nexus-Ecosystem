package com.nexus.portal.data.local.timeline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a single background-tracked GPS location point in the user's daily timeline.
 * Each point is eventually geocoded to a human-readable place label.
 */
@Entity(
    tableName = "timeline_points",
    indices = [Index(value = ["timestamp"])]
)
data class TimelineLocationPoint(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speedMetersPerSec: Float = 0f,
    val timestamp: Long,                    // epoch millis — when the point was recorded
    val placeLabel: String = "",            // geocoded address/place name, filled asynchronously
    val placeSubLabel: String = "",         // e.g. neighbourhood / city
    val placeCategoryIcon: String = "location_on", // Material icon name hint: home/work/restaurant/etc.
    val isSignificantPlace: Boolean = false, // true if device stayed at this location for >5 min in 50m radius
    val stayDurationMs: Long = 0L,          // how long the user stayed at this location (for significant places)
    val isSynced: Boolean = false,           // whether this point has been uploaded to cloud backend
    
    // IMU Sensor values
    val accelX: Double = 0.0,
    val accelY: Double = 0.0,
    val accelZ: Double = 0.0,
    val gyroX: Double = 0.0,
    val gyroY: Double = 0.0,
    val gyroZ: Double = 0.0,
    val magX: Double = 0.0,
    val magY: Double = 0.0,
    val magZ: Double = 0.0,
    
    // Fused Orientation values
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    
    // Activity recognition values
    val detectedActivity: String = "UNKNOWN",
    val activityConfidence: Int = 0,
    
    // Microphone capture values
    val audioLoudnessDb: Double = 0.0,
    val audioFileUrl: String = ""
)
