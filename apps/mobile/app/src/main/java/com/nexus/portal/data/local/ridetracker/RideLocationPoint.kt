package com.nexus.portal.data.local.ridetracker

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ride_location_points",
    foreignKeys = [
        ForeignKey(
            entity = RideSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class RideLocationPoint(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speedMetersPerSec: Float,
    val leanAngle: Float,
    val gForce: Float,
    val accelLateral: Float = 0f,
    val accelLongitudinal: Float = 0f,
    val timestamp: Long
)
