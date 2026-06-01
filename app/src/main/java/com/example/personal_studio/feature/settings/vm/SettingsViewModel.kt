package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val apiKeyDraft: String = "",
    val savedApiKey: String? = null,
    val baseUrlDraft: String = "",
    val savedBaseUrl: String? = null,
    val modelDraft: String = "",
    val savedModel: String? = null,
    val testConnection: TestConnectionState = TestConnectionState.Idle,
)

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Running : TestConnectionState
    data class Success(val replyPreview: String) : TestConnectionState
    data class Failure(val message: String) : TestConnectionState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val llm: LLMProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        prefs.apiKey
            .onEach { saved -> _uiState.update { it.copy(savedApiKey = saved) } }
            .launchIn(viewModelScope)

        prefs.apiBaseUrl
            .onEach { saved -> _uiState.update { it.copy(savedBaseUrl = saved) } }
            .launchIn(viewModelScope)

        prefs.modelName
            .onEach { saved -> _uiState.update { it.copy(savedModel = saved) } }
            .launchIn(viewModelScope)
    }

    // API key ----------------------------------------------------------------

    fun onApiKeyDraftChanged(value: String) {
        _uiState.update { it.copy(apiKeyDraft = value) }
    }

    fun onSaveApiKey() {
        val key = _uiState.value.apiKeyDraft
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

    // Base URL ---------------------------------------------------------------

    fun onBaseUrlDraftChanged(value: String) {
        _uiState.update { it.copy(baseUrlDraft = value) }
    }

    fun onSaveBaseUrl() {
        val url = _uiState.value.baseUrlDraft.trim()
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

    // Model ------------------------------------------------------------------

    fun onModelDraftChanged(value: String) {
        _uiState.update { it.copy(modelDraft = value) }
    }

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
                            it.copy(
                                testConnection = TestConnectionState.Success(
                                    replyPreview = accumulator.toString().take(200),
                                )
                            )
                        }
                        is LlmChunk.Error -> _uiState.update {
                            it.copy(testConnection = TestConnectionState.Failure(chunk.message))
                        }
                    }
                }
        }
    }
}
