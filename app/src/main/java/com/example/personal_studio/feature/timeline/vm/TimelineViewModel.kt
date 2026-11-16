package com.example.personal_studio.feature.timeline.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.bitimport.SyncCenter
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.DeleteItemUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import com.example.personal_studio.feature.timeline.today.TodayClock
import com.example.personal_studio.feature.timeline.today.TodayFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TodayView { DAY, WEEK }

/** Check animation of a task row: the tick and strike-through draw, then the row folds away. */
enum class CheckPhase { CHECKING, COLLAPSING }

data class TimelineUiState(
    val now: Long = System.currentTimeMillis(),
    val today: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = today,
    val view: TodayView = TodayView.DAY,
    /** Today's items, for the next-item card and taglines (independent of [selectedDay]). */
    val todayItems: List<TimelineItem> = emptyList(),
    val tomorrowItems: List<TimelineItem> = emptyList(),
    /** Items of [dayItemsDay], which catches up with [selectedDay] once they are loaded. */
    val dayItems: List<TimelineItem> = emptyList(),
    val dayItemsDay: LocalDate = selectedDay,
    /** Monday of [selectedDay]'s week; [weekItems] cover Monday to Sunday. */
    val weekStart: LocalDate = selectedDay.minusDays(selectedDay.dayOfWeek.value - 1L),
    val weekItems: List<TimelineItem> = emptyList(),
    val periods: List<TimetablePeriod> = DefaultTimetable.PERIODS,
    val semesterStart: LocalDate? = null,
    val checkPhases: Map<Long, CheckPhase> = emptyMap(),
    /** Row to highlight briefly (just added, or just restored from the done group). */
    val flashId: Long? = null,
    val showDone: Boolean = false,
    /** Last 一键同步; [syncKnown] = the user has synced at least once (pill shown). */
    val lastSyncAt: Long? = null,
    val syncKnown: Boolean = false,
    val syncing: Boolean = false,
    val loaded: Boolean = false,
) {
    fun findItem(id: Long): TimelineItem? =
        dayItems.firstOrNull { it.id == id } ?: todayItems.firstOrNull { it.id == id }
            ?: tomorrowItems.firstOrNull { it.id == id } ?: weekItems.firstOrNull { it.id == id }
}

private data class Selection(
    val day: LocalDate?,
    val view: TodayView = TodayView.DAY,
)

private data class Feedback(
    val checkPhases: Map<Long, CheckPhase> = emptyMap(),
    val flashId: Long? = null,
    val showDone: Boolean = false,
)

