package com.example.personal_studio.feature.bitexam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.repository.TimelineRepository
import com.example.personal_studio.domain.bitexam.SyncExamsUseCase
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
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

data class ExamRow(
    val id: Long, val course: String, val startAt: Long, val endAt: Long?,
    val location: String?, val seat: String?, val isDone: Boolean,
)

data class ExamsUiState(
    val upcoming: List<ExamRow> = emptyList(),
    val past: List<ExamRow> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null,
    val credsSaved: Boolean = false,
)

sealed interface ExamsEvent { object NeedLogin : ExamsEvent }

@HiltViewModel
class ExamsViewModel @Inject constructor(
    private val dao: TimelineDao,
    private val toggleDone: ToggleDoneUseCase,
    private val cancelReminders: CancelRemindersUseCase,
    private val scheduleReminders: ScheduleRemindersUseCase,
    private val repo: TimelineRepository,
    private val sync: SyncExamsUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val transient = MutableStateFlow(ExamsUiState())
    private val _events = MutableSharedFlow<ExamsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ExamsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ExamsUiState> = combine(
        dao.observeImportedExams(), credPrefs.observeAll(), transient,
    ) { rows, creds, t ->
        val now = nowProvider()
        val mapped = rows.map { it.toRow() }
        val (past, upcoming) = mapped.partition { (it.endAt ?: it.startAt) < now }
        t.copy(
            upcoming = upcoming.sortedBy { it.startAt },
            past = past.sortedByDescending { it.startAt },
            credsSaved = creds != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExamsUiState())

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
            viewModelScope.launch { _events.emit(ExamsEvent.NeedLogin) }
            return
        }
        sync.sync(ExamSyncRequest(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL, true))
            .onEach { step ->
                transient.value = when (step) {
                    ExamSyncStep.LoggingIn, ExamSyncStep.FetchingExams -> transient.value.copy(syncing = true, error = null)
                    is ExamSyncStep.Done -> transient.value.copy(syncing = false, error = null)
                    is ExamSyncStep.Failed -> transient.value.copy(syncing = false, error = step.error.toMessage())
                }
            }
            .launchIn(viewModelScope)
    }

    private fun TimelineItemEntity.toRow() = ExamRow(
        id, title, startAt, endAt, location,
        notes?.removePrefix("座位: ")?.takeIf { it != notes }, isDone,
    )

    private fun ExamSyncError.toMessage(): String = when (this) {
        is ExamSyncError.WrongCredentials -> "密码错误"
        is ExamSyncError.AccountLocked -> "账号锁定"
        is ExamSyncError.CaptchaRequired -> "需验证码,请网页端登录一次"
        is ExamSyncError.NeedReview -> "请先完成评教"
        is ExamSyncError.ParseFail -> "教务返回异常"
        is ExamSyncError.NetworkFail -> "网络错误,请重试"
        is ExamSyncError.Unexpected -> "未知错误"
    }
}
