package com.example.personal_studio.feature.scanner.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanPage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Minimal VM for the doc-expanded page grid inside
 * [ScanLibraryPickerScreen]. Does nothing but stream pages for [docId];
 * picking/copying is handled in the screen with ScanRepository on the
 * composable's injected repo callback.
 */
@HiltViewModel(assistedFactory = ScanPagePickerViewModel.Factory::class)
class ScanPagePickerViewModel @AssistedInject constructor(
    @Assisted private val docId: Long,
    repo: ScanRepository,
) : ViewModel() {

    val pages: StateFlow<List<ScanPage>> = repo.observePages(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @AssistedFactory
    interface Factory {
        fun create(docId: Long): ScanPagePickerViewModel
    }
}
