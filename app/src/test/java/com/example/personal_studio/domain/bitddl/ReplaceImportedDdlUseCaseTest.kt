package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplaceImportedDdlUseCaseTest {
    private fun row(id: Long, uid: String, done: Boolean, source: TimelineSource = TimelineSource.IMPORTED_LEXUE) = TimelineItemEntity(
        id = id, type = TimelineType.TASK, title = "old", description = "old",
        startAt = 100L, isDone = done, doneAt = if (done) 50L else null,
        sourceType = source, sourceExternalId = uid,
        createdAt = 1L, updatedAt = 1L, courseName = "old",
    )
    private fun ev(uid: String, due: Long) = DdlEvent(uid, "new", "newdesc", "数学", due)
    private fun hw(id: Int, submitted: Boolean) =
        DdlEvent("eclass-homework-$id", "作业$id", "", "计算机视觉", 500L, DdlSource.ECLASS, submitted)
    private fun prefs(submitted: Set<String> = emptySet()) = mockk<DdlSyncPrefs>(relaxed = true) {
        coEvery { snapshot() } returns DdlSyncState(true, 12, null, emptySet(), null, submittedUids = submitted)
    }
    private fun dao(source: TimelineSource, rows: List<TimelineItemEntity>) = mockk<TimelineDao>(relaxed = true) {
        coEvery { getImportedDdls(source.name) } returns rows
    }

    @Test fun `inserts new ddl as TASK of its platform`() = runTest {
        val dao = dao(TimelineSource.IMPORTED_LEXUE, emptyList())
        val resched = mockk<RescheduleAllUpcomingUseCase>(relaxed = true)
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.insertOne(capture(slotItem)) } returns 1L
        ReplaceImportedDdlUseCase(dao, prefs(), mockk(relaxed = true), resched).invoke(DdlSource.LEXUE, listOf(ev("u1", 200L)), now = 999L)
        assertEquals(TimelineType.TASK, slotItem.captured.type)
        assertEquals(TimelineSource.IMPORTED_LEXUE, slotItem.captured.sourceType)
        assertEquals("u1", slotItem.captured.sourceExternalId)
        assertEquals(200L, slotItem.captured.startAt)
        assertEquals("数学", slotItem.captured.courseName)
        assertEquals(false, slotItem.captured.isDone)
        coVerify { resched.invoke() }
    }

    @Test fun `updates existing preserving isDone`() = runTest {
        val dao = dao(TimelineSource.IMPORTED_LEXUE, listOf(row(7L, "u1", done = true)))
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.update(capture(slotItem)) } just Runs
        ReplaceImportedDdlUseCase(dao, prefs(), mockk(relaxed = true), mockk(relaxed = true))
            .invoke(DdlSource.LEXUE, listOf(ev("u1", 300L)), now = 999L)
        assertEquals(7L, slotItem.captured.id)
        assertEquals("new", slotItem.captured.title)
        assertEquals(300L, slotItem.captured.startAt)
        assertEquals(true, slotItem.captured.isDone)   // preserved
        coVerify(exactly = 0) { dao.insertOne(any()) }
    }

    @Test fun `deletes vanished ddl of the same platform only, with its reminders`() = runTest {
        val dao = dao(TimelineSource.IMPORTED_LEXUE, listOf(row(7L, "gone", done = false)))
        val cancel = mockk<CancelRemindersUseCase>(relaxed = true)
        ReplaceImportedDdlUseCase(dao, prefs(), cancel, mockk(relaxed = true)).invoke(DdlSource.LEXUE, listOf(ev("u1", 200L)), now = 999L)
        coVerify { dao.deleteById(7L) }
        coVerify { cancel.invoke(7L) }
        coVerify(exactly = 0) { dao.getImportedDdls(TimelineSource.IMPORTED_ECLASS.name) }
    }

    @Test fun `new 新乐学 homework already submitted arrives done`() = runTest {
        val dao = dao(TimelineSource.IMPORTED_ECLASS, emptyList())
        val items = mutableListOf<TimelineItemEntity>()
        coEvery { dao.insertOne(capture(items)) } returns 1L
        val prefs = prefs()
        ReplaceImportedDdlUseCase(dao, prefs, mockk(relaxed = true), mockk(relaxed = true))
            .invoke(DdlSource.ECLASS, listOf(hw(1, submitted = true), hw(2, submitted = false)), now = 999L)
        assertEquals(TimelineSource.IMPORTED_ECLASS, items[0].sourceType)
        assertEquals(true, items[0].isDone)
        assertEquals(999L, items[0].doneAt)
        assertEquals(false, items[1].isDone)
        assertNull(items[1].doneAt)
        coVerify { prefs.setSubmittedUids(setOf("eclass-homework-1")) }
    }

    @Test fun `submitting on 新乐学 ticks the task once and cancels its reminders`() = runTest {
        val dao = dao(TimelineSource.IMPORTED_ECLASS, listOf(row(9L, "eclass-homework-1", done = false, TimelineSource.IMPORTED_ECLASS)))
        val updated = slot<TimelineItemEntity>()
        coEvery { dao.update(capture(updated)) } just Runs
        val cancel = mockk<CancelRemindersUseCase>(relaxed = true)
        ReplaceImportedDdlUseCase(dao, prefs(), cancel, mockk(relaxed = true))
            .invoke(DdlSource.ECLASS, listOf(hw(1, submitted = true)), now = 999L)
        assertEquals(true, updated.captured.isDone)
        assertEquals(999L, updated.captured.doneAt)
        coVerify { cancel.invoke(9L) }
    }

    @Test fun `a task the user unticked after submitting stays unticked`() = runTest {
        val dao = dao(TimelineSource.IMPORTED_ECLASS, listOf(row(9L, "eclass-homework-1", done = false, TimelineSource.IMPORTED_ECLASS)))
        val updated = slot<TimelineItemEntity>()
        coEvery { dao.update(capture(updated)) } just Runs
        val cancel = mockk<CancelRemindersUseCase>(relaxed = true)
        ReplaceImportedDdlUseCase(dao, prefs(submitted = setOf("eclass-homework-1")), cancel, mockk(relaxed = true))
            .invoke(DdlSource.ECLASS, listOf(hw(1, submitted = true)), now = 999L)
        assertEquals(false, updated.captured.isDone)
        coVerify(exactly = 0) { cancel.invoke(any()) }
    }
}
