package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectNewGradesUseCaseTest {
    private fun entry(term: String, code: String, attempt: String, score: String) = GradeEntryEntity(
        termCode = term, termName = term, courseName = code, courseCode = code, credit = 3.0,
        score = score, gradePoint = 4.0, gradeLetter = null, category = null,
        attemptType = attempt, isPass = true, fetchedAt = 1L,
    )
    private fun stateWithSig(sigs: Set<String>) = GradesSyncState(
        enabled = true, intervalHours = 6, lastSyncAt = 0L, lastSeenSignature = sigs,
    )
    private fun mockPrefs(sigs: Set<String>) = mockk<GradesSyncPrefs> {
        coEvery { snapshot() } returns stateWithSig(sigs)
    }

    @Test fun `first run with empty lastSeen returns isFirstRun true and empty newEntries`() = runTest {
        val prefs = mockPrefs(emptySet())
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(entry("t", "A", "正常", "90")))
        assertEquals(true, r.isFirstRun)
        assertTrue(r.newEntries.isEmpty())
        assertEquals(setOf("t|A|正常|90"), r.fullSignature)
    }

    @Test fun `no new entries when current signatures all known`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|90"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(entry("t", "A", "正常", "90")))
        assertEquals(false, r.isFirstRun)
        assertTrue(r.newEntries.isEmpty())
    }

    @Test fun `new course produces new entry`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|90"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(
            entry("t", "A", "正常", "90"),
            entry("t", "B", "正常", "85"),
        ))
        assertEquals(1, r.newEntries.size)
        assertEquals("B", r.newEntries.single().courseCode)
    }

    @Test fun `score change on same course-attempt counts as new`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|80"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(entry("t", "A", "正常", "92")))
        assertEquals(1, r.newEntries.size)
        assertEquals("92", r.newEntries.single().score)
    }

    @Test fun `retake on same course produces new entry`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|55"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(
            entry("t", "A", "正常", "55"),
            entry("t", "A", "重修", "72"),
        ))
        assertEquals(1, r.newEntries.size)
        assertEquals("重修", r.newEntries.single().attemptType)
    }
}
