package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SemesterSettingsViewModel @Inject constructor(
    private val prefs: SemesterPreferences,
) : ViewModel() {
    val current: StateFlow<LocalDate?> = prefs.startDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setStart(date: LocalDate) {
        viewModelScope.launch { prefs.setStartDate(date) }
    }
}
