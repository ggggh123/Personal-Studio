package com.example.personal_studio.feature.scanner.doc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.scanner.AddPageToDocumentUseCase
import com.example.personal_studio.domain.scanner.CreateScanDocumentUseCase
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.FinalizeScanDocumentUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DocBuilderUiState(
    val docId: Long? = null,
    val pages: List<SavedPageSnapshot> = emptyList(),
    val mode: Mode = Mode.Idle,
    val toast: String? = null,
)

enum class Mode { Idle, CapturePending, Saving, Saved, Cancelled }

data class SavedPageSnapshot(
    val id: Long,
    val ordinal: Int,
    val enhancedImagePath: String,
    val filter: ScanFilter,
)

@HiltViewModel(assistedFactory = DocumentBuilderViewModel.Factory::class)
class DocumentBuilderViewModel @AssistedInject constructor(
    @Assisted private val resumeDocId: Long?,     // null = fresh doc
    private val repo: ScanRepository,
    private val createDoc: CreateScanDocumentUseCase,
    private val addPage: AddPageToDocumentUseCase,
    private val finalize: FinalizeScanDocumentUseCase,
    private val deleteDoc: DeleteScanDocumentUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DocBuilderUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val id = resumeDocId ?: createDoc(defaultTitle())
            _state.value = _state.value.copy(docId = id)
            // Stream the page list for this doc. Collect lives for the VM's
            // lifetime — updates flow in whenever onPageCaptured / deletes /
            // reorders touch the repository.
            repo.observePages(id).collect { pages ->
                _state.value = _state.value.copy(
                    pages = pages.map { p ->
                        SavedPageSnapshot(p.id, p.ordinal, p.enhancedImagePath, p.filter)
                    },
                )
            }
        }
    }

    fun onPageCaptured(
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ) = viewModelScope.launch {
        val id = _state.value.docId ?: return@launch
        addPage(id, originalImagePath, enhancedImagePath, filter, cornersJson)
    }

    fun finish() = viewModelScope.launch {
        val id = _state.value.docId ?: return@launch
        _state.value = _state.value.copy(mode = Mode.Saving)
        finalize(id)
        _state.value = _state.value.copy(mode = Mode.Saved)
    }

    fun cancel() = viewModelScope.launch {
        val id = _state.value.docId ?: return@launch
        deleteDoc(id)
        _state.value = _state.value.copy(mode = Mode.Cancelled)
    }

    private fun defaultTitle(): String =
        "scan_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())}"

    @AssistedFactory
    interface Factory {
        fun create(resumeDocId: Long?): DocumentBuilderViewModel
    }
}
