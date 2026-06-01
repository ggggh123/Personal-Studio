package com.example.personal_studio.feature.emptyroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.NetworkMode
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class EmptyRoomUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val campuses: List<Campus> = emptyList(),
    val buildings: List<Building> = emptyList(),
    val selectedCampus: Campus? = null,
    val selectedBuilding: Building? = null,
    val date: String = "",
    val rooms: List<RoomFreeSlots> = emptyList(),
    val minFreeHours: Int = 0,
)

sealed interface EmptyRoomEvent { object NeedLogin : EmptyRoomEvent }

@HiltViewModel
class EmptyRoomViewModel @Inject constructor(
    private val repo: EmptyRoomRepository,
    private val credPrefs: ImportCredentialPrefs,
    private val dao: TimelineDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(EmptyRoomUiState(date = today()))
    val uiState: StateFlow<EmptyRoomUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<EmptyRoomEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EmptyRoomEvent> = _events.asSharedFlow()

    private var term: String? = null

    fun onSmartNow() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) }; return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val term = ensureSession(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL)
                    ?: return@launch
                val campuses = repo.campuses()
                val campus = _ui.value.selectedCampus ?: campuses.firstOrNull()
                if (campus == null) { _ui.update { it.copy(loading = false, error = "无校区") }; return@launch }
                val rooms = repo.occupancyForCampus(campus.code, _ui.value.date, term, nowMinute())
                    .filter { it.status.freeNow }
                    .sortedByDescending { it.status.freeUntilMinuteOfDay ?: 0 }
                _ui.update { it.copy(loading = false, campuses = campuses, selectedCampus = campus, rooms = rooms) }
            } catch (e: Throwable) {
                _ui.update { it.copy(loading = false, error = "网络错误,请重试") }
            } finally {
                repo.close()
            }
        }
    }

    fun onQuery() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(EmptyRoomEvent.NeedLogin) }; return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val term = ensureSession(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL)
                    ?: return@launch
                val st = _ui.value
                val campus = st.selectedCampus ?: repo.campuses().firstOrNull()
                if (campus == null) { _ui.update { it.copy(loading = false, error = "无校区") }; return@launch }
                val raw = st.selectedBuilding?.let { repo.occupancy(it, st.date, term, nowMinute()) }
                    ?: repo.occupancyForCampus(campus.code, st.date, term, nowMinute())
                val rooms = applyFilterAndSort(raw, st.minFreeHours)
                _ui.update { it.copy(loading = false, selectedCampus = campus, rooms = rooms) }
            } catch (e: Throwable) {
                _ui.update { it.copy(loading = false, error = "网络错误,请重试") }
            } finally {
                repo.close()
            }
        }
    }

    fun onSelectCampus(c: Campus) { _ui.update { it.copy(selectedCampus = c, buildings = emptyList(), selectedBuilding = null) } }
    fun onSelectBuilding(b: Building?) { _ui.update { it.copy(selectedBuilding = b) } }
    fun onSelectDate(d: String) { _ui.update { it.copy(date = d) } }
    fun onMinFreeHours(h: Int) { _ui.update { it.copy(minFreeHours = h) } }

    private fun applyFilterAndSort(rooms: List<RoomFreeSlots>, minHours: Int): List<RoomFreeSlots> {
        val now = nowMinute()
        val filtered = if (minHours <= 0) rooms else rooms.filter {
            it.status.freeNow && (it.status.freeUntilMinuteOfDay ?: now) - now >= minHours * 60
        }
        return filtered.sortedWith(
            compareByDescending<RoomFreeSlots> { it.status.freeNow }
                .thenByDescending { it.status.freeUntilMinuteOfDay ?: 0 }
                .thenBy { it.roomName },
        )
    }

    private suspend fun ensureSession(u: String, p: String, mode: NetworkMode): String? {
        return when (val r = repo.openAndLogin(u, p, mode)) {
            is EmptyRoomResult.Ok -> r.value.also { term = it }
            is EmptyRoomResult.Err -> {
                if (r.error is EmptyRoomError.NeedLogin || r.error is EmptyRoomError.WrongCredentials) {
                    _events.emit(EmptyRoomEvent.NeedLogin)
                }
                _ui.update { it.copy(loading = false, error = errMsg(r.error)) }
                null
            }
        }
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

    private fun today(): String =
        Instant.ofEpochMilli(nowProvider()).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
