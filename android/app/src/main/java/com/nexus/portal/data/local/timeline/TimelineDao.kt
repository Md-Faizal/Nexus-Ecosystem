package com.nexus.portal.data.local.timeline

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TimelineLocationPoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TimelineLocationPoint>)

    /**
     * Fetch all timeline points for a given day, ordered chronologically.
     * [startMs] = midnight (00:00:00) of the day in epoch millis
     * [endMs]   = end of day (23:59:59.999) in epoch millis
     */
    @Query("SELECT * FROM timeline_points WHERE timestamp >= :startMs AND timestamp < :endMs ORDER BY timestamp ASC")
    fun getPointsForDay(startMs: Long, endMs: Long): Flow<List<TimelineLocationPoint>>

    /**
     * Returns one representative timestamp (first point) per distinct calendar day,
     * so the UI can enumerate all days that have recorded data.
     */
    @Query("SELECT MIN(timestamp) FROM timeline_points GROUP BY (timestamp / 86400000) ORDER BY timestamp DESC")
    fun getDistinctDayTimestamps(): Flow<List<Long>>

    /**
     * All points not yet synced to the cloud backend.
     */
    @Query("SELECT * FROM timeline_points WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT 200")
    suspend fun getUnsynced(): List<TimelineLocationPoint>

    /**
     * Mark a batch of point IDs as successfully synced.
     */
    @Query("UPDATE timeline_points SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    /**
     * Update the geocoded place label for a given point.
     */
    @Query("UPDATE timeline_points SET placeLabel = :label, placeSubLabel = :subLabel, placeCategoryIcon = :icon WHERE id = :id")
    suspend fun updatePlaceLabel(id: String, label: String, subLabel: String, icon: String)

    /**
     * Mark a point as a significant place (user stayed there for a while) and record the stay duration.
     */
    @Query("UPDATE timeline_points SET isSignificantPlace = 1, stayDurationMs = :durationMs WHERE id = :id")
    suspend fun markSignificantPlace(id: String, durationMs: Long)

    /**
     * Data retention: delete points older than [cutoffMs] to avoid unbounded storage growth.
     */
    @Query("DELETE FROM timeline_points WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    /**
     * Total point count — useful for triggering periodic sync.
     */
    @Query("SELECT COUNT(*) FROM timeline_points")
    suspend fun getTotalCount(): Int

    /**
     * Get the most recent recorded point (to compute distance delta for movement detection).
     */
    @Query("SELECT * FROM timeline_points ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPoint(): TimelineLocationPoint?

    /**
     * Get points for a specific day as a plain list (for sync or export operations).
     */
    @Query("SELECT * FROM timeline_points WHERE timestamp >= :startMs AND timestamp < :endMs ORDER BY timestamp ASC")
    suspend fun getPointsForDaySync(startMs: Long, endMs: Long): List<TimelineLocationPoint>
}
