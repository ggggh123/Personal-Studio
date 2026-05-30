package com.example.personal_studio.feature.bitexam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitexam.SyncExamsUseCase
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
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
    val location: String?, val seat: String?, val invigilator: String?,
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
    private val sync: SyncExamsUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val transient = MutableStateFlow(ExamsUiState())
    private val _events = MutableSharedFlow<ExamsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ExamsEvent> = _events.asSharedFlow()

    // 考试无「手动完成」概念:已考/未考完全由时间(endAt ?: startAt vs now)决定。
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
        notes?.removePrefix("座位: ")?.takeIf { it != notes }, instructor,
    )

    private fun ExamSyncError.toMessage(): String = when (this) {
        is ExamSyncError.WrongCredentials -> "密码错误"
        is ExamSyncError.AccountLocked -> "账号锁定"
        is ExamSyncError.CaptchaRequired -> "需验证码,请网页端登录一次"
        is ExamSyncError.ParseFail -> "教务返回异常"
        is ExamSyncError.NetworkFail -> "网络错误,请重试"
        is ExamSyncError.Unexpected -> "未知错误"
    }
}
