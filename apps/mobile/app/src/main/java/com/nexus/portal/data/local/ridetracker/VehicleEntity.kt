package com.nexus.portal.data.local.ridetracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

@Entity(tableName = "vehicles")
@TypeConverters(VehicleConverters::class)
data class VehicleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // Car, Bike, Scooter
    val make: String,
    val model: String,
    val year: String,
    val licensePlate: String,
    val vin: String,
    val insurancePolicyNumber: String,
    val insuranceDate: Long, // Stored as timestamp
    val pucDate: Long, // Stored as timestamp
    val nextServiceDate: Long, // Stored as timestamp
    val fuelType: String,
    val tankCapacity: String,
    val description: String,
    val color: String,
    val engineCC: String,
    val odometer: String,
    val topSpeed: String,
    val fuelRange: String,
    val maintenanceDue: String, // Legacy/UI placeholder or derived
    val isActive: Boolean,
    val imageUrl: String,
    val originalImageUrl: String = "",
    val statsAvgSpeed: String?,
    val statsLeanAngle: String?,
    val connectedDevicesJson: String,
    val tripHistoryPreviewCount: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val isOccasional: Boolean = false
)

data class ConnectedDeviceData(
    val name: String,
    val status: String,
    val iconType: String,
    val macAddress: String = "",
    val deviceCategory: String = ""
)

class VehicleConverters {
    @TypeConverter
    fun fromConnectedDevicesList(devices: List<ConnectedDeviceData>): String {
        return devices.joinToString(";") { "${it.name}|${it.status}|${it.iconType}|${it.macAddress}|${it.deviceCategory}" }
    }

    @TypeConverter
    fun toConnectedDevicesList(data: String): List<ConnectedDeviceData> {
        if (data.isBlank()) return emptyList()
        return data.split(";").mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 3) {
                ConnectedDeviceData(
                    name = parts[0],
                    status = parts[1],
                    iconType = parts[2],
                    macAddress = parts.getOrNull(3) ?: "",
                    deviceCategory = parts.getOrNull(4) ?: ""
                )
            } else null
        }
    }
}
