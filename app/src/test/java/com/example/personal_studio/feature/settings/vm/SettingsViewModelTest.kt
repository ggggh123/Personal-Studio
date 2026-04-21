package com.example.personal_studio.feature.settings.vm

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @Test
    fun `initial state shows empty key and idle test result`() = runTest {
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), FakeLLMProvider())
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
        val vm = SettingsViewModel(prefs, FakeLLMProvider())

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
        prefs.setOpenRouterApiKey("configured")
        val vm = SettingsViewModel(prefs, FakeLLMProvider(textChunks = listOf("pong")))
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
}
