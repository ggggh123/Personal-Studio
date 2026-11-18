package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.llm.ApiFormat
import com.example.personal_studio.core.llm.BuiltInLlm
import com.example.personal_studio.core.llm.BundledLlm
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.LlmEndpoint
import com.example.personal_studio.core.llm.ReasoningLevel
import com.example.personal_studio.core.llm.activeEndpoint
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    /** Where requests go right now (built-in or custom). */
    val endpoint: LlmEndpoint? = null,
    /** The custom endpoint's saved values; the key itself is never shown, only whether one is saved. */
    val savedFormat: ApiFormat = ApiFormat.OPENAI,
    val savedApiKey: String? = null,
    val savedBaseUrl: String? = null,
    val savedModel: String? = null,
    val apiKeyDraft: String = "",
    val baseUrlDraft: String = "",
    val modelDraft: String = "",
    val generation: GenerationSettings = GenerationSettings(),
    val testConnection: TestConnectionState = TestConnectionState.Idle,
) {
    val custom: Boolean get() = endpoint?.custom == true
}

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Running : TestConnectionState
    data class Success(val replyPreview: String) : TestConnectionState
    data class Failure(val message: String) : TestConnectionState
}

@HiltViewModel
class SettingsViewModel internal constructor(
    private val prefs: UserPreferencesRepository,
    private val llm: LLMProvider,
    private val bundled: BundledLlm,
) : ViewModel() {

    @Inject constructor(prefs: UserPreferencesRepository, llm: LLMProvider) : this(prefs, llm, BuiltInLlm.bundled)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        prefs.activeEndpoint(bundled).onEach { e -> _uiState.update { it.copy(endpoint = e) } }.launchIn(viewModelScope)
        prefs.apiFormat.onEach { f -> _uiState.update { it.copy(savedFormat = ApiFormat.of(f)) } }.launchIn(viewModelScope)
        prefs.apiKey.onEach { k -> _uiState.update { it.copy(savedApiKey = k) } }.launchIn(viewModelScope)
        prefs.apiBaseUrl.onEach { u -> _uiState.update { it.copy(savedBaseUrl = u) } }.launchIn(viewModelScope)
        prefs.modelName.onEach { m -> _uiState.update { it.copy(savedModel = m) } }.launchIn(viewModelScope)
        prefs.generation.onEach { g -> _uiState.update { it.copy(generation = g) } }.launchIn(viewModelScope)
    }

    // Built-in models --------------------------------------------------------

    /** Picks a built-in model and drops the custom endpoint (address, key, format). */
    fun selectCuratedModel(code: String) {
        viewModelScope.launch {
            prefs.setModelName(code)
            prefs.setApiBaseUrl(null)
            prefs.setApiKey(null)
            prefs.setApiFormat(null)
        }
    }

    // Custom endpoint --------------------------------------------------------

    fun onFormatSelected(format: ApiFormat) {
        viewModelScope.launch { prefs.setApiFormat(if (format == ApiFormat.OPENAI) null else format.id) }
    }

    fun onApiKeyDraftChanged(value: String) = _uiState.update { it.copy(apiKeyDraft = value) }

    fun onSaveApiKey() {
        val key = _uiState.value.apiKeyDraft.trim()
        viewModelScope.launch {
            prefs.setApiKey(key)
            _uiState.update { it.copy(apiKeyDraft = "") }
        }
    }

    fun onClearApiKey() {
        viewModelScope.launch {
            prefs.setApiKey(null)
            _uiState.update { it.copy(apiKeyDraft = "") }
        }
    }

    fun onBaseUrlDraftChanged(value: String) = _uiState.update { it.copy(baseUrlDraft = value) }

    fun onSaveBaseUrl() {
        val url = _uiState.value.baseUrlDraft.trim().trimEnd('/')
        viewModelScope.launch {
            prefs.setApiBaseUrl(url)
            _uiState.update { it.copy(baseUrlDraft = "") }
        }
    }

    fun onResetBaseUrl() {
        viewModelScope.launch {
            prefs.setApiBaseUrl(null)
            _uiState.update { it.copy(baseUrlDraft = "") }
        }
    }

    fun onModelDraftChanged(value: String) = _uiState.update { it.copy(modelDraft = value) }

    fun onSaveModel() {
        val name = _uiState.value.modelDraft.trim()
        viewModelScope.launch {
            prefs.setModelName(name)
            _uiState.update { it.copy(modelDraft = "") }
        }
    }

    fun onResetModel() {
        viewModelScope.launch {
            prefs.setModelName(null)
            _uiState.update { it.copy(modelDraft = "") }
        }
    }

    // Generation settings ----------------------------------------------------

    fun onReasoningChanged(level: ReasoningLevel) = updateGeneration { it.copy(reasoning = level) }

    /** Null = automatic (each feature's own temperature). */
    fun onTemperatureChanged(value: Float?) = updateGeneration {
        it.copy(temperature = value?.let { v -> (Math.round(v.coerceIn(0f, GenerationSettings.TEMPERATURE_MAX) * 10) / 10f) })
    }

    fun onMaxTokensChanged(value: Int?) = updateGeneration { it.copy(maxTokens = value) }

    /** Null = not sent. */
    fun onTopPChanged(value: Float?) = updateGeneration {
        it.copy(topP = value?.let { v -> Math.round(v.coerceIn(0.05f, 1f) * 20) / 20f })
    }

    fun onResetGeneration() = updateGeneration { GenerationSettings() }

    private fun updateGeneration(change: (GenerationSettings) -> GenerationSettings) {
        val next = change(_uiState.value.generation)
        _uiState.update { it.copy(generation = next) }
        viewModelScope.launch { prefs.setGeneration(next) }
    }

    // Test connection --------------------------------------------------------

    fun onTestConnection() {
        _uiState.update { it.copy(testConnection = TestConnectionState.Running) }
        viewModelScope.launch {
            val accumulator = StringBuilder()
            llm.generateText("Reply with exactly the word 'pong' and nothing else.")
                .collect { chunk ->
                    when (chunk) {
                        is LlmChunk.Text -> accumulator.append(chunk.delta)
                        is LlmChunk.Done -> _uiState.update {
                            it.copy(testConnection = TestConnectionState.Success(replyPreview = accumulator.toString().take(200)))
                        }
                        is LlmChunk.Error -> _uiState.update {
                            it.copy(testConnection = TestConnectionState.Failure(chunk.message))
                        }
                    }
                }
        }
    }
}
