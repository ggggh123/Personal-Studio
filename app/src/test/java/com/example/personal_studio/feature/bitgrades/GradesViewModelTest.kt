package com.example.personal_studio.feature.bitgrades

import com.example.personal_studio.data.local.datastore.GradesAnalysisCache
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
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
import org.junit.Before
import org.junit.Test

class GradesViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `book reflects dao grades`() = runTest {
        val dao = mockk<GradesDao> {
            every { observeAll() } returns flowOf(listOf(
                GradeEntryEntity(0,"2024-2025-2","24春","高数","M1",5.0,"92",4.0,"A","必修","正常",true,1L)))
            every { observeRanks() } returns flowOf(emptyList())
        }
        val cache = mockk<GradesAnalysisCache> { every { observe } returns flowOf(null) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(null)
        }
        val sync = mockk<SyncGradesUseCase>(relaxed = true)
        val vm = GradesViewModel(dao, ComputeGpaUseCase(), mockk(relaxed = true), mockk(relaxed = true), cache, sync, creds)
        // uiState is a stateIn(WhileSubscribed) — actively collect so combine runs.
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.book.terms.size)
        assertEquals(4.0, vm.uiState.value.book.overallGpa, 0.001)
        job.cancel()
    }

    @Test fun `excluding a course updates selected stats`() = runTest {
        val dao = mockk<GradesDao> {
            every { observeAll() } returns flowOf(listOf(
                GradeEntryEntity(1,"2024-2025-1","t","A","A",4.0,"90",4.0,null,null,"正常",true,1L),
                GradeEntryEntity(2,"2024-2025-1","t","B","B",2.0,"70",2.475,null,null,"正常",true,1L),
            ))
            every { observeRanks() } returns flowOf(emptyList())
        }
        val cache = mockk<GradesAnalysisCache> { every { observe } returns flowOf(null) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(null)
        }
        val sync = mockk<SyncGradesUseCase>(relaxed = true)
        val vm = GradesViewModel(dao, ComputeGpaUseCase(), mockk(relaxed = true), mockk(relaxed = true), cache, sync, creds)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.selectedCount)
        vm.onToggleCourse(2L)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedCount)
        assertEquals(4.0, vm.uiState.value.selectedGpa, 0.001) // only course A (gp 4.0) remains
        assertEquals(true, vm.uiState.value.filtering)
        job.cancel()
    }

    @Test fun `onSyncNow with saved creds runs sync and surfaces progress`() = runTest {
        val dao = mockk<GradesDao> {
            every { observeAll() } returns flowOf(emptyList())
            every { observeRanks() } returns flowOf(emptyList())
        }
        val cache = mockk<GradesAnalysisCache> { every { observe } returns flowOf(null) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val sync = mockk<SyncGradesUseCase> {
            every { syncAuto(any(), any()) } returns flowOf(SyncGradesStep.LoggingIn, SyncGradesStep.Done(1, 3))
        }
        val vm = GradesViewModel(dao, ComputeGpaUseCase(), mockk(relaxed = true), mockk(relaxed = true), cache, sync, creds)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.loggedIn)
        vm.onSyncNow()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.syncing)
        job.cancel()
    }
}
