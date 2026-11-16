package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.RecalculateCoursesAfterTimetableChangeUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimetableEditorUiState(
    val periods: List<TimetablePeriod> = emptyList(),
    val saving: Boolean = false,
    val confirmDialogVisible: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TimetableEditorViewModel @Inject constructor(
    private val prefs: TimetablePreferences,
    private val repo: TimelineRepository,
    private val recalc: RecalculateCoursesAfterTimetableChangeUseCase,
    private val cancel: CancelRemindersUseCase,
    private val schedule: ScheduleRemindersUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(TimetableEditorUiState())
    val ui: StateFlow<TimetableEditorUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(periods = prefs.periods.first()) }
        }
    }

    fun onPeriodChange(index: Int, startHHmm: String?, endHHmm: String?) {
        _ui.update { st ->
            st.copy(periods = st.periods.map {
                if (it.index == index) it.copy(
                    startHHmm = startHHmm ?: it.startHHmm,
                    endHHmm = endHHmm ?: it.endHHmm,
                ) else it
            })
        }
    }

    fun onAddRow() {
        _ui.update { st ->
            val last = st.periods.maxByOrNull { it.index }
            val nextIdx = (last?.index ?: 0) + 1
            // A new period starts 10 minutes after the last one ends and lasts 45 minutes, so it is valid as added.
            val start = (last?.let { minutesOf(it.endHHmm) } ?: (8 * 60 - 10)) + 10
            st.copy(periods = st.periods + TimetablePeriod(nextIdx, hhmm(start), hhmm(start + 45)))
        }
    }

    fun onRemoveLast() {
        viewModelScope.launch {
            val st = _ui.value
            if (st.periods.isEmpty()) return@launch
            val toRemove = st.periods.maxByOrNull { it.index }?.index ?: return@launch
            val refs = repo.countFutureCoursesUsingPeriodRange(toRemove, toRemove, System.currentTimeMillis())
            if (refs > 0) {
                _ui.update { it.copy(error = "第 $toRemove 节还有 $refs 次课要上，先删除或调整这些课程") }
                return@launch
            }
            _ui.update { it.copy(periods = it.periods.filter { p -> p.index != toRemove }) }
        }
    }

    fun onResetDefault() = _ui.update { it.copy(periods = DefaultTimetable.PERIODS) }

    fun openConfirmDialog() {
        // Validation: each end > start, no inter-period overlap
        val sorted = _ui.value.periods.sortedBy { it.index }
        var prevEnd = ""
        for (p in sorted) {
            if (p.startHHmm >= p.endHHmm) {
                _ui.update { it.copy(error = "第 ${p.index} 节的下课时间要晚于上课时间") }
                return
            }
            if (prevEnd.isNotEmpty() && p.startHHmm < prevEnd) {
                _ui.update { it.copy(error = "第 ${p.index} 节在上一节下课（$prevEnd）之前就开始了") }
                return
            }
            prevEnd = p.endHHmm
        }
        _ui.update { it.copy(confirmDialogVisible = true) }
    }

    fun closeConfirmDialog() = _ui.update { it.copy(confirmDialogVisible = false) }

    fun save(onComplete: (touchedIds: List<Long>) -> Unit) {
        _ui.update { it.copy(saving = true, confirmDialogVisible = false) }
        viewModelScope.launch {
            runCatching {
                prefs.setPeriods(_ui.value.periods)
                val out = mutableListOf<Long>()
                recalc(_ui.value.periods, out)
                out
            }.onSuccess { touched ->
                _ui.update { it.copy(saving = false) }
                touched.forEach { id ->
                    cancel(id)
                    val item = repo.findById(id)
                    if (item != null) schedule(item)
                }
                onComplete(touched)
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, error = e.message ?: "更新失败") }
            }
        }
    }

    fun consumedError() = _ui.update { it.copy(error = null) }

    private fun minutesOf(hhmm: String): Int =
        hhmm.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    private fun hhmm(minutes: Int): String {
        val m = minutes.coerceIn(0, 23 * 60 + 59)
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
