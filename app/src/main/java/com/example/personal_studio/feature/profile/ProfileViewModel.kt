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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loggedIn: Boolean = false,
    val username: String? = null,
    val networkMode: NetworkMode? = null,
    val gpa: Double? = null,
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

    private val bookFlow = combine(gradesDao.observeAll(), gradesDao.observeRanks()) { entries, ranks ->
        computeGpa.invoke(entries, ranks)
    }
    private val pollFlow = combine(gradesSyncPrefs.observe, ddlSyncPrefs.observe) { g, d -> g to d }

    val uiState: StateFlow<ProfileUiState> = combine(
        credPrefs.observeAll(),
        bookFlow,
        timelineDao.observeLexueDdls(),
        timelineDao.observeImportedExams(),
        pollFlow,
    ) { creds, book, ddls, exams, (grades, ddlSync) ->
        val now = nowProvider()
        ProfileUiState(
            loggedIn = creds != null,
            username = creds?.username,
            networkMode = creds?.lastMode,
            gpa = if (book.isEmpty) null else book.overallGpa,
            ddlCount = ddls.count { !it.isDone && it.startAt >= now },
            examCount = exams.count { (it.endAt ?: it.startAt) >= now },
            gradesPollEnabled = grades.enabled,
            gradesPollInterval = grades.intervalHours,
            ddlPollEnabled = ddlSync.enabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun onLogout() = viewModelScope.launch { logout.invoke() }
}
