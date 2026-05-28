package com.example.personal_studio.feature.settings.vm

import androidx.datastore.preferences.core.Preferences
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

class DdlPollSettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun creds(present: Boolean): ImportCredentialPrefs = mockk(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(
            if (present) SavedCredentials("u", "p", NetworkMode.LOCAL) else null
        )
    }

    @Test fun `disabled when no credentials`() = runTest {
        val poll = mockk<DdlSyncPrefs>(relaxed = true) {
            every { observe } returns flowOf(DdlSyncState(false, 12, null, emptySet(), null))
        }
        val vm = DdlPollSettingsViewModel(poll, creds(false), mockk(relaxed = true))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.credsSaved)
        assertEquals(12, vm.uiState.value.intervalHours)
        job.cancel()
    }

    @Test fun `enabling persists and schedules`() = runTest {
        val state = MutableStateFlow(DdlSyncState(false, 12, null, emptySet(), null))
        val poll = mockk<DdlSyncPrefs>(relaxed = true) {
            every { observe } returns state
            coEvery { setEnabled(true) } coAnswers { state.value = state.value.copy(enabled = true); mockk<Preferences>(relaxed = true) }
            coEvery { snapshot() } coAnswers { state.value }
        }
        val scheduler = mockk<DdlPollScheduler>(relaxed = true)
        val vm = DdlPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEnableToggle(true)
        advanceUntilIdle()
        coVerify { poll.setEnabled(true) }
        verify { scheduler.enqueue(12) }
        job.cancel()
    }

    @Test fun `disabling cancels`() = runTest {
        val state = MutableStateFlow(DdlSyncState(true, 12, null, emptySet(), null))
        val poll = mockk<DdlSyncPrefs>(relaxed = true) {
            every { observe } returns state
            coEvery { setEnabled(false) } coAnswers { state.value = state.value.copy(enabled = false); mockk<Preferences>(relaxed = true) }
            coEvery { snapshot() } coAnswers { state.value }
        }
        val scheduler = mockk<DdlPollScheduler>(relaxed = true)
        val vm = DdlPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEnableToggle(false)
        advanceUntilIdle()
        verify { scheduler.cancel() }
        job.cancel()
    }
}
