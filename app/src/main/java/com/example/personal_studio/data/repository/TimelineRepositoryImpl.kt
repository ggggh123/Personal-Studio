package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.dao.DayCount
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.model.CourseSeriesSummary
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepositoryImpl @Inject constructor(
    private val dao: TimelineDao,
) : TimelineRepository {

    override fun observeItemsInRange(startInclusive: Long, endExclusive: Long): Flow<List<TimelineItem>> =
        dao.observeItemsInRange(startInclusive, endExclusive).map { rows -> rows.map(::toDomain) }

    override fun observeDayCounts(startInclusive: Long, endExclusive: Long): Flow<List<DayCount>> =
        dao.observeDayCounts(startInclusive, endExclusive)

    override fun observeCourseSeriesList(): Flow<List<CourseSeriesSummary>> =
        dao.observeCourseSeriesList().map { rows ->
            rows.map { CourseSeriesSummary(it.seriesId, it.title, it.instructor, it.location, it.occurrenceCount, it.minWeek, it.maxWeek) }
        }

    override suspend fun findById(id: Long): TimelineItem? = dao.findById(id)?.let(::toDomain)

    override suspend fun insertItems(items: List<TimelineItem>): List<Long> =
        dao.insertAll(items.map(::toEntity))

    override suspend fun updateItem(item: TimelineItem) = dao.update(toEntity(item))

    override suspend fun updateTime(id: Long, startAt: Long, endAt: Long?, now: Long) =
        dao.updateTime(id, startAt, endAt, now)

    override suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long) =
        dao.setDone(id, done, doneAt, now)

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteSeriesAll(seriesId: Long) = dao.deleteSeriesAll(seriesId)
    override suspend fun deleteSeriesFuture(seriesId: Long, now: Long) = dao.deleteSeriesFuture(seriesId, now)

    override suspend fun updateSeriesAttributes(
        seriesId: Long, title: String, instructor: String?, location: String?, notes: String?, now: Long,
    ) = dao.updateSeriesAttributes(seriesId, title, instructor, location, notes, now)

    override suspend fun nextSeriesId(): Long = (dao.maxSeriesId() ?: 0L) + 1L

    override suspend fun getUpcomingItems(now: Long, until: Long): List<TimelineItem> =
        dao.getUpcomingItems(now, until).map(::toDomain)

    override suspend fun getFutureCourses(now: Long): List<TimelineItem> =
        dao.getFutureCourses(now).map(::toDomain)

    override suspend fun firstOfSeries(seriesId: Long): TimelineItem? =
        dao.firstOfSeries(seriesId)?.let(::toDomain)

    override suspend fun itemsForSeries(seriesId: Long): List<TimelineItem> =
        dao.observeItemsInRange(0, Long.MAX_VALUE).first()
            .filter { it.seriesId == seriesId }
            .map(::toDomain)

    override suspend fun countFutureCoursesUsingPeriodRange(minPeriod: Int, maxPeriod: Int, now: Long): Int =
        dao.countFutureCoursesUsingPeriodRange(minPeriod, maxPeriod, now)

    override suspend fun findCourseConflicts(
        weekday: Int, periodStart: Int, periodEnd: Int, weekStart: Int, weekEnd: Int,
    ): List<TimelineItem> =
        dao.findCourseConflicts(weekday, periodStart, periodEnd, weekStart, weekEnd).map(::toDomain)

    // ---------- mappers ----------

    private fun toDomain(e: TimelineItemEntity): TimelineItem = TimelineItem(
        id = e.id,
        type = e.type,
        title = e.title,
        description = e.description,
        startAt = e.startAt,
        endAt = e.endAt,
        isDone = e.isDone,
        doneAt = e.doneAt,
        location = e.location,
        instructor = e.instructor,
        notes = e.notes,
        seriesId = e.seriesId,
        periodIndex = e.periodIndex,
        periodEndIndex = e.periodEndIndex,
        weekdayCode = e.weekdayCode,
        weekIndexInSemester = e.weekIndexInSemester,
        colorOverride = e.colorOverride,
        sourceType = e.sourceType,
        sourceExternalId = e.sourceExternalId,
        kbEntryIds = runCatching { Json.decodeFromString<List<Long>>(e.kbEntryIdsJson) }
            .getOrDefault(emptyList()),
        createdAt = e.createdAt,
        updatedAt = e.updatedAt,
    )

    private fun toEntity(m: TimelineItem): TimelineItemEntity = TimelineItemEntity(
        id = m.id,
        type = m.type,
        title = m.title,
        description = m.description,
        startAt = m.startAt,
        endAt = m.endAt,
        isDone = m.isDone,
        doneAt = m.doneAt,
        location = m.location,
        instructor = m.instructor,
        notes = m.notes,
        seriesId = m.seriesId,
        periodIndex = m.periodIndex,
        periodEndIndex = m.periodEndIndex,
        weekdayCode = m.weekdayCode,
        weekIndexInSemester = m.weekIndexInSemester,
        colorOverride = m.colorOverride,
        sourceType = m.sourceType,
        sourceExternalId = m.sourceExternalId,
        kbEntryIdsJson = Json.encodeToString(ListSerializer(Long.serializer()), m.kbEntryIds),
        createdAt = m.createdAt,
        updatedAt = m.updatedAt,
    )

    @Suppress("unused") private val _ensureEnumsImported: Pair<TimelineType, TimelineSource> =
        TimelineType.TASK to TimelineSource.MANUAL
}
