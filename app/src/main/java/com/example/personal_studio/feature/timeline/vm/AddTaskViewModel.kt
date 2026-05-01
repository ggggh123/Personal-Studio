package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.AddTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTaskUiState(
    val type: TimelineType = TimelineType.TASK,
    val title: String = "",
    val description: String = "",
    val startAtEpoch: Long? = null,
    val endAtEpoch: Long? = null,   // null while user has not set; only used for CUSTOM
    val location: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val saveEnabled: Boolean get() {
        if (saving) return false
        if (title.isBlank()) return false
        if (startAtEpoch == null) return false
        if (type == TimelineType.CUSTOM) {
            val end = endAtEpoch ?: return false
            if (end <= startAtEpoch) return false
        }
        return true
    }
}

sealed interface AddTaskEvent {
    data class Saved(val itemId: Long) : AddTaskEvent
    object RequestNotifPermission : AddTaskEvent
}

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val addTask: AddTaskUseCase,
    private val schedule: com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase,
    private val repo: com.example.personal_studio.data.repository.TimelineRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AddTaskUiState())
    val ui: StateFlow<AddTaskUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<AddTaskEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddTaskEvent> = _events.asSharedFlow()

    fun onTypeChange(t: TimelineType) = _ui.update { it.copy(type = t, error = null) }
    fun onTitleChange(s: String) = _ui.update { it.copy(title = s, error = null) }
    fun onDescChange(s: String) = _ui.update { it.copy(description = s, error = null) }
    fun onStartChange(epoch: Long?) = _ui.update { it.copy(startAtEpoch = epoch, error = null) }
    fun onEndChange(epoch: Long?) = _ui.update { it.copy(endAtEpoch = epoch, error = null) }
    fun onLocationChange(s: String) = _ui.update { it.copy(location = s, error = null) }

    fun save() {
        val s = _ui.value
        if (!s.saveEnabled) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                addTask(
                    type = s.type,
                    title = s.title,
                    description = s.description.takeIf { it.isNotBlank() },
                    startAt = s.startAtEpoch!!,
                    endAt = if (s.type == TimelineType.CUSTOM) s.endAtEpoch else null,
                    location = s.location.takeIf { it.isNotBlank() },
                )
            }.onSuccess { id ->
                val saved = repo.findById(id)
                if (saved != null) schedule(saved)
                _ui.update { it.copy(saving = false) }
                _events.emit(AddTaskEvent.RequestNotifPermission)
                _events.emit(AddTaskEvent.Saved(id))
            }.onFailure { t ->
                _ui.update { it.copy(saving = false, error = t.message ?: "保存失败") }
            }
        }
    }
}
