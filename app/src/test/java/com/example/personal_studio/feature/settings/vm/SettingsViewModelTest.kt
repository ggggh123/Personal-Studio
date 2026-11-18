package com.example.personal_studio.feature.settings.vm

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    private fun vm(
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        llm: FakeLLMProvider = FakeLLMProvider(),
    ) = SettingsViewModel(prefs, llm)

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

    @Test
    fun `generation settings are saved and reset`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val vm = vm(prefs)
        vm.onReasoningChanged(com.example.personal_studio.core.llm.ReasoningLevel.HIGH)
        vm.onTemperatureChanged(0.73f)
        vm.onMaxTokensChanged(8192)
        vm.onTopPChanged(0.92f)
        val saved = prefs.generation.first()
        assertEquals(com.example.personal_studio.core.llm.ReasoningLevel.HIGH, saved.reasoning)
        assertEquals(0.7f, saved.temperature!!, 0.0001f)
        assertEquals(8192, saved.maxTokens)
        assertEquals(0.9f, saved.topP!!, 0.0001f)
        assertEquals(saved, vm.uiState.value.generation)
        vm.onResetGeneration()
        assertEquals(com.example.personal_studio.core.llm.GenerationSettings(), prefs.generation.first())
    }

    @Test
    fun `picking a built-in model drops the whole custom endpoint`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val vm = vm(prefs)
        vm.onFormatSelected(com.example.personal_studio.core.llm.ApiFormat.ANTHROPIC)
        prefs.setApiBaseUrl("https://api.anthropic.com")
        prefs.setApiKey("sk-ant")
        assertTrue(vm.uiState.value.custom)
        assertEquals(com.example.personal_studio.core.llm.ApiFormat.ANTHROPIC, vm.uiState.value.savedFormat)

        vm.selectCuratedModel("deepseek-v4-pro")
        assertEquals(null, prefs.apiFormat.first())
        assertEquals(null, prefs.apiBaseUrl.first())
        assertEquals(null, prefs.apiKey.first())
        assertEquals(false, vm.uiState.value.custom)
        assertEquals("deepseek-v4-pro", vm.uiState.value.endpoint!!.model)
    }
}
