package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.formatCredits
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.DeleteCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.UpdateCourseSeriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseSeriesEditUiState(
    val seriesId: Long = 0,
    val title: String = "",
    val instructor: String = "",
    val location: String = "",
    val notes: String = "",
    val credits: String = "",
    val occurrenceCount: Int = 0,
    val minWeek: Int = 0,
    val maxWeek: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
    val deleteDialogVisible: Boolean = false,
) {
    val saveEnabled: Boolean get() = !saving && title.trim().isNotEmpty()
}

sealed interface CourseSeriesEditEvent { object Closed : CourseSeriesEditEvent }

@HiltViewModel
class CourseSeriesEditViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val updateSeries: UpdateCourseSeriesUseCase,
    private val deleteSeries: DeleteCourseSeriesUseCase,
    private val cancel: CancelRemindersUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(CourseSeriesEditUiState())
    val ui: StateFlow<CourseSeriesEditUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<CourseSeriesEditEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CourseSeriesEditEvent> = _events.asSharedFlow()

    fun load(seriesId: Long) {
        viewModelScope.launch {
            val first = repo.firstOfSeries(seriesId)
            if (first == null) {
                _events.emit(CourseSeriesEditEvent.Closed)
                return@launch
            }
            val summary = repo.observeCourseSeriesList().first().firstOrNull { it.seriesId == seriesId }
            _ui.update {
                it.copy(
                    seriesId = seriesId,
                    title = first.title,
                    instructor = first.instructor.orEmpty(),
                    location = first.location.orEmpty(),
                    notes = first.notes.orEmpty(),
                    credits = summary?.credits?.let(::formatCredits) ?: first.credits?.let(::formatCredits) ?: "",
                    occurrenceCount = summary?.occurrenceCount ?: 0,
                    minWeek = summary?.minWeek ?: 0,
                    maxWeek = summary?.maxWeek ?: 0,
                )
            }
        }
    }

    fun onTitleChange(s: String) = _ui.update { it.copy(title = s) }
    fun onInstructorChange(s: String) = _ui.update { it.copy(instructor = s) }
    fun onLocationChange(s: String) = _ui.update { it.copy(location = s) }
    fun onNotesChange(s: String) = _ui.update { it.copy(notes = s) }
    fun onCreditsChange(s: String) = _ui.update { it.copy(credits = s, error = null) }

    fun save() {
        val s = _ui.value
        // Parse credits same rule as AddCourseScreen.
        val parsedCredits: Float? = if (s.credits.isBlank()) null else s.credits.toFloatOrNull()
        if (s.credits.isNotBlank() && (parsedCredits == null || parsedCredits < 0f)) {
            _ui.update { it.copy(error = "学分必须是非负数字") }
            return
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                updateSeries(s.seriesId, s.title, s.instructor, s.location, s.notes, parsedCredits)
            }.onSuccess { _ui.update { it.copy(saving = false) } }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun openDeleteDialog() = _ui.update { it.copy(deleteDialogVisible = true) }
    fun closeDeleteDialog() = _ui.update { it.copy(deleteDialogVisible = false) }

    fun confirmDelete(scope: DeleteCourseSeriesUseCase.Scope) {
        viewModelScope.launch {
            // Cancel reminders for all rows that are about to be deleted.
            val rows = repo.itemsForSeries(_ui.value.seriesId)
            val now = System.currentTimeMillis()
            val toCancel = if (scope == DeleteCourseSeriesUseCase.Scope.ALL) rows
                           else rows.filter { (it.endAt ?: it.startAt) > now }
            toCancel.forEach { cancel(it.id) }

            deleteSeries(_ui.value.seriesId, scope)
            _events.emit(CourseSeriesEditEvent.Closed)
        }
    }
}
