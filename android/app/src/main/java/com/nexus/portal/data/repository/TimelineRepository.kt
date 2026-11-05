package com.nexus.portal.data.repository

import com.nexus.portal.data.local.timeline.TimelineDao
import com.nexus.portal.data.local.timeline.TimelineLocationPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface TimelineRepository {
    fun getTimelinePointsForDate(startMs: Long, endMs: Long): Flow<List<TimelineLocationPoint>>
    suspend fun insertPoint(point: TimelineLocationPoint)
    suspend fun deleteOlderThan(timestamp: Long)
    suspend fun markSignificantPlace(id: String, durationMs: Long)
    suspend fun updatePlaceLabel(id: String, label: String, subLabel: String, icon: String)
}

class DefaultTimelineRepository @Inject constructor(
    private val timelineDao: TimelineDao
) : TimelineRepository {

    override fun getTimelinePointsForDate(startMs: Long, endMs: Long): Flow<List<TimelineLocationPoint>> {
        return timelineDao.getPointsForDay(startMs, endMs)
    }

    override suspend fun insertPoint(point: TimelineLocationPoint) {
        timelineDao.insertPoint(point)
    }

    override suspend fun deleteOlderThan(timestamp: Long) {
        timelineDao.deleteOlderThan(timestamp)
    }

    override suspend fun markSignificantPlace(id: String, durationMs: Long) {
        timelineDao.markSignificantPlace(id, durationMs)
    }

    override suspend fun updatePlaceLabel(id: String, label: String, subLabel: String, icon: String) {
        timelineDao.updatePlaceLabel(id, label, subLabel, icon)
    }
}
