package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.bitexam.model.ExamItem
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

class ReplaceImportedExamUseCaseTest {
    private fun examRow(id: Long, uid: String, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "old", startAt = 100L, endAt = 200L,
        isDone = done, doneAt = if (done) 50L else null,
        sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = uid,
        createdAt = 1L, updatedAt = 1L,
    )
    private fun item(uid: String, start: Long) =
        ExamItem(uid, "高数", start, start + 7200_000L, "中教401", "23", "张老师")

    @Test fun `inserts new exam as EXAM IMPORTED_EXAM with block + seat`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { coEvery { getImportedExams() } returns emptyList() }
        val resched = mockk<RescheduleAllUpcomingUseCase>(relaxed = true)
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.insertOne(capture(slotItem)) } returns 1L
        ReplaceImportedExamUseCase(dao, resched).invoke(listOf(item("u1", 1000L)), now = 9L)
        with(slotItem.captured) {
            assertEquals(TimelineType.EXAM, type)
            assertEquals(TimelineSource.IMPORTED_EXAM, sourceType)
            assertEquals("u1", sourceExternalId)
            assertEquals(1000L, startAt)
            assertEquals(1000L + 7200_000L, endAt)
            assertEquals("中教401", location)
            assertEquals("张老师", instructor)
            assertEquals("座位: 23", notes)
        }
        coVerify { resched.invoke() }
    }

    @Test fun `updates existing preserving isDone`() = runTest {
        val existing = examRow(7L, "u1", done = true)
        val dao = mockk<TimelineDao>(relaxed = true) { coEvery { getImportedExams() } returns listOf(existing) }
        val slotItem = slot<TimelineItemEntity>()
        coEvery { dao.update(capture(slotItem)) } just Runs
        ReplaceImportedExamUseCase(dao, mockk(relaxed = true)).invoke(listOf(item("u1", 300L)), now = 9L)
        assertEquals(7L, slotItem.captured.id)
        assertEquals(300L, slotItem.captured.startAt)
        assertEquals(true, slotItem.captured.isDone)
        coVerify(exactly = 0) { dao.insertOne(any()) }
    }

    @Test fun `deletes vanished exam`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            coEvery { getImportedExams() } returns listOf(examRow(7L, "gone", done = false))
        }
        ReplaceImportedExamUseCase(dao, mockk(relaxed = true)).invoke(listOf(item("u1", 300L)), now = 9L)
        coVerify { dao.deleteById(7L) }
    }

    @Test fun `reconciles insert update-preserving-done and delete in one pass`() = runTest {
        // existing: u-update (done=true, keep), u-gone (to delete). incoming: u-update, u-new.
        val keep = examRow(11L, "u-update", done = true)
        val gone = examRow(22L, "u-gone", done = false)
        val dao = mockk<TimelineDao>(relaxed = true) {
            coEvery { getImportedExams() } returns listOf(keep, gone)
        }
        val inserted = slot<TimelineItemEntity>()
        val updated = slot<TimelineItemEntity>()
        coEvery { dao.insertOne(capture(inserted)) } returns 99L
        coEvery { dao.update(capture(updated)) } just Runs

        ReplaceImportedExamUseCase(dao, mockk(relaxed = true)).invoke(
            listOf(item("u-update", 300L), item("u-new", 500L)),
            now = 9L,
        )

        // delete the vanished one only
        coVerify { dao.deleteById(22L) }
        coVerify(exactly = 0) { dao.deleteById(11L) }
        // insert the new one
        assertEquals("u-new", inserted.captured.sourceExternalId)
        assertEquals(500L, inserted.captured.startAt)
        assertEquals(false, inserted.captured.isDone)
        // update the existing one, preserving isDone=true and reusing its id
        assertEquals(11L, updated.captured.id)
        assertEquals(300L, updated.captured.startAt)
        assertEquals(true, updated.captured.isDone)
    }
}
