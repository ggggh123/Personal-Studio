package com.example.personal_studio.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.auth.LogoutUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.model.TimelineType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ProfileUiState(
    val loggedIn: Boolean = false,
    val username: String? = null,
    val networkMode: NetworkMode? = null,
    val gpa: Double? = null,
    val remainingCoursesThisWeek: Int = 0,   // 本周从现在起还剩几节课
    val ddlCount: Int = 0,
    val examCount: Int = 0,
    val gradesPollEnabled: Boolean = false,
    val gradesPollInterval: Int = 6,
    val ddlPollEnabled: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val credPrefs: ImportCredentialPrefs,
    gradesDao: GradesDao,
    computeGpa: ComputeGpaUseCase,
    timelineDao: TimelineDao,
    gradesSyncPrefs: GradesSyncPrefs,
    ddlSyncPrefs: DdlSyncPrefs,
    private val logout: LogoutUseCase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // 本周区间 [周一 00:00, 下周一 00:00):进 profile 时算一次;跨周由 WhileSubscribed 重订阅刷新。
    private val weekRange: Pair<Long, Long> = run {
        val zone = ZoneId.systemDefault()
        val monday = Instant.ofEpochMilli(nowProvider()).atZone(zone).toLocalDate().with(DayOfWeek.MONDAY)
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        start to end
    }

    private val bookFlow = combine(gradesDao.observeAll(), gradesDao.observeRanks()) { entries, ranks ->
        computeGpa.invoke(entries, ranks)
    }
    private val pollFlow = combine(gradesSyncPrefs.observe, ddlSyncPrefs.observe) { g, d -> g to d }
    private val timelineFlow = combine(
        timelineDao.observeLexueDdls(),
        timelineDao.observeImportedExams(),
        timelineDao.observeItemsInRange(weekRange.first, weekRange.second),
    ) { ddls, exams, weekItems -> Triple(ddls, exams, weekItems) }

    val uiState: StateFlow<ProfileUiState> = combine(
        credPrefs.observeAll(),
        bookFlow,
        timelineFlow,
        pollFlow,
    ) { creds, book, (ddls, exams, weekItems), (grades, ddlSync) ->
        val now = nowProvider()
        ProfileUiState(
            loggedIn = creds != null,
            username = creds?.username,
            networkMode = creds?.lastMode,
            gpa = if (book.isEmpty) null else book.overallGpa,
            remainingCoursesThisWeek = weekItems.count { it.type == TimelineType.COURSE && it.startAt >= now },
            ddlCount = ddls.count { !it.isDone && it.startAt >= now },
            examCount = exams.count { (it.endAt ?: it.startAt) >= now },
            gradesPollEnabled = grades.enabled,
            gradesPollInterval = grades.intervalHours,
            ddlPollEnabled = ddlSync.enabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun onLogout() = viewModelScope.launch { logout.invoke() }
}
