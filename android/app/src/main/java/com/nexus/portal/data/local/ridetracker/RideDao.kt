package com.nexus.portal.data.local.ridetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: RideSession)

    @Update
    suspend fun updateSession(session: RideSession)

    @androidx.room.Transaction
    suspend fun upsertSession(session: RideSession) {
        val existing = getSessionById(session.id)
        if (existing != null) {
            updateSession(session)
        } else {
            insertSession(session)
        }
    }

    @androidx.room.Transaction
    suspend fun upsertSessions(sessions: List<RideSession>) {
        sessions.forEach { upsertSession(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocationPoint(point: RideLocationPoint)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocationPoints(points: List<RideLocationPoint>)

    // Only show non-deleted sessions
    @Query("SELECT * FROM ride_sessions WHERE isDeleted = 0 ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RideSession>>

    @Query("SELECT * FROM ride_sessions WHERE isDeleted = 0 AND startTime >= :startOfDay AND startTime <= :endOfDay ORDER BY startTime DESC")
    fun getSessionsBetween(startOfDay: Long, endOfDay: Long): Flow<List<RideSession>>

    @Query("""
        SELECT * FROM ride_sessions 
        WHERE isDeleted = 0 
          AND (:filterVehicle = 0 OR 
               (:filterVehicle = 1 AND vehicleId = :vehicleId) OR 
               (:filterVehicle = 2 AND vehicleId IS NULL))
          AND (:filterDate = 0 OR 
               (startTime >= :startOfDay AND startTime <= :endOfDay))
        ORDER BY startTime DESC
    """)
    fun getSessionsFiltered(
        filterVehicle: Int,
        vehicleId: String?,
        filterDate: Int,
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<RideSession>>

    // Include deleted sessions in sync upload
    @Query("SELECT * FROM ride_sessions ORDER BY startTime DESC")
    fun getAllSessionsIncludingDeleted(): Flow<List<RideSession>>

    @Query("SELECT * FROM ride_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): RideSession?

    @Query("SELECT * FROM ride_sessions WHERE isDeleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(): RideSession?

    @Query("SELECT * FROM ride_location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLocationsListForSession(sessionId: String): List<RideLocationPoint>

    @Query("SELECT lastUpdated FROM ride_sessions WHERE id = :sessionId")
    suspend fun getSessionLastUpdated(sessionId: String): Long?

    @Query("SELECT * FROM ride_location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getLocationsForSession(sessionId: String): Flow<List<RideLocationPoint>>

    @Query("SELECT * FROM ride_sessions WHERE lastUpdated > :timestamp")
    suspend fun getSessionsUpdatedAfter(timestamp: Long): List<RideSession>

    // Soft delete: marks as deleted with new timestamp
    @Query("UPDATE ride_sessions SET isDeleted = 1, lastUpdated = :now WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM ride_location_points WHERE sessionId = :sessionId")
    suspend fun deleteLocationsForSession(sessionId: String)
}
