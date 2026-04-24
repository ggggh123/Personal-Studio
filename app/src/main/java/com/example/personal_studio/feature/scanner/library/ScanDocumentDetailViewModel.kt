package com.example.personal_studio.feature.scanner.library

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.scanner.ExportDocumentToPdfUseCase
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.ReorderPagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanDetailUiState(
    val doc: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val isExporting: Boolean = false,
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
    @ApplicationContext private val context: Context,
    private val repo: ScanRepository,
    private val reorderUc: ReorderPagesUseCase,
    private val removePageUc: RemovePageUseCase,
    private val exportUc: ExportDocumentToPdfUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanDetailUiState())
    val state = _state.asStateFlow()

    /**
     * One-shot share-sheet intent target. Set by [exportPdf] once the PDF
     * is written + wrapped in a FileProvider URI; the screen observes this
     * with LaunchedEffect and fires the system share chooser, then calls
     * [clearShareIntent] so the effect doesn't re-fire on config change.
     */
    private val _pendingShareUri = MutableStateFlow<Uri?>(null)
    val pendingShareUri = _pendingShareUri.asStateFlow()

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

    /**
     * Render the document's pages to a PDF and expose a content:// Uri
     * that the screen can feed into ACTION_SEND. isExporting flips the
     * action button into a disabled/"exporting" state for the duration.
     */
    fun exportPdf() = viewModelScope.launch {
        if (_state.value.isExporting) return@launch
        _state.value = _state.value.copy(isExporting = true)
        try {
            val file = exportUc(docId)
            _pendingShareUri.value = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        } finally {
            _state.value = _state.value.copy(isExporting = false)
        }
    }

    fun clearShareIntent() { _pendingShareUri.value = null }

    @AssistedFactory
    interface Factory {
        fun create(docId: Long): ScanDocumentDetailViewModel
    }
}
