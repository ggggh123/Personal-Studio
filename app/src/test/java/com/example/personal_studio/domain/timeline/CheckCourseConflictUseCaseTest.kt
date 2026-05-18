package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.CourseSeriesDraft
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckCourseConflictUseCaseTest {

    private fun courseRow(weekday: Int, p1: Int, p2: Int, weekIdx: Int, title: String = "X") =
        TimelineItem(
            id = 0, type = TimelineType.COURSE, title = title, description = null,
            startAt = 0, endAt = 1, isDone = false, doneAt = null,
            location = null, instructor = null, notes = null,
            seriesId = 1, periodIndex = p1, periodEndIndex = p2,
            weekdayCode = weekday, weekIndexInSemester = weekIdx,
            colorOverride = null, sourceType = TimelineSource.MANUAL, sourceExternalId = null,
            kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
        )

    @Test fun `no conflict when weekday differs`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload((1..16).map { courseRow(weekday = 1, p1 = 1, p2 = 2, weekIdx = it) })
        }
        val useCase = CheckCourseConflictUseCase(repo)
        val hits = useCase.invoke(
            CourseSeriesDraft("Y", null, null, null,
                weekdays = listOf(2), periodStart = 1, periodEnd = 2, weekStart = 1, weekEnd = 16,
            )
        )
        assertTrue(hits.isEmpty())
    }

    @Test fun `period overlap on same weekday and week range yields conflict`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(courseRow(weekday = 1, p1 = 3, p2 = 4, weekIdx = 5)))
        }
        val useCase = CheckCourseConflictUseCase(repo)
        val hits = useCase.invoke(
            CourseSeriesDraft("Y", null, null, null,
                weekdays = listOf(1), periodStart = 4, periodEnd = 5, weekStart = 5, weekEnd = 5,
            )
        )
        assertEquals(1, hits.size)
    }

    @Test fun `non-overlapping period range yields no conflict`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(courseRow(weekday = 1, p1 = 3, p2 = 4, weekIdx = 5)))
        }
        val useCase = CheckCourseConflictUseCase(repo)
        val hits = useCase.invoke(
            CourseSeriesDraft("Y", null, null, null,
                weekdays = listOf(1), periodStart = 5, periodEnd = 6, weekStart = 5, weekEnd = 5,
            )
        )
        assertTrue(hits.isEmpty())
    }
}
