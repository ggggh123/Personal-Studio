package com.example.personal_studio.domain.timeline

import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddTaskUseCaseTest {

    private val repo = FakeTimelineRepository()
    private val useCase = AddTaskUseCase(repo) { 12345L } // injectable now()

    @Test fun `task persists with type TASK and null endAt`() = runTest {
        val id = useCase.invoke(
            type = TimelineType.TASK,
            title = "homework",
            description = "ch3",
            startAt = 1000,
            endAt = null,
            location = null,
        )
        val saved = repo.findById(id)!!
        assertEquals(TimelineType.TASK, saved.type)
        assertEquals("homework", saved.title)
        assertNull(saved.endAt)
        assertEquals(TimelineSource.MANUAL, saved.sourceType)
        assertEquals(12345L, saved.createdAt)
    }

    @Test fun `custom event persists with both startAt and endAt`() = runTest {
        val id = useCase.invoke(
            type = TimelineType.CUSTOM,
            title = "lab",
            description = null,
            startAt = 1000,
            endAt = 2000,
            location = "Room 301",
        )
        val saved = repo.findById(id)!!
        assertEquals(2000L, saved.endAt)
        assertEquals("Room 301", saved.location)
    }
}
