package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.domain.model.CourseSeriesDraft
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.timeline.AddCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.CheckCourseConflictUseCase
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
import java.time.LocalDate
import javax.inject.Inject

data class AddCourseUiState(
    val title: String = "",
    val instructor: String = "",
    val location: String = "",
    val notes: String = "",
    val credits: String = "",
    val weekdays: Set<Int> = emptySet(),
    val periodStart: Int = 1,
    val periodEnd: Int = 1,
    val weekStart: Int = 1,
    val weekEnd: Int = 16,
    val maxPeriod: Int = 13,
    val maxWeek: Int = 30,
    val needsSemesterStart: Boolean = false,
    val semesterStart: LocalDate? = null,
    val conflicts: List<TimelineItem> = emptyList(),
    val saving: Boolean = false,
    val savedToast: String? = null,
    val error: String? = null,
) {
    val saveEnabled: Boolean get() {
        if (saving) return false
        if (title.isBlank()) return false
        if (weekdays.isEmpty()) return false
        if (periodStart < 1 || periodEnd < periodStart) return false
        if (weekStart < 1 || weekEnd < weekStart) return false
        if (semesterStart == null) return false
        return true
    }
}

sealed interface AddCourseEvent {
    data class Saved(val seriesId: Long, val count: Int) : AddCourseEvent
    object RequestNotifPermission : AddCourseEvent
}

@HiltViewModel
class AddCourseViewModel @Inject constructor(
    private val addCourse: AddCourseSeriesUseCase,
    private val checkConflict: CheckCourseConflictUseCase,
    private val semester: SemesterPreferences,
    private val timetable: TimetablePreferences,
    private val schedule: com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase,
    private val repo: com.example.personal_studio.data.repository.TimelineRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AddCourseUiState())
    val ui: StateFlow<AddCourseUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<AddCourseEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddCourseEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val periods = timetable.periods.first()
            val start = semester.startDate.first()
            _ui.update {
                it.copy(
                    maxPeriod = periods.maxOfOrNull { p -> p.index } ?: 13,
                    semesterStart = start,
                    needsSemesterStart = start == null,
                )
            }
        }
    }

    fun onSemesterStartPicked(date: LocalDate) {
        viewModelScope.launch {
            semester.setStartDate(date)
            _ui.update { it.copy(semesterStart = date, needsSemesterStart = false) }
        }
    }

    fun onTitleChange(s: String) = _ui.update { it.copy(title = s, conflicts = emptyList(), error = null) }
    fun onInstructorChange(s: String) = _ui.update { it.copy(instructor = s, error = null) }
    fun onLocationChange(s: String) = _ui.update { it.copy(location = s, error = null) }
    fun onNotesChange(s: String) = _ui.update { it.copy(notes = s, error = null) }
    fun onCreditsChange(s: String) = _ui.update { it.copy(credits = s, error = null) }
    fun onToggleWeekday(weekday: Int) = _ui.update {
        val next = if (weekday in it.weekdays) it.weekdays - weekday else it.weekdays + weekday
        it.copy(weekdays = next, conflicts = emptyList(), error = null)
    }
    fun onPeriodStart(p: Int) = _ui.update { it.copy(periodStart = p, periodEnd = maxOf(p, it.periodEnd), conflicts = emptyList(), error = null) }
    fun onPeriodEnd(p: Int) = _ui.update { it.copy(periodEnd = maxOf(p, it.periodStart), conflicts = emptyList(), error = null) }
    fun onWeekStart(w: Int) = _ui.update { it.copy(weekStart = w, weekEnd = maxOf(w, it.weekEnd), conflicts = emptyList(), error = null) }
    fun onWeekEnd(w: Int) = _ui.update { it.copy(weekEnd = maxOf(w, it.weekStart), conflicts = emptyList(), error = null) }

    fun save() {
        val s = _ui.value
        if (!s.saveEnabled) return
        // Parse credits: blank → null; non-blank must parse to a non-negative Float.
        val parsedCredits: Float? = if (s.credits.isBlank()) null else s.credits.toFloatOrNull()
        if (s.credits.isNotBlank() && (parsedCredits == null || parsedCredits < 0f)) {
            _ui.update { it.copy(error = "学分必须是非负数字") }
            return
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = CourseSeriesDraft(
                title = s.title.trim(),
                instructor = s.instructor.takeIf { it.isNotBlank() },
                location = s.location.takeIf { it.isNotBlank() },
                notes = s.notes.takeIf { it.isNotBlank() },
                credits = parsedCredits,
                weekdays = s.weekdays.sorted(),
                periodStart = s.periodStart, periodEnd = s.periodEnd,
                weekStart = s.weekStart, weekEnd = s.weekEnd,
            )
            // Conflict scan first — display chips but DO NOT block.
            val conflicts = checkConflict(draft)
            _ui.update { it.copy(conflicts = conflicts) }

            runCatching { addCourse(draft, s.semesterStart!!) }
                .onSuccess { (sid, count) ->
                    repo.itemsForSeries(sid).forEach { schedule(it) }
                    _ui.update {
                        it.copy(
                            saving = false,
                            savedToast = "已添加 $count 节${draft.title}",
                            // Reset form for the next course but keep semester + maxPeriod
                            title = "", instructor = "", location = "", notes = "",
                            credits = "",
                            weekdays = emptySet(),
                            periodStart = 1, periodEnd = 1,
                            weekStart = 1, weekEnd = 16,
                            conflicts = emptyList(),
                            error = null,
                        )
                    }
                    _events.emit(AddCourseEvent.RequestNotifPermission)
                    _events.emit(AddCourseEvent.Saved(sid, count))
                }
                .onFailure { t ->
                    _ui.update { it.copy(saving = false, error = t.message ?: "保存失败") }
                }
        }
    }

    fun consumedToast() = _ui.update { it.copy(savedToast = null) }
}
