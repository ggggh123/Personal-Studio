package com.example.personal_studio.feature.emptyroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.local.datastore.EmptyRoomPrefs
import com.example.personal_studio.data.local.datastore.EmptyRoomSelection
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.network.bit.withSessionAutoFallback
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.emptyroom.EmptyRoomQuery
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.TimeFilter
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomCatalog
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** What the result list shows after the time filter: rooms by building, and the counts above them. */
data class EmptyRoomResults(
    val groups: List<Pair<String, List<RoomFreeSlots>>>,
    val matching: Int,
    val total: Int,
    /** Rooms free right now among [matching]; null when the date is not today. */
    val freeNow: Int?,
    /** The chosen time is already over today, so every room is listed. */
    val passed: Boolean,
)

data class EmptyRoomUiState(
    val loggedIn: Boolean = false,
    val catalog: EmptyRoomCatalog? = null,
    val catalogLoading: Boolean = false,
    val campusCode: String? = null,
    /** Empty = every building of the campus. */
    val buildingCodes: Set<String> = emptySet(),
    val recentBuildingCodes: List<String> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val date: LocalDate = today,
    val timeFilter: TimeFilter = TimeFilter.NOW,
    val custom: IntRange = 1..2,
    val periods: List<TimetablePeriod> = DefaultTimetable.PERIODS,
    val nowMinute: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    /** Rooms of the chosen buildings on [date] before the time filter; null until fetched. */
    val fetched: List<RoomFreeSlots>? = null,
    val results: EmptyRoomResults? = null,
) {
    val isToday: Boolean get() = date == today
    val campus: Campus? get() = catalog?.campuses?.firstOrNull { it.code == campusCode }
    val campusBuildings: List<Building> get() = catalog?.buildingsOf(campusCode).orEmpty()
    val targets: List<Building> get() = campusBuildings.filter { buildingCodes.isEmpty() || it.code in buildingCodes }
    /** 现在 has no meaning on another day; it reads as 不限 there. */
    val effectiveFilter: TimeFilter get() = if (timeFilter == TimeFilter.NOW && !isToday) TimeFilter.ANY else timeFilter
}

sealed interface EmptyRoomEvent { object NeedLogin : EmptyRoomEvent }

/**
 * 空教室. Campuses and buildings come from a catalog kept on the phone (fetched once, refreshed
 * after [CATALOG_TTL_MS]), so choosing a place needs no network. Only fetching a building's
 * occupancy for a day needs the school, and that is cached per building and day: changing the
 * time filter, or going back to a day already seen, never logs in again. The place and filter
 * are remembered, and a remembered place is queried as soon as the page opens.
 */
