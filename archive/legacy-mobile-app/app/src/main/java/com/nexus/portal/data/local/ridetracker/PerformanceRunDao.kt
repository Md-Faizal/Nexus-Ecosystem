package com.nexus.portal.data.local.ridetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceRunDao {
    @Query("SELECT * FROM performance_runs WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllRuns(): Flow<List<PerformanceRunEntity>>

    @Query("SELECT * FROM performance_runs ORDER BY timestamp DESC")
    fun getAllRunsIncludingDeleted(): Flow<List<PerformanceRunEntity>>

    @Query("SELECT * FROM performance_runs WHERE id = :id LIMIT 1")
    suspend fun getRunById(id: String): PerformanceRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: PerformanceRunEntity)

    @Update
    suspend fun updateRun(run: PerformanceRunEntity)

    @androidx.room.Transaction
    suspend fun upsertRun(run: PerformanceRunEntity) {
        val existing = getRunById(run.id)
        if (existing != null) {
            updateRun(run)
        } else {
            insertRun(run)
        }
    }

    @androidx.room.Transaction
    suspend fun upsertRuns(runs: List<PerformanceRunEntity>) {
        runs.forEach { upsertRun(it) }
    }

    @Query("UPDATE performance_runs SET isDeleted = 1, isSynced = 0, lastUpdated = :now WHERE id = :id")
    suspend fun deleteRunById(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT MAX(lastUpdated) FROM performance_runs")
    suspend fun getMaxLastUpdated(): Long?

    @Query("SELECT lastUpdated FROM performance_runs WHERE id = :id")
    suspend fun getRunLastUpdated(id: String): Long?

    @Query("SELECT * FROM performance_runs WHERE isSynced = 0")
    suspend fun getUnsyncedRuns(): List<PerformanceRunEntity>

    @Query("UPDATE performance_runs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
