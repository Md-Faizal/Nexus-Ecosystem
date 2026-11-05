package com.nexus.portal.data.local.ridetracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ride_sessions")
data class RideSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long?,
    val totalDistanceMeters: Float,
    val maxSpeedKmh: Float,
    val averageSpeedKmh: Float,
    val maxLeanAngle: Float,
    val peakLatG: Float,
    val flowRating: Int,
    val vehicleType: String, // "two_wheeler" or "four_wheeler"
    val deviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
    val vehicleId: String? = null,
    val connectedMacAddress: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val isAutoRecorded: Boolean = false,
    val connectionLabel: String? = null, // e.g. "Dash Only", "Dash + Helmet", "Helmet Only"
    val isPublic: Boolean = true
)