@HiltViewModel
class EmptyRoomViewModel @Inject constructor(
    private val repo: EmptyRoomRepository,
    private val credPrefs: ImportCredentialPrefs,
    private val resolveNetworkMode: ResolveNetworkModeUseCase,
    private val prefs: EmptyRoomPrefs,
    private val timetable: TimetablePreferences,
    private val nowProvider: () -> Long,
) : ViewModel() {

    private val _ui = MutableStateFlow(EmptyRoomUiState(today = todayDate(), nowMinute = nowMinute()))
    val uiState: StateFlow<EmptyRoomUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<EmptyRoomEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EmptyRoomEvent> = _events.asSharedFlow()

    // BitApiClient is a shared singleton; one open → close at a time (see docs/school-integration.md).
    private val sessionMutex = Mutex()
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            val periods = timetable.periods.first().ifEmpty { DefaultTimetable.PERIODS }
            val saved = prefs.selection()
            val catalog = prefs.catalog()
            _ui.update {
                it.copy(
                    loggedIn = credPrefs.observeAll().value != null,
                    catalog = catalog,
                    campusCode = saved.campusCode ?: catalog?.campuses?.singleOrNull()?.code,
                    buildingCodes = saved.buildingCodes,
                    recentBuildingCodes = saved.recentBuildingCodes,
                    timeFilter = saved.timeFilter?.let { f -> TimeFilter.entries.firstOrNull { e -> e.name == f } } ?: TimeFilter.NOW,
                    custom = saved.customFrom..maxOf(saved.customFrom, saved.customTo),
                    periods = periods,
                )
            }
            load(needCatalog = catalog == null || nowProvider() - catalog.fetchedAt > CATALOG_TTL_MS)
            // Coming back from the login page with an account: load what could not be loaded.
            credPrefs.observeAll().drop(1).onEach { creds ->
                _ui.update { it.copy(loggedIn = creds != null) }
                if (creds != null) load(needCatalog = _ui.value.catalog == null)
            }.launchIn(viewModelScope)
        }
    }

    /** Commit the building sheet: a campus and its buildings (empty = all), then query. */
    fun onPickPlace(campusCode: String, buildingCodes: Set<String>) {
        _ui.update { st ->
            val recent = (buildingCodes.toList() + st.recentBuildingCodes).distinct().take(EmptyRoomPrefs.RECENT_MAX)
            st.copy(campusCode = campusCode, buildingCodes = buildingCodes, recentBuildingCodes = recent, fetched = null, error = null)
                .withResults()
        }
        persist()
        load(needCatalog = false)
    }

    /** Another day (never before today); days already fetched come from memory. */
    fun onDate(date: LocalDate) {
        val d = if (date.isBefore(_ui.value.today)) _ui.value.today else date
        if (d == _ui.value.date) return
        _ui.update { it.copy(date = d, fetched = null, error = null).withResults() }
        load(needCatalog = false)
    }

    /** Time filter changes only re-filter what is already fetched. */
    fun onTimeFilter(filter: TimeFilter) {
        _ui.update { it.copy(timeFilter = filter).withResults() }
        persist()
    }

    fun onCustomRange(range: IntRange) {
        val r = range.first.coerceIn(1, 13)..range.last.coerceIn(range.first.coerceIn(1, 13), 13)
        _ui.update { it.copy(custom = r, timeFilter = TimeFilter.CUSTOM).withResults() }
        persist()
    }

    /** Pull the chosen buildings again for this day, ignoring what is in memory. */
    fun onRefresh() {
        val st = _ui.value
        repo.dropOccupancy(st.targets, st.date.toString())
        load(needCatalog = st.catalog == null)
    }

    /** Fetch the campus and building list again (it rarely changes; also after 30 days on its own). */
    fun onRefreshCatalog() = load(needCatalog = true, needRooms = false)

    fun onLogin() { viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) } }

    /** Back on the page later: move "now" on and re-derive the statuses from memory, no network. */
    fun onResume() {
        val st = _ui.value
        val today = todayDate()
        if (today != st.today) {
            _ui.update { it.copy(today = today, date = if (it.date.isBefore(today)) today else it.date, nowMinute = nowMinute(), fetched = null).withResults() }
            load(needCatalog = false)
            return
        }
        val now = nowMinute()
        viewModelScope.launch {
            val rooms = if (st.fetched != null) repo.cachedOccupancy(st.targets, st.date.toString(), now) else null
            _ui.update { it.copy(nowMinute = now, fetched = rooms ?: it.fetched).withResults() }
        }
    }

    /**
     * Brings the page up to date with at most one school session: the catalog if [needCatalog],
     * then the occupancy of the chosen buildings unless all of it is already in memory.
     */
    private fun load(needCatalog: Boolean, needRooms: Boolean = true) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val now = nowMinute()
            val before = _ui.value
            if (!needCatalog && needRooms) {
                if (before.targets.isEmpty()) return@launch
                repo.cachedOccupancy(before.targets, before.date.toString(), now)?.let { rooms ->
                    _ui.update { it.copy(fetched = rooms, nowMinute = now, loading = false, error = null).withResults() }
                    return@launch
                }
            }
            if (!needCatalog && before.targets.isEmpty()) return@launch
            val creds = credPrefs.observeAll().value
            if (creds == null) {
                _ui.update { it.copy(loggedIn = false, loading = false, catalogLoading = false) }
                return@launch
            }
            _ui.update { it.copy(loading = needRooms && (it.targets.isNotEmpty() || needCatalog), catalogLoading = needCatalog, error = null) }
            sessionMutex.withLock {
                runSession(creds, onNetworkError = {
                    _ui.update { it.copy(loading = false, catalogLoading = false, error = "连不上学校，请检查网络后重试") }
                }) { term ->
                    if (needCatalog) {
                        val catalog = repo.fetchCatalog(nowProvider())
                        prefs.saveCatalog(catalog)
                        _ui.update { st ->
                            val campus = st.campusCode?.takeIf { c -> catalog.campuses.any { it.code == c } }
                                ?: catalog.campuses.singleOrNull()?.code
                            st.copy(
                                catalog = catalog,
                                catalogLoading = false,
                                campusCode = campus,
                                buildingCodes = st.buildingCodes.filter { code -> catalog.buildings.any { it.code == code } }.toSet(),
                            )
                        }
                    }
                    val st = _ui.value
                    if (needRooms && st.targets.isNotEmpty()) {
                        val rooms = repo.occupancyForBuildings(st.targets, st.date.toString(), term, now)
                        _ui.update { it.copy(fetched = rooms, nowMinute = now, loading = false).withResults() }
                    } else {
                        _ui.update { it.copy(loading = false) }
                    }
                }
            }
        }
    }

    private fun persist() {
        val st = _ui.value
        viewModelScope.launch {
            prefs.saveSelection(
                EmptyRoomSelection(
                    campusCode = st.campusCode,
                    buildingCodes = st.buildingCodes,
                    timeFilter = st.timeFilter.name,
                    customFrom = st.custom.first,
                    customTo = st.custom.last,
                    recentBuildingCodes = st.recentBuildingCodes,
                ),
            )
        }
    }

    private fun EmptyRoomUiState.withResults(): EmptyRoomUiState {
        val rooms = fetched ?: return copy(results = null)
        val required = EmptyRoomQuery.required(effectiveFilter, custom, isToday, nowMinute, PeriodClock(periods))
        val matching = EmptyRoomQuery.filter(rooms, required.periods)
        return copy(
            results = EmptyRoomResults(
                groups = EmptyRoomQuery.group(matching, campusBuildings.map { it.code }, isToday),
                matching = matching.size,
                total = rooms.size,
                freeNow = if (isToday) matching.count { it.status.freeNow } else null,
                passed = required.passed,
            ),
        )
    }

    /**
     * One session: open → login → [block] → close, on the network mode that works (school
     * network first, WebVPN when it is unreachable). Login errors go to [handleLoginErr];
     * connection failures on both modes to [onNetworkError].
     */
    private suspend fun runSession(
        creds: SavedCredentials,
        onNetworkError: () -> Unit,
        block: suspend (term: String) -> Unit,
    ) {
        val firstMode = resolveNetworkMode(creds.lastMode)
        var loginErr: EmptyRoomError? = null
        try {
            withSessionAutoFallback(
                first = firstMode,
                onModeSucceeded = { if (it != firstMode) credPrefs.save(creds.username, creds.password, it) },
            ) { mode ->
                repo.acquireSession()
                try {
                    when (val r = repo.openAndLogin(creds.username, creds.password, mode)) {
                        is EmptyRoomResult.Ok -> block(r.value)
                        is EmptyRoomResult.Err -> loginErr = r.error
                    }
                } finally {
                    repo.close()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onNetworkError()
            return
        }
        loginErr?.let { handleLoginErr(it) }
    }

    private suspend fun handleLoginErr(error: EmptyRoomError) {
        if (error is EmptyRoomError.NeedLogin || error is EmptyRoomError.WrongCredentials) {
            _events.emit(EmptyRoomEvent.NeedLogin)
        }
        _ui.update { it.copy(loading = false, catalogLoading = false, error = errMsg(error)) }
    }

    private fun errMsg(e: EmptyRoomError): String = when (e) {
        EmptyRoomError.NeedLogin, EmptyRoomError.WrongCredentials -> "需要重新登录学校账户"
        is EmptyRoomError.Network -> "连不上学校，请检查网络后重试"
        is EmptyRoomError.Parse -> e.message.takeIf { it.startsWith("请") } ?: "教务系统返回了意外的内容"
        is EmptyRoomError.Unexpected -> "出现了未知错误"
    }

    private fun nowMinute(): Int {
        val t = Instant.ofEpochMilli(nowProvider()).atZone(ZoneId.systemDefault()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun todayDate(): LocalDate = Instant.ofEpochMilli(nowProvider()).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        const val CATALOG_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
