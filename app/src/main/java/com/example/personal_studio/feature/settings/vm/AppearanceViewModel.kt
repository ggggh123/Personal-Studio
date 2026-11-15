package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.DebugMoment
import com.example.personal_studio.data.local.datastore.ThemePreferences
import com.example.personal_studio.ui.theme.daylight.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val prefs: ThemePreferences,
) : ViewModel() {

    val mode: StateFlow<ThemeMode> =
        prefs.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DAYLIGHT)

    val debugMoment: StateFlow<DebugMoment?> =
        prefs.debugMoment.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setMode(mode) }
    }

    fun setDebugMoment(moment: DebugMoment?) {
        viewModelScope.launch { prefs.setDebugMoment(moment) }
    }
}
