package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceImportedDdlUseCaseTest {
    private fun lexueRow(id: Long, uid: String, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.TASK, title = "old", description = "old",
        startAt = 100L, isDone = done, doneAt = if (done) 50L else null,
        sourceType = TimelineSource.IMPORTED_LEXUE, sourceExternalId = uid,
        createdAt = 1L, updatedAt = 1L, courseName = "old",
    )
    private fun ev(uid: String, due: Long) = DdlEvent(uid, "new", "newdesc", "数学", due)

    @Test fun `inserts new ddl as TASK IMPORTED_LEXUE`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { coEvery { getLexueDdls() } returns emptyList() }
        val resched = mockk<RescheduleAllUpcomingUseCase>(relaxed = true)
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.insertOne(capture(slotItem)) } returns 1L
        ReplaceImportedDdlUseCase(dao, resched).invoke(listOf(ev("u1", 200L)), now = 999L)
        assertEquals(TimelineType.TASK, slotItem.captured.type)
        assertEquals(TimelineSource.IMPORTED_LEXUE, slotItem.captured.sourceType)
        assertEquals("u1", slotItem.captured.sourceExternalId)
        assertEquals(200L, slotItem.captured.startAt)
        assertEquals("数学", slotItem.captured.courseName)
        coVerify { resched.invoke() }
    }

    @Test fun `updates existing preserving isDone`() = runTest {
        val existing = lexueRow(7L, "u1", done = true)
        val dao = mockk<TimelineDao>(relaxed = true) { coEvery { getLexueDdls() } returns listOf(existing) }
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.update(capture(slotItem)) } just Runs
        ReplaceImportedDdlUseCase(dao, mockk(relaxed = true)).invoke(listOf(ev("u1", 300L)), now = 999L)
        assertEquals(7L, slotItem.captured.id)
        assertEquals("new", slotItem.captured.title)
        assertEquals(300L, slotItem.captured.startAt)
        assertEquals(true, slotItem.captured.isDone)   // preserved
        coVerify(exactly = 0) { dao.insertOne(any()) }
    }

    @Test fun `deletes vanished ddl`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            coEvery { getLexueDdls() } returns listOf(lexueRow(7L, "gone", done = false))
        }
        ReplaceImportedDdlUseCase(dao, mockk(relaxed = true)).invoke(listOf(ev("u1", 200L)), now = 999L)
        coVerify { dao.deleteById(7L) }
    }
}
