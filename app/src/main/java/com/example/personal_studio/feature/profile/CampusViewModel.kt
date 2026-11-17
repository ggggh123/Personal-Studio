package com.example.personal_studio.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitgrades.GradeBookSource
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.auth.LogoutUseCase
import com.example.personal_studio.domain.bitimport.SyncCenter
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import com.example.personal_studio.feature.timeline.today.TodayClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A background check on the 校园 page. */
data class PollUi(val enabled: Boolean = false, val intervalHours: Int = 6)

/** One upcoming item shown under a tile ("明天 19:00 · 大学物理 II 期中"). */
data class Upcoming(val title: String, val at: Long)

data class CampusUiState(
    val loaded: Boolean = false,
    val loggedIn: Boolean = false,
    val username: String? = null,
    val networkMode: NetworkMode? = null,
    val lastSyncAt: Long? = null,
    val courseCount: Int = 0,
    val ddlOpen: Int = 0,
    val nextDdl: Upcoming? = null,
    val examsUpcoming: Int = 0,
    val nextExam: Upcoming? = null,
    val gradeCount: Int = 0,
    val gpa: Double? = null,
    val latestTerm: String? = null,
    val gradesPoll: PollUi = PollUi(intervalHours = 6),
    val ddlPoll: PollUi = PollUi(intervalHours = 12),
    val now: Long = 0,
)

/** Open Lexue assignments and upcoming exams, counted the same way as their own pages. */
internal data class SchoolAgenda(val ddlOpen: Int, val nextDdl: Upcoming?, val examsUpcoming: Int, val nextExam: Upcoming?)

internal fun schoolAgenda(ddls: List<TimelineItemEntity>, exams: List<TimelineItemEntity>, now: Long): SchoolAgenda {
    val open = ddls.filter { !it.isDone && it.startAt >= now }.sortedBy { it.startAt }
    val ahead = exams.filter { (it.endAt ?: it.startAt) >= now }.sortedBy { it.startAt }
    return SchoolAgenda(
        ddlOpen = open.size,
        nextDdl = open.firstOrNull()?.let { Upcoming(it.title, it.startAt) },
        examsUpcoming = ahead.size,
        nextExam = ahead.firstOrNull()?.let { Upcoming(it.title, it.startAt) },
    )
}

/** "1120 **** 21": enough to recognise the account, not enough to read it out. */
fun maskAccount(username: String): String {
    val u = username.trim()
    return if (u.length >= 8) "${u.take(4)} **** ${u.takeLast(2)}" else u
}

/**
 * 校园: school account, 一键同步 with counts, entries to grades / exams / assignments / empty
 * rooms, the two background checks, and logout (optionally clearing downloaded school data).
 */
@HiltViewModel
class CampusViewModel @Inject constructor(
    credPrefs: ImportCredentialPrefs,
    loginPrefs: LoginPrefs,
    bookSource: GradeBookSource,
    timelineDao: TimelineDao,
    private val gradesSyncPrefs: GradesSyncPrefs,
    private val ddlSyncPrefs: DdlSyncPrefs,
    private val gradesScheduler: GradesPollScheduler,
    private val ddlScheduler: DdlPollScheduler,
    private val logout: LogoutUseCase,
    private val syncCenter: SyncCenter,
    private val clock: TodayClock,
) : ViewModel() {

    private val book = bookSource.books
    private val agenda = combine(timelineDao.observeImportedDdls(), timelineDao.observeImportedExams()) { d, e -> d to e }
    private val polls = combine(gradesSyncPrefs.observe, ddlSyncPrefs.observe) { g, d ->
        PollUi(g.enabled, g.intervalHours) to PollUi(d.enabled, d.intervalHours)
    }
    private val account = combine(credPrefs.observeAll(), loginPrefs.observeLastSyncAt) { c, s -> c to s }

    val uiState: StateFlow<CampusUiState> = combine(
        account, book, agenda, polls, timelineDao.observeCourseCount(),
    ) { (creds, lastSync), gradeBook, (ddls, exams), (g, d), courses ->
        val now = clock.now()
        val a = schoolAgenda(ddls, exams, now)
        CampusUiState(
            loaded = true,
            loggedIn = creds != null,
            username = creds?.username,
            networkMode = creds?.lastMode,
            lastSyncAt = lastSync,
            courseCount = courses,
            ddlOpen = a.ddlOpen,
            nextDdl = a.nextDdl,
            examsUpcoming = a.examsUpcoming,
            nextExam = a.nextExam,
            gradeCount = gradeBook.terms.sumOf { it.courses.size },
            gpa = if (gradeBook.isEmpty) null else gradeBook.overallGpa,
            latestTerm = gradeBook.terms.firstOrNull()?.termName,
            gradesPoll = g,
            ddlPoll = d,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CampusUiState())

    /** Running sync, for the card's button ("同步中"). */
    val syncProgress = syncCenter.progress

    fun startSync() = syncCenter.startAll()

    fun setGradesPoll(on: Boolean) = viewModelScope.launch {
        gradesSyncPrefs.setEnabled(on)
        if (on) gradesScheduler.enqueue(gradesSyncPrefs.snapshot().intervalHours) else gradesScheduler.cancel()
    }

    fun setGradesInterval(hours: Int) = viewModelScope.launch {
        gradesSyncPrefs.setIntervalHours(hours)
        if (gradesSyncPrefs.snapshot().enabled) gradesScheduler.enqueue(hours)
    }

    fun setDdlPoll(on: Boolean) = viewModelScope.launch {
        ddlSyncPrefs.setEnabled(on)
        if (on) ddlScheduler.enqueue(ddlSyncPrefs.snapshot().intervalHours) else ddlScheduler.cancel()
    }

    fun setDdlInterval(hours: Int) = viewModelScope.launch {
        ddlSyncPrefs.setIntervalHours(hours)
        if (ddlSyncPrefs.snapshot().enabled) ddlScheduler.enqueue(hours)
    }

    fun logout(clearSchoolData: Boolean) = viewModelScope.launch { logout.invoke(clearSchoolData) }
}
