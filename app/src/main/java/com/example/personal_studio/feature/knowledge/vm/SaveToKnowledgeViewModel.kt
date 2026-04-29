package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.domain.knowledge.SaveToKnowledgeUseCase
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SaveToKnowledgeUiState {
    data object Idle : SaveToKnowledgeUiState
    data object Loading : SaveToKnowledgeUiState
    data class Preview(val draft: KbEntryDraft) : SaveToKnowledgeUiState
    data object Saving : SaveToKnowledgeUiState
    data class Saved(val entryId: Long) : SaveToKnowledgeUiState
    data class Error(val message: String, val canRetry: Boolean = true) : SaveToKnowledgeUiState
}

@HiltViewModel
class SaveToKnowledgeViewModel @Inject constructor(
    private val useCase: SaveToKnowledgeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SaveToKnowledgeUiState>(SaveToKnowledgeUiState.Idle)
    val uiState: StateFlow<SaveToKnowledgeUiState> = _uiState.asStateFlow()

    // Track in-flight work so double-taps + early dismiss don't race late emissions
    // back into the state flow after the user has moved on.
    private var draftJob: Job? = null
    private var commitJob: Job? = null

    fun startDraft(source: KbDraftSource) {
        draftJob?.cancel()
        _uiState.value = SaveToKnowledgeUiState.Loading
        draftJob = viewModelScope.launch {
            try {
                val draft = useCase.draft(source)
                _uiState.value = SaveToKnowledgeUiState.Preview(draft)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _uiState.value = SaveToKnowledgeUiState.Error(t.message ?: "Unknown error")
            }
        }
    }

    fun commit(draft: KbEntryDraft) {
        commitJob?.cancel()
        _uiState.value = SaveToKnowledgeUiState.Saving
        commitJob = viewModelScope.launch {
            try {
                val id = useCase.commit(draft)
                _uiState.value = SaveToKnowledgeUiState.Saved(id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _uiState.value = SaveToKnowledgeUiState.Error(t.message ?: "Save failed")
            }
        }
    }

    fun retry(source: KbDraftSource) = startDraft(source)

    /** Reset to Idle so the modal can be safely reopened. Cancels any in-flight work. */
    fun reset() {
        draftJob?.cancel()
        commitJob?.cancel()
        _uiState.value = SaveToKnowledgeUiState.Idle
    }
}
