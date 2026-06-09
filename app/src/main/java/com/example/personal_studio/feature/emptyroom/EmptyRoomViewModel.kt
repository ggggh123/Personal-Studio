package com.example.personal_studio.feature.emptyroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.withSessionAutoFallback
import com.example.personal_studio.domain.emptyroom.EmptyRoomRepository
import com.example.personal_studio.domain.emptyroom.EmptyRoomResult
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomError
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class EmptyRoomUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val campuses: List<Campus> = emptyList(),
    val buildings: List<Building> = emptyList(),        // 所有选中校区合并的楼(去重)
    val selectedCampusCodes: Set<String> = emptySet(),  // 多选校区,必须非空才能查
    val selectedBuildingCodes: Set<String> = emptySet(), // 多选楼;空 = 选中校区的全部楼
    val requiredFreePeriods: Set<Int> = emptySet(),      // 须全部空闲的节次(1..13);空 = 不限
    val date: String = "",
    val rooms: List<RoomFreeSlots> = emptyList(),
    val queried: Boolean = false,                        // 是否执行过查询(区分「未查」与「查了无结果」)
)

sealed interface EmptyRoomEvent { object NeedLogin : EmptyRoomEvent }

@HiltViewModel
class EmptyRoomViewModel @Inject constructor(
    private val repo: EmptyRoomRepository,
    private val credPrefs: ImportCredentialPrefs,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(EmptyRoomUiState(date = today()))
    val uiState: StateFlow<EmptyRoomUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<EmptyRoomEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EmptyRoomEvent> = _events.asSharedFlow()

    // 串行化所有「open→…→close」会话操作:共享 @Singleton BitApiClient,并发 close() 会清掉对方
    // 的 cookie/retrofit。withLock 保证同一时刻只有一个操作占用会话。
    private val sessionMutex = Mutex()

    init {
        // 有凭据(进屏或登录回来)即拉校区列表供手动选;无凭据则等用户操作时触发登录。
        credPrefs.observeAll()
            .onEach { creds -> if (creds != null && _ui.value.campuses.isEmpty()) loadCampuses() }
            .launchIn(viewModelScope)
    }

    /** 拉校区列表(供用户手动选)。不预选校区。 */
    fun loadCampuses() {
        val creds = credPrefs.observeAll().value ?: return
        viewModelScope.launch {
            sessionMutex.withLock {
                runSession(creds, onNetworkError = { /* 进屏自动拉,失败静默,等用户操作再提示 */ }) {
                    _ui.update { it.copy(campuses = repo.campuses()) }
                }
            }
        }
    }

    /** 勾/取消校区(多选);变化后重新合并各选中校区的楼,并清空上次查询。 */
    fun onToggleCampus(c: Campus) {
        _ui.update {
            val codes = it.selectedCampusCodes.toMutableSet().apply { if (!add(c.code)) remove(c.code) }
            it.copy(selectedCampusCodes = codes, rooms = emptyList(), queried = false, error = null)
        }
        loadBuildings()
    }

    /** 勾/取消教学楼(多选)。 */
    fun onToggleBuilding(b: Building) {
        _ui.update {
            val codes = it.selectedBuildingCodes.toMutableSet().apply { if (!add(b.code)) remove(b.code) }
            it.copy(selectedBuildingCodes = codes, rooms = emptyList(), queried = false, error = null)
        }
    }

    /** 选「全部教学楼」= 清空具体楼选择(查选中校区的所有楼)。 */
    fun onSelectAllBuildings() {
        _ui.update { it.copy(selectedBuildingCodes = emptySet(), rooms = emptyList(), queried = false, error = null) }
    }

    /** 勾/取消「须空闲节次」(多选,1..13)。空 = 不限。 */
    fun onTogglePeriod(p: Int) {
        _ui.update {
            val periods = it.requiredFreePeriods.toMutableSet().apply { if (!add(p)) remove(p) }
            it.copy(requiredFreePeriods = periods, rooms = emptyList(), queried = false, error = null)
        }
    }

    /** 查询:基于选中校区合并出的楼(选了具体楼则只查这些,否则全部),列出教室。 */
    fun onQuery() {
        val creds = credentialsOrLogin() ?: return
        if (_ui.value.selectedCampusCodes.isEmpty()) {
            _ui.update { it.copy(error = "请先选择校区") }
            return
        }
        viewModelScope.launch {
            sessionMutex.withLock {
                _ui.update { it.copy(loading = true, error = null) }
                runSession(creds, onNetworkError = { _ui.update { it.copy(loading = false, error = "网络错误,请重试") } }) { term ->
                    val st = _ui.value
                    val targets = if (st.selectedBuildingCodes.isEmpty()) st.buildings
                        else st.buildings.filter { it.code in st.selectedBuildingCodes }
                    val raw = repo.occupancyForBuildings(targets, st.date, term, nowMinute())
                    // 「须空闲节次」筛:选中的节次必须都不在该教室占用集中
                    val req = st.requiredFreePeriods
                    val filtered = if (req.isEmpty()) raw else raw.filter { room -> req.none { it in room.busyPeriods } }
                    _ui.update { it.copy(loading = false, rooms = sortRooms(filtered), queried = true) }
                }
            }
        }
    }

    private fun loadBuildings() {
        val creds = credPrefs.observeAll().value ?: return
        val selected = _ui.value.campuses.filter { it.code in _ui.value.selectedCampusCodes }
        if (selected.isEmpty()) {
            _ui.update { it.copy(buildings = emptyList(), selectedBuildingCodes = emptySet()) }
            return
        }
        viewModelScope.launch {
            sessionMutex.withLock {
                runSession(creds, onNetworkError = { /* 切校区拉楼,失败静默 */ }) {
                    val merged = selected.flatMap { repo.buildings(it.code) }.distinctBy { it.code }
                    _ui.update { st ->
                        st.copy(
                            buildings = merged,
                            // 仅保留仍属于某个选中校区的楼选择
                            selectedBuildingCodes = st.selectedBuildingCodes.filter { code -> merged.any { it.code == code } }.toSet(),
                        )
                    }
                }
            }
        }
    }

    /** 查询日期前/后移 days 天;下限为今天(查过去无意义)。换日期清空上次结果。 */
    fun onShiftDate(days: Int) {
        _ui.update {
            val target = LocalDate.parse(it.date).plusDays(days.toLong())
            val capped = if (target.isBefore(todayLocalDate())) todayLocalDate() else target
            it.copy(date = capped.toString(), rooms = emptyList(), queried = false, error = null)
        }
    }

    /** 回到今天。 */
    fun onResetToday() {
        _ui.update { it.copy(date = todayLocalDate().toString(), rooms = emptyList(), queried = false, error = null) }
    }

    /** 无凭据时发 NeedLogin 并返回 null;否则返回凭据。 */
    private fun credentialsOrLogin(): SavedCredentials? {
        val creds = credPrefs.observeAll().value
        if (creds == null) { viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) } }
        return creds
    }

    /** 现在空的排前,其次空闲节次多的排前,再按楼/房号。 */
    private fun sortRooms(rooms: List<RoomFreeSlots>): List<RoomFreeSlots> =
        rooms.sortedWith(
            compareByDescending<RoomFreeSlots> { it.status.freeNow }
                .thenBy { it.busyPeriods.size }
                .thenBy { it.buildingName }
                .thenBy { it.roomName },
        )

    /** 在 withLock 调用方内跑一段会话:open→login→[block]。连接级失败(IOException)自动换 mode 重试
     *  一次并回写生效 mode;登录类失败 → [handleLoginErr](不重试);两 mode 都连接级失败 → [onNetworkError]。
     *  每次 attempt 自带 open/close(共享单例会话,故回退重试必须在同一 withLock 内,见调用点)。 */
    private suspend fun runSession(
        creds: SavedCredentials,
        onNetworkError: () -> Unit,
        block: suspend (term: String) -> Unit,
    ) {
        val firstMode = creds.lastMode ?: NetworkMode.LOCAL
        var loginErr: EmptyRoomError? = null
        try {
            withSessionAutoFallback(
                first = firstMode,
                onModeSucceeded = { if (it != firstMode) credPrefs.save(creds.username, creds.password, it) },
            ) { mode ->
                try {
                    when (val r = repo.openAndLogin(creds.username, creds.password, mode)) {
                        is EmptyRoomResult.Ok -> block(r.value)
                        is EmptyRoomResult.Err -> loginErr = r.error
                    }
                } finally {
                    repo.close()
                }
            }
        } catch (e: Throwable) {
            // 连接级失败两 mode 都失败,或非连接级异常(withSessionAutoFallback 对非 IOException 不重试,
            // 直接抛出)——统一当"网络/未知错误"处理(与重构前 catch(Throwable) 行为一致)。
            onNetworkError()
            return
        }
        loginErr?.let { handleLoginErr(it) }
    }

    private suspend fun handleLoginErr(error: EmptyRoomError) {
        if (error is EmptyRoomError.NeedLogin || error is EmptyRoomError.WrongCredentials) {
            _events.emit(EmptyRoomEvent.NeedLogin)
        }
        _ui.update { it.copy(loading = false, error = errMsg(error)) }
    }

    private fun errMsg(e: EmptyRoomError): String = when (e) {
        EmptyRoomError.NeedLogin, EmptyRoomError.WrongCredentials -> "请登录"
        is EmptyRoomError.Network -> "网络错误,请重试"
        is EmptyRoomError.Parse -> "教务返回异常"
        is EmptyRoomError.Unexpected -> "未知错误"
    }

    private fun nowMinute(): Int {
        val z = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(nowProvider()).atZone(z).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun todayLocalDate(): LocalDate =
        Instant.ofEpochMilli(nowProvider()).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun today(): String = todayLocalDate().toString()
}
