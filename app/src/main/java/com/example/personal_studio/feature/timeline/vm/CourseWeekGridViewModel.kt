package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * UI state for the 7×N traditional course-table view.
 *
 * - [displayWeekIndex]: 1-based week relative to [SemesterPreferences.startDate].
 * - [coursesByCell]: keyed by (weekday 1..7, periodIndex) using the START period.
 *   Multi-period rows still appear once and the renderer computes the visual span
 *   from `(periodIndex, periodEndIndex)`.
 */
data class CourseWeekGridUiState(
    val loading: Boolean = true,
    val needsSemesterStart: Boolean = false,
    val semesterStart: LocalDate? = null,
    val displayWeekIndex: Int = 1,
    val weekStart: LocalDate = LocalDate.now(),
    val weekEnd: LocalDate = LocalDate.now(),
    val isCurrentWeek: Boolean = true,
    val periods: List<TimetablePeriod> = emptyList(),
    val coursesByCell: Map<Pair<Int, Int>, TimelineItem> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CourseWeekGridViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val semester: SemesterPreferences,
    private val timetable: TimetablePreferences,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** 1-based week to display. */
    private val displayWeekIndex = MutableStateFlow(1)

    private data class Bootstrap(
        val semesterStart: LocalDate?,
        val periods: List<TimetablePeriod>,
    )

    /**
     * Reactive bootstrap: re-emits whenever the semester start or period table
     * changes. Critical so that after an import (or manual pick) writes the
     * anchor, this grid flips out of the "needs semester start" state live —
     * even if the screen's ViewModel survived on the back stack.
     */
    private val bootstrap: StateFlow<Bootstrap?> =
        combine(semester.startDate, timetable.periods) { start, periods ->
            Bootstrap(start, periods)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Position at the current week the first time a non-null start arrives.
        viewModelScope.launch {
            val start = semester.startDate.first { it != null } ?: return@launch
            val today = LocalDate.now(zone)
            val days = ChronoUnit.DAYS.between(start, today)
            displayWeekIndex.value = ((days / 7L).toInt() + 1).coerceAtLeast(1)
        }
    }

    val uiState: StateFlow<CourseWeekGridUiState> =
        bootstrap.flatMapLatest { boot ->
            displayWeekIndex.flatMapLatest { weekIdx ->
                val semesterStart = boot?.semesterStart
                val periods = boot?.periods ?: emptyList()
                if (semesterStart == null) {
                    kotlinx.coroutines.flow.flowOf(
                        CourseWeekGridUiState(
                            loading = boot == null,
                            needsSemesterStart = boot != null,
                            semesterStart = null,
                            displayWeekIndex = weekIdx,
                            periods = periods,
                        )
                    )
                } else {
                    val ws = semesterStart.plusWeeks((weekIdx - 1).toLong())
                    val we = ws.plusDays(6)
                    val startEpoch = ws.atStartOfDay(zone).toInstant().toEpochMilli()
                    val endEpoch = we.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val today = LocalDate.now(zone)
                    val isCurrent = !today.isBefore(ws) && !today.isAfter(we)
                    repo.observeItemsInRange(startEpoch, endEpoch).map { rows ->
                        val courseRows = rows.filter { it.type == TimelineType.COURSE }
                        val byCell: Map<Pair<Int, Int>, TimelineItem> = courseRows
                            .filter { it.weekdayCode != null && it.periodIndex != null }
                            .associateBy { it.weekdayCode!! to it.periodIndex!! }
                        CourseWeekGridUiState(
                            loading = false,
                            needsSemesterStart = false,
                            semesterStart = semesterStart,
                            displayWeekIndex = weekIdx,
                            weekStart = ws,
                            weekEnd = we,
                            isCurrentWeek = isCurrent,
                            periods = periods,
                            coursesByCell = byCell,
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseWeekGridUiState())

    /** 手设兜底:持久化用户选的学期起始日(已由 SemesterStartModal 归一到周一)。
     *  响应式 bootstrap 会随之刷新,课表立即渲染。 */
    fun onSemesterStartPicked(date: LocalDate) {
        viewModelScope.launch { semester.setStartDate(date) }
    }

    fun onPrevWeek() = displayWeekIndex.update { (it - 1).coerceAtLeast(1) }
    fun onNextWeek() = displayWeekIndex.update { it + 1 }
    fun onCurrentWeek() {
        val start = bootstrap.value?.semesterStart ?: return
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(start, today)
        val weekIdx = ((days / 7L).toInt() + 1).coerceAtLeast(1)
        displayWeekIndex.value = weekIdx
    }
}
