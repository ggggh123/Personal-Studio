package com.example.personal_studio.data.local.db.dao

import androidx.room.*
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import kotlinx.coroutines.flow.Flow

data class CourseSeriesSummaryRow(
    val seriesId: Long,
    val title: String,
    val instructor: String?,
    val location: String?,
    val credits: Float?,
    val occurrenceCount: Int,
    val minWeek: Int,
    val maxWeek: Int,
    val weekdaysCsv: String?,
    val periodStart: Int?,
    val periodEnd: Int?,
)

/**
 * Per-day activity counts split by type. The day-strip uses (courseCount, taskCount)
 * to render two coloured badges; CUSTOM is intentionally excluded from the strip.
 */
data class DayCount(
    val day: String,
    val courseCount: Int,
    val taskCount: Int,
)

@Dao
interface TimelineDao {

    // ---------- Insert / Update / Delete ----------

    @Insert
    suspend fun insertAll(items: List<TimelineItemEntity>): List<Long>

    @Insert
    suspend fun insertOne(item: TimelineItemEntity): Long

    @Update
    suspend fun update(item: TimelineItemEntity)

    @Query("UPDATE timeline_items SET startAt = :startAt, endAt = :endAt, updatedAt = :now WHERE id = :id")
    suspend fun updateTime(id: Long, startAt: Long, endAt: Long?, now: Long)

