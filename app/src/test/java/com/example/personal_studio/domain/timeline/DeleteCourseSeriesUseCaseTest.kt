package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteCourseSeriesUseCaseTest {

    @Test fun `ALL removes both past and future`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(course(id = 1, seriesId = 7, startAt = 10, endAt = 20),
                course(id = 2, seriesId = 7, startAt = 100, endAt = 200)))
        }
        val useCase = DeleteCourseSeriesUseCase(repo, nowProvider = { 50L })
        useCase(seriesId = 7, scope = DeleteCourseSeriesUseCase.Scope.ALL)
        assertEquals(0, repo.snapshot().size)
    }

    @Test fun `FUTURE_ONLY keeps past rows`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(course(id = 1, seriesId = 7, startAt = 10, endAt = 20),
                course(id = 2, seriesId = 7, startAt = 100, endAt = 200)))
        }
        val useCase = DeleteCourseSeriesUseCase(repo, nowProvider = { 50L })
        useCase(seriesId = 7, scope = DeleteCourseSeriesUseCase.Scope.FUTURE_ONLY)
        val remaining = repo.snapshot()
        assertEquals(1, remaining.size)
        assertEquals(1L, remaining.first().id)
    }

    private fun course(id: Long, seriesId: Long, startAt: Long, endAt: Long) = TimelineItem(
        id = id, type = TimelineType.COURSE, title = "x", description = null,
        startAt = startAt, endAt = endAt, isDone = false, doneAt = null,
        location = null, instructor = null, notes = null,
        seriesId = seriesId, periodIndex = 1, periodEndIndex = 1,
        weekdayCode = 1, weekIndexInSemester = 1, colorOverride = null,
        sourceType = TimelineSource.MANUAL, sourceExternalId = null,
        kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
    )
}
