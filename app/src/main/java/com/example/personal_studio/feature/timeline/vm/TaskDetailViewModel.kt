package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.timeline.DeleteItemUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
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

data class TaskDetailUiState(
    val item: TimelineItem? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

sealed interface TaskDetailEvent {
    object Closed : TaskDetailEvent
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val toggleDone: ToggleDoneUseCase,
    private val deleteItem: DeleteItemUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(TaskDetailUiState())
    val ui: StateFlow<TaskDetailUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<TaskDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TaskDetailEvent> = _events.asSharedFlow()

    fun load(itemId: Long) {
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val item = repo.findById(itemId)
            _ui.update { it.copy(item = item, loading = false, error = if (item == null) "条目已删除" else null) }
        }
    }

    fun onToggleDone() {
        val item = _ui.value.item ?: return
        viewModelScope.launch {
            runCatching { toggleDone(item.id, !item.isDone) }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
            load(item.id)
        }
    }

    fun onDelete() {
        val item = _ui.value.item ?: return
        viewModelScope.launch {
            deleteItem(item.id)
            _events.emit(TaskDetailEvent.Closed)
        }
    }
}
