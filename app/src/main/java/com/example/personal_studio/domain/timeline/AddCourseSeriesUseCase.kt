package com.example.personal_studio.domain.timeline

import com.example.personal_studio.core.util.SemesterTimeMapper
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.CourseSeriesDraft
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class AddCourseSeriesUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val timetableProvider: suspend () -> List<TimetablePeriod>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    /**
     * Returns (seriesId, occurrenceCount). Throws IllegalArgumentException for malformed drafts.
     */
    suspend operator fun invoke(
        draft: CourseSeriesDraft,
        semesterStart: LocalDate,
    ): Pair<Long, Int> {
        require(draft.title.isNotBlank()) { "title blank" }
        require(draft.weekdays.isNotEmpty()) { "weekdays empty" }
        require(draft.weekdays.all { it in 1..7 }) { "weekday must be 1..7" }
        require(draft.periodStart in 1..30 && draft.periodEnd >= draft.periodStart) {
            "period range invalid: [${draft.periodStart}, ${draft.periodEnd}]"
        }
        require(draft.weekStart in 1..30 && draft.weekEnd >= draft.weekStart) {
            "week range invalid: [${draft.weekStart}, ${draft.weekEnd}]"
        }

        val timetable = timetableProvider()
        timetable.firstOrNull { it.index == draft.periodStart }
            ?: error("Timetable lacks period ${draft.periodStart}")
        timetable.firstOrNull { it.index == draft.periodEnd }
            ?: error("Timetable lacks period ${draft.periodEnd}")

        val seriesId = repo.nextSeriesId()
        val now = nowProvider()
        val items = mutableListOf<TimelineItem>()

        for (week in draft.weekStart..draft.weekEnd) {
            for (weekday in draft.weekdays) {
                val (start, end) = SemesterTimeMapper.mapPeriodRange(
                    semesterStart = semesterStart,
                    weekIndex = week,
                    weekday = weekday,
                    periodStart = draft.periodStart,
                    periodEnd = draft.periodEnd,
                    timetable = timetable,
                    zone = zone,
                )
                items += TimelineItem(
                    id = 0,
                    type = TimelineType.COURSE,
                    title = draft.title.trim(),
                    description = null,
                    startAt = start,
                    endAt = end,
                    isDone = false,
                    doneAt = null,
                    location = draft.location?.takeIf { it.isNotBlank() },
                    instructor = draft.instructor?.takeIf { it.isNotBlank() },
                    notes = draft.notes?.takeIf { it.isNotBlank() },
                    credits = draft.credits,
                    seriesId = seriesId,
                    periodIndex = draft.periodStart,
                    periodEndIndex = draft.periodEnd,
                    weekdayCode = weekday,
                    weekIndexInSemester = week,
                    colorOverride = null,
                    sourceType = TimelineSource.MANUAL,
                    sourceExternalId = null,
                    kbEntryIds = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }
        if (items.isEmpty()) error("No occurrences would be generated; check inputs.")
        repo.insertItems(items)
        return seriesId to items.size
    }
}
