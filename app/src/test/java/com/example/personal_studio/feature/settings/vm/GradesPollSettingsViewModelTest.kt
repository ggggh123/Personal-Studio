package com.example.personal_studio.feature.settings.vm

import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import androidx.datastore.preferences.core.Preferences
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class GradesPollSettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun creds(present: Boolean): ImportCredentialPrefs = mockk(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(
            if (present) SavedCredentials("u", "p", NetworkMode.LOCAL) else null
        )
    }

    @Test fun `disabled when no credentials saved`() = runTest {
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns flowOf(GradesSyncState(false, 6, null, emptySet()))
        }
        val vm = GradesPollSettingsViewModel(poll, creds(false), mockk(relaxed = true))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.credsSaved)
        assertEquals(false, vm.uiState.value.enabled)
        job.cancel()
    }

    @Test fun `enabling persists and schedules`() = runTest {
        val pollState = MutableStateFlow(GradesSyncState(false, 6, null, emptySet()))
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns pollState
            coEvery { setEnabled(true) } coAnswers {
                pollState.value = pollState.value.copy(enabled = true)
                mockk<Preferences>(relaxed = true)
            }
            coEvery { snapshot() } answers { pollState.value }
        }
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val vm = GradesPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEnableToggle(true)
        advanceUntilIdle()
        coVerify { poll.setEnabled(true) }
        verify { scheduler.enqueue(6) }
        job.cancel()
    }

    @Test fun `changing interval reschedules when enabled`() = runTest {
        val pollState = MutableStateFlow(GradesSyncState(true, 6, null, emptySet()))
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns pollState
            coEvery { setIntervalHours(12) } coAnswers {
                pollState.value = pollState.value.copy(intervalHours = 12)
                mockk<Preferences>(relaxed = true)
            }
            coEvery { snapshot() } answers { pollState.value }
        }
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val vm = GradesPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onIntervalSelect(12)
        advanceUntilIdle()
        coVerify { poll.setIntervalHours(12) }
        verify { scheduler.enqueue(12) }
        job.cancel()
    }

    @Test fun `disabling cancels schedule`() = runTest {
        val pollState = MutableStateFlow(GradesSyncState(true, 6, null, emptySet()))
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns pollState
            coEvery { setEnabled(false) } coAnswers {
                pollState.value = pollState.value.copy(enabled = false)
                mockk<Preferences>(relaxed = true)
            }
            coEvery { snapshot() } answers { pollState.value }
        }
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val vm = GradesPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEnableToggle(false)
        advanceUntilIdle()
        coVerify { poll.setEnabled(false) }
        verify { scheduler.cancel() }
        job.cancel()
    }
}
