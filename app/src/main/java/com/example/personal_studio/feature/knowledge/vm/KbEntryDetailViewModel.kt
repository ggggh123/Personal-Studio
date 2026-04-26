package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
}
