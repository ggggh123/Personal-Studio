package com.example.personal_studio.feature.profile

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.auth.LogoutUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProfileViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_000_000_000_000L

    private fun ddl(id: Long, start: Long, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.TASK, title = "D$id", startAt = start, endAt = null,
        isDone = done, sourceType = TimelineSource.IMPORTED_LEXUE, sourceExternalId = "d$id",
        createdAt = 1L, updatedAt = 1L,
    )
    private fun exam(id: Long, start: Long) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "E$id", startAt = start, endAt = start + 7200_000L,
        isDone = false, sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = "e$id",
        createdAt = 1L, updatedAt = 1L,
    )

    private fun vm(
        creds: SavedCredentials? = null,
        book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null),
        ddls: List<TimelineItemEntity> = emptyList(),
        exams: List<TimelineItemEntity> = emptyList(),
        grades: GradesSyncState = GradesSyncState(false, 6, null, emptySet()),
        ddlSync: DdlSyncState = DdlSyncState(false, 12, null, emptySet(), null),
        logout: LogoutUseCase = mockk(relaxed = true),
    ): ProfileViewModel {
        val gradesDao = mockk<GradesDao>(relaxed = true) {
            every { observeAll() } returns flowOf(emptyList())
            every { observeRanks() } returns flowOf(emptyList())
        }
        val timelineDao = mockk<TimelineDao>(relaxed = true) {
            every { observeLexueDdls() } returns flowOf(ddls)
            every { observeImportedExams() } returns flowOf(exams)
        }
        val computeGpa = mockk<ComputeGpaUseCase> { every { this@mockk.invoke(any(), any()) } returns book }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds)
        }
        val gradesSyncPrefs = mockk<GradesSyncPrefs>(relaxed = true) { every { observe } returns flowOf(grades) }
        val ddlSyncPrefs = mockk<DdlSyncPrefs>(relaxed = true) { every { observe } returns flowOf(ddlSync) }
        return ProfileViewModel(
            credPrefs, gradesDao, computeGpa, timelineDao, gradesSyncPrefs, ddlSyncPrefs, logout,
            nowProvider = { now },
        )
    }

    @Test fun `logged out state`() = runTest {
        val vm = vm(creds = null)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.loggedIn)
        assertNull(vm.uiState.value.username)
        job.cancel()
    }

    @Test fun `logged in maps username and network mode`() = runTest {
        val vm = vm(creds = SavedCredentials("1120243640", "pw", NetworkMode.LOCAL))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.loggedIn)
        assertEquals("1120243640", vm.uiState.value.username)
        assertEquals(NetworkMode.LOCAL, vm.uiState.value.networkMode)
        job.cancel()
    }

    @Test fun `ddl count is unfinished and future only`() = runTest {
        val vm = vm(ddls = listOf(
            ddl(1, now + 3600_000L, false),
            ddl(2, now - 3600_000L, false),
            ddl(3, now + 3600_000L, true),
        ))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.ddlCount)
        job.cancel()
    }

    @Test fun `exam count is upcoming only`() = runTest {
        val vm = vm(exams = listOf(
            exam(1, now + 2 * 3600_000L),
            exam(2, now - 5 * 3600_000L),
        ))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.examCount)
        job.cancel()
    }

    @Test fun `gpa null when no grades, value when present`() = runTest {
        val empty = vm(book = GradeBook(emptyList(), 0.0, 0.0, null, overallRank = null))
        val j1 = launch { empty.uiState.collect {} }; advanceUntilIdle()
        assertNull(empty.uiState.value.gpa); j1.cancel()

        val withGpa = vm(book = GradeBook(listOf(mockk(relaxed = true)), 3.82, 100.0, null, overallRank = null))
        val j2 = launch { withGpa.uiState.collect {} }; advanceUntilIdle()
        assertEquals(3.82, withGpa.uiState.value.gpa!!, 1e-9); j2.cancel()
    }

    @Test fun `poll states mapped`() = runTest {
        val vm = vm(
            grades = GradesSyncState(true, 6, null, emptySet()),
            ddlSync = DdlSyncState(true, 12, null, emptySet(), null),
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.gradesPollEnabled)
        assertEquals(6, vm.uiState.value.gradesPollInterval)
        assertEquals(true, vm.uiState.value.ddlPollEnabled)
        job.cancel()
    }

    @Test fun `onLogout delegates to use case`() = runTest {
        val logout = mockk<LogoutUseCase>(relaxed = true)
        val vm = vm(logout = logout)
        val job = launch { vm.uiState.collect {} }
        vm.onLogout(); advanceUntilIdle()
        coVerify { logout.invoke() }
        job.cancel()
    }
}
