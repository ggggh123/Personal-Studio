package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.NotifPreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TimelineUiState(
    val displayDay: LocalDate = LocalDate.now(),
    val items: List<TimelineItem> = emptyList(),
    val weekStart: LocalDate = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()),
    val dayCounts: Map<LocalDate, Int> = emptyMap(),
    val nowEpoch: Long = System.currentTimeMillis(),
    val notifBannerVisible: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val notifPrefs: NotifPreferences,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val tickFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    private val displayDay = MutableStateFlow(LocalDate.now())

    private val itemsFlow = displayDay.flatMapLatest { day ->
        val (start, end) = dayBounds(day)
        repo.observeItemsInRange(start, end)
    }

    private val weekStripFlow = displayDay.flatMapLatest { day ->
        val ws = day.minusDays((day.dayOfWeek.value - 1).toLong())
        val (start, _) = dayBounds(ws)
        val (_, end) = dayBounds(ws.plusDays(6))
        repo.observeDayCounts(start, end)
    }

    val uiState: StateFlow<TimelineUiState> = combine(
        displayDay, itemsFlow, weekStripFlow, tickFlow, notifPrefs.switches,
    ) { day, items, dayCounts, now, switches ->
        val ws = day.minusDays((day.dayOfWeek.value - 1).toLong())
        TimelineUiState(
            displayDay = day,
            items = items,
            weekStart = ws,
            dayCounts = dayCounts.associate { LocalDate.parse(it.day) to it.count },
            nowEpoch = now,
            notifBannerVisible = false, // permission state computed elsewhere; banner value comes from screen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUiState())

    fun onSelectDay(day: LocalDate) = displayDay.update { day }

    fun onPrevDay() = displayDay.update { it.minusDays(1) }
    fun onNextDay() = displayDay.update { it.plusDays(1) }
    fun onToday() = displayDay.update { LocalDate.now() }

    private fun dayBounds(day: LocalDate): Pair<Long, Long> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
