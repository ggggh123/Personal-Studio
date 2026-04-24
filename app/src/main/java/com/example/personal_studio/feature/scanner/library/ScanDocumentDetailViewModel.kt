package com.example.personal_studio.feature.scanner.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.ReorderPagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanDetailUiState(
    val doc: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
)

/**
 * Owns the detail view of a finalized (or pending) scan document.
 * Streams doc + pages from the repository and exposes reorder + per-page
 * ops. Rename/delete-doc live on the library row's long-press dialog, not
 * here — consolidating those entry points avoids duplicate UX paths.
 *
 * Keyed by docId via @AssistedInject so each detail screen instance
 * scopes to its own document.
 */
@HiltViewModel(assistedFactory = ScanDocumentDetailViewModel.Factory::class)
class ScanDocumentDetailViewModel @AssistedInject constructor(
    @Assisted private val docId: Long,
    private val repo: ScanRepository,
    private val reorderUc: ReorderPagesUseCase,
    private val removePageUc: RemovePageUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanDetailUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeDocument(docId).collect { doc ->
                _state.value = _state.value.copy(doc = doc)
            }
        }
        viewModelScope.launch {
            repo.observePages(docId).collect { pages ->
                _state.value = _state.value.copy(pages = pages)
            }
        }
    }

    /** Persists [orderedIds] as the new page order. No-op on empty input. */
    fun reorderPages(orderedIds: List<Long>) = viewModelScope.launch {
        if (orderedIds.isNotEmpty()) reorderUc(docId, orderedIds)
    }

    fun deletePage(pageId: Long) = viewModelScope.launch { removePageUc(pageId) }

    @AssistedFactory
    interface Factory {
        fun create(docId: Long): ScanDocumentDetailViewModel
    }
}
