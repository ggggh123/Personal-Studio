package com.example.personal_studio.domain.timeline

import com.example.personal_studio.core.util.SemesterTimeMapper
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.repository.TimelineRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RecalculateCoursesAfterTimetableChangeUseCase @Inject constructor(
    private val repo: TimelineRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val semesterProvider: suspend () -> LocalDate?,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    /**
     * Recompute startAt / endAt for every COURSE row whose endAt > now using [newTimetable].
     * Appends touched item ids to [outRescheduleIds] so the caller can re-schedule reminders
     * (Phase 5 wires that up; Phase 4 just leaves the buffer for it).
     */
    suspend operator fun invoke(newTimetable: List<TimetablePeriod>, outRescheduleIds: MutableList<Long>) {
        val now = nowProvider()
        val semester = semesterProvider() ?: return
        val futures = repo.getFutureCourses(now)
        for (row in futures) {
            val pi = row.periodIndex ?: continue
            val pe = row.periodEndIndex ?: continue
            val wd = row.weekdayCode ?: continue
            val wi = row.weekIndexInSemester ?: continue
            val (newStart, newEnd) = SemesterTimeMapper.mapPeriodRange(
                semesterStart = semester,
                weekIndex = wi,
                weekday = wd,
                periodStart = pi,
                periodEnd = pe,
                timetable = newTimetable,
                zone = zone,
            )
            if (newStart != row.startAt || newEnd != row.endAt) {
                repo.updateTime(row.id, newStart, newEnd, now)
                outRescheduleIds += row.id
            }
        }
    }
}
