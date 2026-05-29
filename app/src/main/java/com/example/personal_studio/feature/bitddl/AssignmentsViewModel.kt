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
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val transient = MutableStateFlow(AssignmentsUiState())

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
            transient.value = transient.value.copy(error = "请先在「成绩查询」里登录并勾选记住密码")
            return
        }
        sync.sync(DdlSyncRequest(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL, true))
            .onEach { step ->
                transient.value = when (step) {
                    DdlSyncStep.FetchingCalendar -> transient.value.copy(syncing = true, error = null)
                    is DdlSyncStep.Done -> transient.value.copy(syncing = false, error = null)
                    is DdlSyncStep.Failed -> transient.value.copy(syncing = false, error = step.error.toMessage())
                }
            }
            .launchIn(viewModelScope)
    }

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
