package com.example.personal_studio.feature.bitgrades

import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GradesSyncViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun creds() = mockk<com.example.personal_studio.data.local.datastore.ImportCredentialPrefs>(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(null)
    }

    @Test fun `failed login surfaces error`() = runTest {
        val sync = mockk<SyncGradesUseCase> {
            every { sync(any()) } returns flowOf(
                SyncGradesStep.LoggingIn,
                SyncGradesStep.Failed(GradesSyncError.WrongCredentials),
            )
        }
        val vm = GradesSyncViewModel(sync, creds())
        vm.onUsernameChange("u"); vm.onPasswordChange("p")
        vm.onSync()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is GradesSyncError.WrongCredentials)
    }

    @Test fun `done sets done flag`() = runTest {
        val sync = mockk<SyncGradesUseCase> {
            every { sync(any()) } returns flowOf(
                SyncGradesStep.LoggingIn, SyncGradesStep.FetchingGrades,
                SyncGradesStep.Persisting, SyncGradesStep.Done(2, 10),
            )
        }
        val vm = GradesSyncViewModel(sync, creds())
        vm.onUsernameChange("u"); vm.onPasswordChange("p"); vm.onSync()
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.done)
    }
}
