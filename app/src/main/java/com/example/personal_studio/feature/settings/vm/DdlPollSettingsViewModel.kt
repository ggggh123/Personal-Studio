package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DdlPollUiState(
    val credsSaved: Boolean = false,
    val enabled: Boolean = false,
    val intervalHours: Int = 12,
    val lastSyncAt: Long? = null,
)

@HiltViewModel
class DdlPollSettingsViewModel @Inject constructor(
    private val pollPrefs: DdlSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val scheduler: DdlPollScheduler,
) : ViewModel() {

    val uiState: StateFlow<DdlPollUiState> = combine(
        pollPrefs.observe, credPrefs.observeAll(),
    ) { poll: DdlSyncState, creds -> DdlPollUiState(
        credsSaved = creds != null,
        enabled = poll.enabled,
        intervalHours = poll.intervalHours,
        lastSyncAt = poll.lastSyncAt,
    ) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DdlPollUiState())

    fun onEnableToggle(on: Boolean) = viewModelScope.launch {
        pollPrefs.setEnabled(on)
        if (on) scheduler.enqueue(pollPrefs.snapshot().intervalHours) else scheduler.cancel()
    }

    fun onIntervalSelect(hours: Int) = viewModelScope.launch {
        pollPrefs.setIntervalHours(hours)
        if (pollPrefs.snapshot().enabled) scheduler.enqueue(hours)
    }
}
