package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.CourseSeriesDraft
import com.example.personal_studio.domain.model.TimelineItem
import javax.inject.Inject

class CheckCourseConflictUseCase @Inject constructor(
    private val repo: TimelineRepository,
) {
    /**
     * Aggregates conflicts across all weekdays in the draft. Each match represents an existing
     * row whose (weekday, periodIndex..periodEndIndex, weekIndexInSemester) overlaps the draft.
     */
    suspend operator fun invoke(draft: CourseSeriesDraft): List<TimelineItem> {
        val out = mutableListOf<TimelineItem>()
        for (wd in draft.weekdays) {
            out += repo.findCourseConflicts(
                weekday = wd,
                periodStart = draft.periodStart,
                periodEnd = draft.periodEnd,
                weekStart = draft.weekStart,
                weekEnd = draft.weekEnd,
            )
        }
        return out.distinctBy { it.id }
    }
}
