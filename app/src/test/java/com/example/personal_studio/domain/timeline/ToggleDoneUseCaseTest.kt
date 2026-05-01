package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleDoneUseCaseTest {

    @Test fun `done sets isDone true and doneAt now`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(item(TimelineType.TASK, id = 7)))
        }
        val useCase = ToggleDoneUseCase(repo, nowProvider = { 1234L })
        useCase(itemId = 7, done = true)
        val saved = repo.findById(7)!!
        assertTrue(saved.isDone)
        assertEquals(1234L, saved.doneAt)
    }

    @Test fun `undone clears doneAt`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(item(TimelineType.TASK, id = 7, isDone = true, doneAt = 999)))
        }
        val useCase = ToggleDoneUseCase(repo, nowProvider = { 1234L })
        useCase(itemId = 7, done = false)
        val saved = repo.findById(7)!!
        assertFalse(saved.isDone)
        assertNull(saved.doneAt)
    }

    @Test fun `course rejects toggle`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(item(TimelineType.COURSE, id = 7)))
        }
        val useCase = ToggleDoneUseCase(repo)
        try {
            useCase(itemId = 7, done = true)
            assert(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    private fun item(type: TimelineType, id: Long, isDone: Boolean = false, doneAt: Long? = null) =
        TimelineItem(
            id = id, type = type, title = "x", description = null,
            startAt = 0, endAt = null, isDone = isDone, doneAt = doneAt,
            location = null, instructor = null, notes = null,
            seriesId = null, periodIndex = null, periodEndIndex = null,
            weekdayCode = null, weekIndexInSemester = null, colorOverride = null,
            sourceType = TimelineSource.MANUAL, sourceExternalId = null,
            kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
        )
}
