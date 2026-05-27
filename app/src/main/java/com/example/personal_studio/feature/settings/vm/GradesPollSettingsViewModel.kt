package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesPollUiState(
    val credsSaved: Boolean = false,
    val enabled: Boolean = false,
    val intervalHours: Int = 6,
    val lastSyncAt: Long? = null,
)

@HiltViewModel
class GradesPollSettingsViewModel @Inject constructor(
    private val pollPrefs: GradesSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val scheduler: GradesPollScheduler,
) : ViewModel() {

    val uiState: StateFlow<GradesPollUiState> = combine(
        pollPrefs.observe, credPrefs.observeAll(),
    ) { poll: GradesSyncState, creds -> GradesPollUiState(
        credsSaved = creds != null,
        enabled = poll.enabled,
        intervalHours = poll.intervalHours,
        lastSyncAt = poll.lastSyncAt,
    ) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GradesPollUiState())

    fun onEnableToggle(on: Boolean) = viewModelScope.launch {
        pollPrefs.setEnabled(on)
        if (on) scheduler.enqueue(pollPrefs.snapshot().intervalHours) else scheduler.cancel()
    }

    fun onIntervalSelect(hours: Int) = viewModelScope.launch {
        pollPrefs.setIntervalHours(hours)
        if (pollPrefs.snapshot().enabled) scheduler.enqueue(hours)
    }
}
