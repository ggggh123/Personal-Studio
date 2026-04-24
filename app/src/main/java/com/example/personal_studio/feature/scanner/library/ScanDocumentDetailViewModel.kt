package com.example.personal_studio.feature.scanner.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.RenameScanDocumentUseCase
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
    val reorderMode: Boolean = false,
    val reorderDraft: List<Long> = emptyList(),
)

/**
 * Owns the detail view of a finalized (or pending) scan document.
 * Streams doc + pages from the repository, tracks a local reorder draft
 * that is only persisted on commit, and exposes single-shot doc/page ops.
 *
 * Keyed by docId via @AssistedInject so each detail screen instance
 * scopes to its own document without re-creating the host nav entry.
 */
@HiltViewModel(assistedFactory = ScanDocumentDetailViewModel.Factory::class)
class ScanDocumentDetailViewModel @AssistedInject constructor(
    @Assisted private val docId: Long,
    private val repo: ScanRepository,
    private val reorderUc: ReorderPagesUseCase,
    private val removePageUc: RemovePageUseCase,
    private val renameUc: RenameScanDocumentUseCase,
    private val deleteDocUc: DeleteScanDocumentUseCase,
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

    fun enterReorder() {
        _state.value = _state.value.copy(
            reorderMode = true,
            reorderDraft = _state.value.pages.map { it.id },
        )
    }

    fun cancelReorder() {
        _state.value = _state.value.copy(reorderMode = false, reorderDraft = emptyList())
    }

    fun moveInDraft(fromIdx: Int, toIdx: Int) {
        val draft = _state.value.reorderDraft.toMutableList()
        val id = draft.removeAt(fromIdx)
        draft.add(toIdx, id)
        _state.value = _state.value.copy(reorderDraft = draft)
    }

    fun commitReorder() = viewModelScope.launch {
        val draft = _state.value.reorderDraft
        if (draft.isNotEmpty()) reorderUc(docId, draft)
        _state.value = _state.value.copy(reorderMode = false, reorderDraft = emptyList())
    }

    fun deletePage(pageId: Long) = viewModelScope.launch { removePageUc(pageId) }

    fun renameDoc(title: String) = viewModelScope.launch { renameUc(docId, title) }

    fun deleteDoc() = viewModelScope.launch { deleteDocUc(docId) }

    @AssistedFactory
    interface Factory {
        fun create(docId: Long): ScanDocumentDetailViewModel
    }
}
