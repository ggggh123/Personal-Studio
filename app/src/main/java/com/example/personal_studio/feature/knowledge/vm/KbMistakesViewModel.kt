package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class KbMistakesUiState(val entries: List<KbEntry> = emptyList())

@HiltViewModel
class KbMistakesViewModel @Inject constructor(
    repo: KnowledgeRepository,
) : ViewModel() {
    val uiState: StateFlow<KbMistakesUiState> = repo.observeMistakes()
        .map { KbMistakesUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbMistakesUiState())
}