/**
 * 今日: the day timeline, the week grid and the header's next-item card and tagline.
 * The selected day is "today" (null) unless the user picks another day, so it follows
 * midnight. Ticks once a minute while observed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val toggleDone: ToggleDoneUseCase,
    private val deleteItem: DeleteItemUseCase,
    private val cancelReminders: CancelRemindersUseCase,
    private val scheduleReminders: ScheduleRemindersUseCase,
    semester: SemesterPreferences,
    timetable: TimetablePreferences,
    loginPrefs: LoginPrefs,
    private val focus: TodayFocus,
    syncCenter: SyncCenter,
    private val clock: TodayClock,
    private val zone: ZoneId,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection(day = null))
    private val feedback = MutableStateFlow(Feedback())
    private var flashJob: Job? = null

    private val nowFlow: Flow<Long> = flow {
        while (true) {
            val now = clock.now()
            emit(now)
            delay(60_000 - now % 60_000 + 50)
        }
    }
    private val now: StateFlow<Long> = nowFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock.now())
    private val today: Flow<LocalDate> = now.map { it.toDate() }.distinctUntilChanged()

    private val todayAndTomorrow: Flow<Pair<List<TimelineItem>, List<TimelineItem>>> =
        today.flatMapLatest { day ->
            repo.observeItemsInRange(day.startMs(), day.plusDays(2).startMs()).map { items ->
                val split = day.plusDays(1).startMs()
                items.filter { it.startAt < split } to items.filter { it.startAt >= split }
            }
        }

    private val selectedDay: Flow<LocalDate> =
        combine(selection, today) { sel, t -> sel.day ?: t }.distinctUntilChanged()

    private val dayItems: Flow<Pair<LocalDate, List<TimelineItem>>> = selectedDay.flatMapLatest { day ->
        repo.observeItemsInRange(day.startMs(), day.plusDays(1).startMs()).map { day to it }
    }

    private val week: Flow<Pair<LocalDate, List<TimelineItem>>> =
        selectedDay.map { it.minusDays(it.dayOfWeek.value - 1L) }.distinctUntilChanged().flatMapLatest { monday ->
            repo.observeItemsInRange(monday.startMs(), monday.plusDays(7).startMs()).map { monday to it }
        }

    private val settings: Flow<Settings> = combine(
        timetable.periods, semester.startDate, loginPrefs.observeLastSyncAt, loginPrefs.observeFirstSyncDone,
        syncCenter.progress.map { it != null && !it.done },
    ) { periods, start, lastSync, firstSyncDone, syncing ->
        Settings(periods.ifEmpty { DefaultTimetable.PERIODS }, start, lastSync, firstSyncDone || lastSync != null, syncing)
    }

    private data class Settings(
        val periods: List<TimetablePeriod>,
        val semesterStart: LocalDate?,
        val lastSyncAt: Long?,
        val syncKnown: Boolean,
        val syncing: Boolean,
    )

    private data class Data(
        val now: Long,
        val today: LocalDate,
        val day: LocalDate,
        val todayItems: Pair<List<TimelineItem>, List<TimelineItem>>,
        val dayItems: Pair<LocalDate, List<TimelineItem>>,
        val week: Pair<LocalDate, List<TimelineItem>>,
    )

    private val data: Flow<Data> = combine(
        combine(now, today, selectedDay) { n, t, d -> Triple(n, t, d) },
        todayAndTomorrow, dayItems, week,
    ) { (n, t, d), tt, items, wk -> Data(n, t, d, tt, items, wk) }

    val uiState: StateFlow<TimelineUiState> = combine(data, settings, selection, feedback) { d, s, sel, fb ->
        TimelineUiState(
            now = d.now,
            today = d.today,
            selectedDay = d.day,
            view = sel.view,
            todayItems = d.todayItems.first,
            tomorrowItems = d.todayItems.second,
            dayItems = d.dayItems.second,
            dayItemsDay = d.dayItems.first,
            weekStart = d.week.first,
            weekItems = d.week.second,
            periods = s.periods,
            semesterStart = s.semesterStart,
            checkPhases = fb.checkPhases,
            flashId = fb.flashId,
            showDone = fb.showDone,
            lastSyncAt = s.lastSyncAt,
            syncKnown = s.syncKnown,
            syncing = s.syncing,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState(now = clock.now(), today = clock.now().toDate()))

    init {
        viewModelScope.launch {
            focus.requests.collect { id ->
                focus.consumed()
                val item = repo.findById(id) ?: return@collect
                selectDay(item.startAt.toDate(), TodayView.DAY)
                flash(id)
            }
        }
    }

    fun onSelectDay(day: LocalDate) = selectDay(day, selection.value.view)

    fun onShiftDay(delta: Long) = selectDay(currentDay().plusDays(delta), TodayView.DAY)

    fun onShiftWeek(delta: Long) = selectDay(currentDay().plusWeeks(delta), TodayView.WEEK)

    fun onToday() = selectDay(clock.now().toDate(), selection.value.view)

    fun onView(view: TodayView) = selection.update { it.copy(view = view) }

    fun onToggleShowDone() = feedback.update { it.copy(showDone = !it.showDone) }

    /**
     * Ticks a task: saved at once (so nothing is lost if the app goes away), while the row
     * plays the tick and strike-through, then folds into the "已完成" group.
     */
    fun onCheck(id: Long) {
        if (id in feedback.value.checkPhases) return
        feedback.update { it.copy(checkPhases = it.checkPhases + (id to CheckPhase.CHECKING)) }
        viewModelScope.launch {
            setDone(id, true)
            delay(CHECK_MS)
            feedback.update { it.copy(checkPhases = it.checkPhases + (id to CheckPhase.COLLAPSING)) }
            delay(COLLAPSE_MS)
            feedback.update { it.copy(checkPhases = it.checkPhases - id) }
        }
    }

    /** Restores a finished item; its row flashes where it lands. */
    fun onUndo(id: Long) {
        viewModelScope.launch {
            setDone(id, false)
            flash(id)
        }
    }

    /** "标记已考" / "取消已考" and other done toggles without a row animation. */
    fun onSetDone(id: Long, done: Boolean) {
        viewModelScope.launch { setDone(id, done) }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            cancelReminders(id)
            deleteItem(id)
        }
    }

    private suspend fun setDone(id: Long, done: Boolean) {
        runCatching { toggleDone(id, done) }
        cancelReminders(id)
        val updated = repo.findById(id)
        if (updated != null && !updated.isDone && updated.startAt > clock.now()) scheduleReminders(updated)
    }

    private fun flash(id: Long) {
        flashJob?.cancel()
        feedback.update { it.copy(flashId = id) }
        flashJob = viewModelScope.launch {
            delay(FLASH_MS)
            feedback.update { if (it.flashId == id) it.copy(flashId = null) else it }
        }
    }

    private fun selectDay(day: LocalDate, view: TodayView) {
        val today = clock.now().toDate()
        selection.value = Selection(day = day.takeIf { it != today }, view = view)
    }

    private fun currentDay(): LocalDate = selection.value.day ?: clock.now().toDate()

    private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private fun LocalDate.startMs(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        const val CHECK_MS = 480L
        const val COLLAPSE_MS = 380L
        const val FLASH_MS = 1_700L
    }
}
