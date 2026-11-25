package com.nexus.portal.data.local.ridetracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "performance_runs")
data class PerformanceRunEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String? = null,
    val vehicleName: String = "Unknown Vehicle",
    val vehicleType: String = "two_wheeler", // "two_wheeler" or "four_wheeler"
    val timestamp: Long = System.currentTimeMillis(),
    
    // Standard Acceleration Times (in seconds)
    val accel0To60Mph: Float? = null,
    val accel0To100Kmh: Float? = null,
    val accel60To130Mph: Float? = null,
    val accel100To200Kmh: Float? = null,
    val accel0To130Mph: Float? = null,
    val accel0To300Kmh: Float? = null,
    
    // Drag Distances (times in seconds, trap speeds in km/h or mph)
    val drag1_8Time: Float? = null,
    val drag1_8TrapSpeedKmh: Float? = null,
    val drag1_4Time: Float? = null,
    val drag1_4TrapSpeedKmh: Float? = null,
    val drag1_2Time: Float? = null,
    val drag1_2TrapSpeedKmh: Float? = null,
    
    // Braking Performance
    val brakingStartSpeedKmh: Float? = null,
    val brakingTime: Float? = null,
    val brakingDistanceMeters: Float? = null,
    
    // G-Forces
    val peakLateralG: Float = 0f,
    val peakLongitudinalG: Float = 0f,
    
    // Lap Times (Laps stored as comma-separated durations or JSON)
    val lapTimesJson: String = "[]", // List of lap times in milliseconds
    
    // Video Path if recorded
    val videoPath: String? = null,
    
    // Telemetry JSON (Time-series points: list of speed, altitude, slope, lat, lng, Gs, elapsed time)
    val telemetryJson: String = "[]",
    
    // Synchronization status
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val profileId: String = "",
    val deviceId: String = ""
)
