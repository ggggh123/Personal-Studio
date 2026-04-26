package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.knowledge.DeleteEntryUseCase
import com.example.personal_studio.domain.knowledge.RegenerateEntryUseCase
import com.example.personal_studio.domain.knowledge.UpdateEntryUseCase
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KbEntryDetailUiState {
    data object Loading : KbEntryDetailUiState
    data class Loaded(val entry: KbEntry, val related: List<KbEntry>) : KbEntryDetailUiState
    data object NotFound : KbEntryDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KbEntryDetailViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
    private val updateUseCase: UpdateEntryUseCase,
    private val deleteUseCase: DeleteEntryUseCase,
    private val regenerateUseCase: RegenerateEntryUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: 0L

    val uiState: StateFlow<KbEntryDetailUiState> = combine(
        repo.observeEntry(entryId),
        repo.observeRelated(entryId),
    ) { entry, related ->
        if (entry == null) KbEntryDetailUiState.NotFound
        else KbEntryDetailUiState.Loaded(entry, related)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbEntryDetailUiState.Loading)

    private val _busy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * Cold passthrough of category list for the CategoryPickerSheet. UI side
     * uses [collectAsStateWithLifecycle] which handles subscription lifecycle.
     */
    val observeCategoriesForUi = repo.observeCategories()

    fun rename(newTitle: String) = mutate { entry -> entry.copy(title = newTitle.trim().ifBlank { entry.title }) }

    fun changeCategory(newCategoryId: Long?) = mutate { entry -> entry.copy(categoryId = newCategoryId) }

    fun saveSummary(newMarkdown: String) = mutate { entry -> entry.copy(summaryMarkdown = newMarkdown) }

    fun saveStandardizedQuestion(newQuestion: String?) = mutate { entry ->
        entry.copy(standardizedQuestion = newQuestion?.takeIf { it.isNotBlank() })
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try { deleteUseCase(entryId) } finally { _busy.value = false }
            onDone()
        }
    }

    fun regenerate() {
        viewModelScope.launch {
            val current = (uiState.value as? KbEntryDetailUiState.Loaded)?.entry ?: return@launch
            _busy.value = true
            try { regenerateUseCase(current) } finally { _busy.value = false }
        }
    }

    /**
     * Helper for UI to upsert a brand-new category by name and use it immediately.
     * Returns the category ID synchronously to the suspend caller. UI can then
     * call [changeCategory] with the returned id.
     */
    suspend fun upsertCategoryAndUse(name: String): Long = repo.upsertCategory(name)

    private fun mutate(block: (KbEntry) -> KbEntry) {
        val current = (uiState.value as? KbEntryDetailUiState.Loaded)?.entry ?: return
        viewModelScope.launch {
            _busy.value = true
            try { updateUseCase(block(current)) } finally { _busy.value = false }
        }
    }
}
