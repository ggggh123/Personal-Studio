package com.example.personal_studio.feature.settings.vm

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // Unconfined so that flows launched inside the VM's init execute eagerly,
    // keeping the test deterministic without explicit advanceUntilIdle() calls.
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // Constructs the VM with the existing prefs/llm under test and relaxed mocks
    // for the P8 logout deps (cred/sync prefs + pollers).
    private fun vm(
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        llm: FakeLLMProvider = FakeLLMProvider(),
    ) = SettingsViewModel(
        prefs, llm,
        mockk<ImportCredentialPrefs>(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
    )

    @Test
    fun `initial state shows empty key and idle test result`() = runTest {
        val vm = vm()
        vm.uiState.test {
            val first = awaitItem()
            assertEquals("", first.apiKeyDraft)
            assertEquals(null, first.savedApiKey)
            assertEquals(TestConnectionState.Idle, first.testConnection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving api key updates savedApiKey in state`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val vm = vm(prefs)

        vm.onApiKeyDraftChanged("abc123")
        vm.onSaveApiKey()

        vm.uiState.test {
            val state = awaitItem()
            if (state.savedApiKey == null) {
                val next = awaitItem()
                assertEquals("abc123", next.savedApiKey)
            } else {
                assertEquals("abc123", state.savedApiKey)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `testConnection transitions Idle to Running to Success on happy path`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        prefs.setApiKey("configured")
        val vm = vm(prefs, FakeLLMProvider(textChunks = listOf("pong")))
        vm.onTestConnection()

        vm.uiState.test {
            var reached = false
            while (!reached) {
                val s = awaitItem()
                if (s.testConnection is TestConnectionState.Success) {
                    assertTrue(s.testConnection.replyPreview.contains("pong"))
                    reached = true
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `logout clears creds and stops both pollers`() = runTest {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) }
        val gradesSync = mockk<GradesSyncPrefs>(relaxed = true)
        val ddlSync = mockk<DdlSyncPrefs>(relaxed = true)
        val gradesSched = mockk<GradesPollScheduler>(relaxed = true)
        val ddlSched = mockk<DdlPollScheduler>(relaxed = true)
        val vm = SettingsViewModel(mockk(relaxed = true), mockk(relaxed = true),
            credPrefs, gradesSync, ddlSync, gradesSched, ddlSched)
        vm.onLogout(); advanceUntilIdle()
        verify { credPrefs.clear() }
        coVerify { gradesSync.setEnabled(false) }
        coVerify { ddlSync.setEnabled(false) }
        verify { gradesSched.cancel() }
        verify { ddlSched.cancel() }
    }
}
