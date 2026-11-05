package com.nexus.portal.data.local.ridetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    // Only show non-deleted vehicles
    @Query("SELECT * FROM vehicles WHERE isDeleted = 0")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE isDeleted = 0")
    suspend fun getAllVehiclesSync(): List<VehicleEntity>

    // Include deleted in sync upload
    @Query("SELECT * FROM vehicles")
    fun getAllVehiclesIncludingDeleted(): Flow<List<VehicleEntity>>

    @Query("SELECT lastUpdated FROM vehicles WHERE id = :id")
    suspend fun getVehicleLastUpdated(id: String): Long?

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicles(vehicles: List<VehicleEntity>)

    // Soft delete
    @Query("UPDATE vehicles SET isDeleted = 1, lastUpdated = :now WHERE id = :id")
    suspend fun deleteVehicle(id: String, now: Long = System.currentTimeMillis())
}
