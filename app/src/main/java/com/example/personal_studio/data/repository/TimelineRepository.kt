package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.dao.DayCount
import com.example.personal_studio.domain.model.CourseSeriesSummary
import com.example.personal_studio.domain.model.TimelineItem
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {

    fun observeItemsInRange(startInclusive: Long, endExclusive: Long): Flow<List<TimelineItem>>
    fun observeDayCounts(startInclusive: Long, endExclusive: Long): Flow<List<DayCount>>
    fun observeCourseSeriesList(): Flow<List<CourseSeriesSummary>>

    suspend fun findById(id: Long): TimelineItem?

    suspend fun insertItems(items: List<TimelineItem>): List<Long>

    suspend fun updateItem(item: TimelineItem)
    suspend fun updateTime(id: Long, startAt: Long, endAt: Long?, now: Long)
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long)

    suspend fun deleteById(id: Long)
    suspend fun deleteSeriesAll(seriesId: Long)
    suspend fun deleteSeriesFuture(seriesId: Long, now: Long)

    suspend fun updateSeriesAttributes(
        seriesId: Long,
        title: String,
        instructor: String?,
        location: String?,
        notes: String?,
        now: Long,
    )

    suspend fun nextSeriesId(): Long
    suspend fun getUpcomingItems(now: Long, until: Long): List<TimelineItem>
    suspend fun getFutureCourses(now: Long): List<TimelineItem>
    suspend fun firstOfSeries(seriesId: Long): TimelineItem?
    suspend fun itemsForSeries(seriesId: Long): List<TimelineItem>
    suspend fun countFutureCoursesUsingPeriodRange(minPeriod: Int, maxPeriod: Int, now: Long): Int

    suspend fun findCourseConflicts(
        weekday: Int,
        periodStart: Int,
        periodEnd: Int,
        weekStart: Int,
        weekEnd: Int,
    ): List<TimelineItem>
}
