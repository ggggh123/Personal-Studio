package com.example.personal_studio.feature.bitddl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.bitddl.SyncAssignmentsUseCase
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitddl.model.DdlSyncStep
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AssignmentsEvent {
    object NeedLogin : AssignmentsEvent
}

data class DdlRow(
    val id: Long,
    val title: String,
    val courseName: String?,
    val dueAt: Long,
    val isDone: Boolean,
)

data class AssignmentsUiState(
    val upcoming: List<DdlRow> = emptyList(),
    val doneOrOverdue: List<DdlRow> = emptyList(),
    val syncing: Boolean = false,
    val syncSteps: List<String> = emptyList(),  // 刷新时逐步累积的状态文案(同成绩页)
    val error: String? = null,
    val credsSaved: Boolean = false,
)

@HiltViewModel
class AssignmentsViewModel @Inject constructor(
    private val dao: TimelineDao,
    private val toggleDone: ToggleDoneUseCase,
    private val cancelReminders: CancelRemindersUseCase,
    private val scheduleReminders: ScheduleRemindersUseCase,
    private val repo: TimelineRepository,
    private val sync: SyncAssignmentsUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val resolveNetworkMode: ResolveNetworkModeUseCase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val transient = MutableStateFlow(AssignmentsUiState())

    private val _events = MutableSharedFlow<AssignmentsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AssignmentsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<AssignmentsUiState> = combine(
        dao.observeLexueDdls(), credPrefs.observeAll(), transient,
    ) { rows, creds, t ->
        val now = nowProvider()
        val mapped = rows.map { it.toRow() }
        val (upcoming, folded) = mapped.partition { !it.isDone && it.dueAt >= now }
        t.copy(
            upcoming = upcoming.sortedBy { it.dueAt },
            doneOrOverdue = folded.sortedByDescending { it.dueAt },
            credsSaved = creds != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssignmentsUiState())

    fun onToggleDone(id: Long, done: Boolean) = viewModelScope.launch {
        toggleDone(id, done)
        cancelReminders(id)
        if (!done) {
            val item = repo.findById(id)
            if (item != null && item.startAt > nowProvider()) scheduleReminders(item)
        }
    }

    fun onRefresh() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(AssignmentsEvent.NeedLogin) }
            return
        }
        transient.value = transient.value.copy(syncing = true, error = null, syncSteps = emptyList())
        viewModelScope.launch {
            // 校内优先:lastMode=WEBVPN 时先探一次校内可达性,在校园网下自动脱离"粘校外"。
            val firstMode = resolveNetworkMode(creds.lastMode)
            sync.syncAuto(
                DdlSyncRequest(creds.username, creds.password, firstMode, true),
                onModeSucceeded = { if (it != firstMode) credPrefs.save(creds.username, creds.password, it) },
            ).collect { step ->
                val cur = transient.value
                transient.value = when (step) {
                    DdlSyncStep.FetchingCalendar -> cur.copy(syncing = true, error = null, syncSteps = cur.syncSteps + "登录并拉取乐学日历…")
                    is DdlSyncStep.SwitchingMode -> cur.copy(syncSteps = cur.syncSteps + switchMsg(step.to))
                    is DdlSyncStep.Done -> cur.copy(syncing = false, syncSteps = cur.syncSteps + "完成 · ${step.total} 项作业")
                    is DdlSyncStep.Failed -> cur.copy(syncing = false, error = step.error.toMessage())
                }
            }
        }
    }

    private fun switchMsg(to: NetworkMode): String =
        if (to == NetworkMode.WEBVPN) "校内不可达，改用校外(WEBVPN)重试…" else "校外不可达，改用校内重试…"

    private fun TimelineItemEntity.toRow() = DdlRow(id, title, courseName, startAt, isDone)

    private fun DdlSyncError.toMessage(): String = when (this) {
        is DdlSyncError.WrongCredentials -> "密码错误"
        is DdlSyncError.AccountLocked -> "账号锁定"
        is DdlSyncError.CaptchaRequired -> "需验证码,请网页端登录一次"
        is DdlSyncError.NeedReview -> "请先完成评教"
        is DdlSyncError.ParseFail -> "乐学返回异常"
        is DdlSyncError.NetworkFail -> "网络错误,请重试"
        is DdlSyncError.Unexpected -> "未知错误"
    }
}
