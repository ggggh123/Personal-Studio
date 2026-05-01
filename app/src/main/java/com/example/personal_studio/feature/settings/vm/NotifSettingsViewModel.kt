package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.NotifPreferences
import com.example.personal_studio.data.local.datastore.NotifSwitches
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotifSettingsViewModel @Inject constructor(
    private val prefs: NotifPreferences,
) : ViewModel() {
    val switches: StateFlow<NotifSwitches> = prefs.switches.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), NotifSwitches(true, true, true, false),
    )

    fun toggleCourse(b: Boolean) = viewModelScope.launch { prefs.setCourse(b) }
    fun toggleTask(b: Boolean) = viewModelScope.launch { prefs.setTask(b) }
    fun toggleCustom(b: Boolean) = viewModelScope.launch { prefs.setCustom(b) }
}
