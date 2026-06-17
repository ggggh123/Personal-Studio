package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitddl.SyncAssignmentsUseCase
import com.example.personal_studio.domain.bitddl.model.DdlSyncStep
import com.example.personal_studio.domain.bitexam.SyncExamsUseCase
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import com.example.personal_studio.domain.bitimport.model.ImportResult
import com.example.personal_studio.domain.bitimport.model.ImportStep
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.domain.bitimport.model.SyncSourceStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAllUseCaseTest {

    private val credPrefs = mockk<ImportCredentialPrefs>()
    private val resolveMode = mockk<ResolveNetworkModeUseCase>()
    private val importCourses = mockk<ImportCoursesUseCase>()
    private val syncAssignments = mockk<SyncAssignmentsUseCase>()
    private val syncExams = mockk<SyncExamsUseCase>()
    private val syncGrades = mockk<SyncGradesUseCase>()

    private fun useCase() =
        SyncAllUseCase(credPrefs, resolveMode, importCourses, syncAssignments, syncExams, syncGrades)

    private fun stubCreds(creds: SavedCredentials?) {
        every { credPrefs.observeAll() } returns MutableStateFlow(creds)
        every { credPrefs.save(any(), any(), any()) } just Runs
        coEvery { resolveMode.invoke(any()) } returns NetworkMode.LOCAL
    }

    private fun stubAllOk() {
        every { importCourses.importAuto(any(), any(), any()) } returns
            flowOf(ImportStep.Done(ImportResult(32, 0, "2025-2026-2")))
        every { syncAssignments.syncAuto(any(), any()) } returns flowOf(DdlSyncStep.Done(12, 3))
        every { syncExams.syncAuto(any(), any()) } returns flowOf(ExamSyncStep.Done(5))
        every { syncGrades.syncAuto(any(), any()) } returns flowOf(SyncGradesStep.Done(2, 40))
    }

    @Test fun `all sources succeed`() = runTest {
        stubCreds(SavedCredentials("u", "p", NetworkMode.LOCAL))
        stubAllOk()

        val last = useCase().run().toList().last()

        assertTrue(last.done)
        assertEquals(SyncSourceStatus.OK, last.states[SyncSource.COURSES]?.status)
        assertEquals(SyncSourceStatus.OK, last.states[SyncSource.DDL]?.status)
        assertEquals(SyncSourceStatus.OK, last.states[SyncSource.EXAMS]?.status)
        assertEquals(SyncSourceStatus.OK, last.states[SyncSource.GRADES]?.status)
    }

    @Test fun `one source fails, others still OK`() = runTest {
        stubCreds(SavedCredentials("u", "p", NetworkMode.LOCAL))
        stubAllOk()
        every { syncGrades.syncAuto(any(), any()) } returns
            flowOf(SyncGradesStep.Failed(GradesSyncError.EmptyGrades))

        val last = useCase().run().toList().last()

        assertTrue(last.done)
        assertEquals(SyncSourceStatus.OK, last.states[SyncSource.COURSES]?.status)
        assertEquals(SyncSourceStatus.OK, last.states[SyncSource.EXAMS]?.status)
        assertEquals(SyncSourceStatus.FAILED, last.states[SyncSource.GRADES]?.status)
    }

    @Test fun `no credentials emits terminal noCredentials`() = runTest {
        stubCreds(null)

        val last = useCase().run().toList().last()

        assertTrue(last.done)
        assertTrue(last.noCredentials)
    }
}
