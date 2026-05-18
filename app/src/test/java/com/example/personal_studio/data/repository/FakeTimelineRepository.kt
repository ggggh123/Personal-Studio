package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.dao.DayCount
import com.example.personal_studio.domain.model.CourseSeriesSummary
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTimelineRepository : TimelineRepository {
    private val items = MutableStateFlow<List<TimelineItem>>(emptyList())
    private var nextId = 1L
    private var nextSeries = 0L

    fun snapshot(): List<TimelineItem> = items.value
    fun preload(rows: List<TimelineItem>) { items.value = rows }

    override fun observeItemsInRange(startInclusive: Long, endExclusive: Long): Flow<List<TimelineItem>> =
        items.map { list ->
            list.filter { it.startAt < endExclusive && (it.endAt ?: it.startAt) >= startInclusive }
                .sortedBy { it.startAt }
        }

    override fun observeDayCounts(startInclusive: Long, endExclusive: Long): Flow<List<DayCount>> =
        items.map { _ -> emptyList() } // tests that need this provide their own subclass

    override fun observeCourseSeriesList(): Flow<List<CourseSeriesSummary>> =
        items.map { list ->
            list.filter { it.type == TimelineType.COURSE && it.seriesId != null }
                .groupBy { it.seriesId!! }
                .map { (sid, rows) ->
                    val first = rows.first()
                    CourseSeriesSummary(
                        seriesId = sid,
                        title = first.title,
                        instructor = first.instructor,
                        location = first.location,
                        credits = first.credits,
                        occurrenceCount = rows.size,
                        minWeek = rows.minOf { it.weekIndexInSemester ?: 0 },
                        maxWeek = rows.maxOf { it.weekIndexInSemester ?: 0 },
                    )
                }
        }

    override suspend fun findById(id: Long): TimelineItem? = items.value.firstOrNull { it.id == id }

    override suspend fun insertItems(input: List<TimelineItem>): List<Long> {
        val assigned = input.map { it.copy(id = nextId++) }
        items.value = items.value + assigned
        return assigned.map { it.id }
    }

    override suspend fun updateItem(item: TimelineItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun updateTime(id: Long, startAt: Long, endAt: Long?, now: Long) {
        items.value = items.value.map {
            if (it.id == id) it.copy(startAt = startAt, endAt = endAt, updatedAt = now) else it
        }
    }

    override suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long) {
        items.value = items.value.map {
            if (it.id == id) it.copy(isDone = done, doneAt = doneAt, updatedAt = now) else it
        }
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filter { it.id != id }
    }

    override suspend fun deleteSeriesAll(seriesId: Long) {
        items.value = items.value.filter { it.seriesId != seriesId }
    }

    override suspend fun deleteSeriesFuture(seriesId: Long, now: Long) {
        items.value = items.value.filter { it.seriesId != seriesId || (it.endAt ?: it.startAt) <= now }
    }

    override suspend fun updateSeriesAttributes(
        seriesId: Long, title: String, instructor: String?, location: String?, notes: String?, credits: Float?, now: Long,
    ) {
        items.value = items.value.map {
            if (it.seriesId == seriesId)
                it.copy(title = title, instructor = instructor, location = location, notes = notes, credits = credits, updatedAt = now)
            else it
        }
    }

    override suspend fun nextSeriesId(): Long = ++nextSeries

    override suspend fun getUpcomingItems(now: Long, until: Long): List<TimelineItem> =
        items.value.filter { !it.isDone && it.startAt in now until until }

    override suspend fun getFutureCourses(now: Long): List<TimelineItem> =
        items.value.filter { it.type == TimelineType.COURSE && (it.endAt ?: it.startAt) > now }

    override suspend fun firstOfSeries(seriesId: Long): TimelineItem? =
        items.value.filter { it.seriesId == seriesId }.minByOrNull { it.startAt }

    override suspend fun itemsForSeries(seriesId: Long): List<TimelineItem> =
        items.value.filter { it.seriesId == seriesId }.sortedBy { it.startAt }

    override suspend fun countFutureCoursesUsingPeriodRange(minPeriod: Int, maxPeriod: Int, now: Long): Int =
        items.value.count {
            it.type == TimelineType.COURSE
                && it.periodIndex != null && it.periodEndIndex != null
                && (it.endAt ?: it.startAt) > now
                && it.periodIndex <= maxPeriod && it.periodEndIndex >= minPeriod
        }

    override suspend fun findCourseConflicts(
        weekday: Int, periodStart: Int, periodEnd: Int, weekStart: Int, weekEnd: Int,
    ): List<TimelineItem> =
        items.value.filter {
            it.type == TimelineType.COURSE
                && it.weekdayCode == weekday
                && it.periodIndex != null && it.periodEndIndex != null
                && !(it.periodEndIndex < periodStart || it.periodIndex > periodEnd)
                && (it.weekIndexInSemester ?: 0) in weekStart..weekEnd
        }
}
