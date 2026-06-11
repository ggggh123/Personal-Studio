package com.example.personal_studio.feature.scanner.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.RenameScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanLibraryUiState(
    val sort: SortMode = SortMode.TIME_DESC,
    val docs: List<ScanDocumentSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScanLibraryViewModel @Inject constructor(
    private val repo: ScanRepository,
    private val rename: RenameScanDocumentUseCase,
    private val delete: DeleteScanDocumentUseCase,
) : ViewModel() {

    private val sort = MutableStateFlow(SortMode.TIME_DESC)

    val uiState = sort
        .flatMapLatest { s ->
            repo.observeDocumentSummaries(s).map { docs -> ScanLibraryUiState(s, docs) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanLibraryUiState())

    fun setSort(mode: SortMode) { sort.value = mode }

    fun onRename(docId: Long, newTitle: String) = viewModelScope.launch {
        rename(docId, newTitle)
    }

    fun onDelete(docId: Long) = viewModelScope.launch {
        delete(docId)
    }
}