    @Query("UPDATE timeline_items SET isDone = :done, doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long)

    @Query("DELETE FROM timeline_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM timeline_items WHERE seriesId = :seriesId")
    suspend fun deleteSeriesAll(seriesId: Long)

    @Query("DELETE FROM timeline_items WHERE seriesId = :seriesId AND COALESCE(endAt, startAt) > :now")
    suspend fun deleteSeriesFuture(seriesId: Long, now: Long)

    @Query("UPDATE timeline_items SET title = :title, instructor = :instructor, location = :location, notes = :notes, credits = :credits, updatedAt = :now WHERE seriesId = :seriesId")
    suspend fun updateSeriesAttributes(
        seriesId: Long,
        title: String,
        instructor: String?,
        location: String?,
        notes: String?,
        credits: Float?,
        now: Long,
    )

    // ---------- Reads ----------

    @Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_LEXUE'")
    suspend fun getLexueDdls(): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_LEXUE' ORDER BY startAt ASC")
    fun observeLexueDdls(): kotlinx.coroutines.flow.Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_EXAM'")
    suspend fun getImportedExams(): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE sourceType = 'IMPORTED_EXAM' ORDER BY startAt ASC")
    fun observeImportedExams(): kotlinx.coroutines.flow.Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE id = :id")
    suspend fun findById(id: Long): TimelineItemEntity?

    @Query("SELECT MAX(seriesId) FROM timeline_items")
    suspend fun maxSeriesId(): Long?

    @Query(
        "SELECT * FROM timeline_items " +
        "WHERE startAt < :endExclusive AND COALESCE(endAt, startAt) >= :startInclusive " +
        "ORDER BY startAt ASC"
    )
    fun observeItemsInRange(startInclusive: Long, endExclusive: Long): Flow<List<TimelineItemEntity>>

    @Query(
        """
        SELECT date(startAt / 1000, 'unixepoch', 'localtime') AS day,
               SUM(CASE WHEN type = 'COURSE' THEN 1 ELSE 0 END) AS courseCount,
               SUM(CASE WHEN type IN ('TASK','EXAM') THEN 1 ELSE 0 END) AS taskCount
        FROM timeline_items
        WHERE startAt >= :startInclusive AND startAt < :endExclusive
        GROUP BY day
        """
    )
    fun observeDayCounts(startInclusive: Long, endExclusive: Long): Flow<List<DayCount>>

    /**
     * Aggregates rows of a COURSE series into a single summary row.
     *
     * Title / instructor / location / credits use `MIN(...)` because:
     * - title and instructor are series-level invariants (`updateSeriesAttributes`
     *   writes them uniformly across all rows); MIN is purely lexicographic and
     *   matches the canonical value.
     * - location MAY diverge per occurrence (bubble-detail allows "改地点(本次)"
     *   per spec §3.4), in which case MIN surfaces an arbitrary representative.
     *   The Settings list is informational; clicking through to series edit
     *   shows the most-recently-set series-level location.
     * - credits is series-level (same caveat as title/instructor); MIN is purely
     *   numeric aggregation and matches the canonical value because the column
     *   is uniform per-series via `updateSeriesAttributes`.
     */
    @Query(
        """
        SELECT seriesId, MIN(title) AS title, MIN(instructor) AS instructor, MIN(location) AS location,
               MIN(credits) AS credits,
               COUNT(*) AS occurrenceCount, MIN(weekIndexInSemester) AS minWeek, MAX(weekIndexInSemester) AS maxWeek,
               GROUP_CONCAT(DISTINCT weekdayCode) AS weekdaysCsv,
               MIN(periodIndex) AS periodStart, MAX(periodEndIndex) AS periodEnd
        FROM timeline_items
        WHERE type = 'COURSE' AND seriesId IS NOT NULL
        GROUP BY seriesId
        ORDER BY MIN(startAt) ASC
        """
    )
    fun observeCourseSeriesList(): Flow<List<CourseSeriesSummaryRow>>

    @Query("SELECT * FROM timeline_items WHERE seriesId = :seriesId ORDER BY startAt ASC LIMIT 1")
    suspend fun firstOfSeries(seriesId: Long): TimelineItemEntity?

    @Query("SELECT * FROM timeline_items WHERE seriesId = :seriesId ORDER BY startAt ASC")
    suspend fun itemsForSeries(seriesId: Long): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE isDone = 0 AND startAt >= :now AND startAt < :until")
    suspend fun getUpcomingItems(now: Long, until: Long): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE type = 'COURSE' AND COALESCE(endAt, startAt) > :now")
    suspend fun getFutureCourses(now: Long): List<TimelineItemEntity>

    @Query("SELECT COUNT(*) FROM timeline_items WHERE type = 'COURSE' AND periodIndex <= :maxPeriod AND periodEndIndex >= :minPeriod AND COALESCE(endAt, startAt) > :now")
    suspend fun countFutureCoursesUsingPeriodRange(minPeriod: Int, maxPeriod: Int, now: Long): Int

    /**
     * Lightweight conflict check during AddCourseScreen.
     * Returns rows whose (weekday, periodIndex..periodEndIndex, weekIndexInSemester) overlap.
     */
    @Query(
        """
        SELECT * FROM timeline_items
        WHERE type = 'COURSE'
          AND weekdayCode = :weekday
          AND periodIndex IS NOT NULL AND periodEndIndex IS NOT NULL
          AND NOT (periodEndIndex < :periodStart OR periodIndex > :periodEnd)
          AND weekIndexInSemester BETWEEN :weekStart AND :weekEnd
        """
    )
    suspend fun findCourseConflicts(
        weekday: Int,
        periodStart: Int,
        periodEnd: Int,
        weekStart: Int,
        weekEnd: Int,
    ): List<TimelineItemEntity>

    /** Counts the IMPORTED_PORTAL rows whose startAt falls in [start, end).
     *  Used by the preview screen to show how many rows would be overwritten. */
    @Query("""
        SELECT COUNT(*) FROM timeline_items
        WHERE sourceType = 'IMPORTED_PORTAL'
          AND startAt >= :startInclusive AND startAt < :endExclusive
    """)
    suspend fun countImportedInRange(startInclusive: Long, endExclusive: Long): Int

    /** Deletes the IMPORTED_PORTAL rows whose startAt falls in [start, end).
     *  MANUAL rows are untouched. Returns the number of rows deleted. */
    @Query("""
        DELETE FROM timeline_items
        WHERE sourceType = 'IMPORTED_PORTAL'
          AND startAt >= :startInclusive AND startAt < :endExclusive
    """)
    suspend fun deleteImportedInRange(startInclusive: Long, endExclusive: Long): Int
}
