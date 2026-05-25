package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesSyncUiState(
    val username: String = "",
    val password: String = "",
    val rememberPwd: Boolean = false,
    val networkMode: NetworkMode = NetworkMode.LOCAL,
    val showPassword: Boolean = false,
    val progressSteps: List<String> = emptyList(),
    val syncing: Boolean = false,
    val error: GradesSyncError? = null,
    val done: Boolean = false,
)

@HiltViewModel
class GradesSyncViewModel @Inject constructor(
    private val syncUseCase: SyncGradesUseCase,
    private val credPrefs: ImportCredentialPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradesSyncUiState())
    val uiState: StateFlow<GradesSyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            credPrefs.observeAll().collect { saved ->
                if (saved != null) _uiState.update {
                    it.copy(username = saved.username, password = saved.password,
                        rememberPwd = true, networkMode = saved.lastMode ?: NetworkMode.LOCAL)
                }
            }
        }
    }

    fun onUsernameChange(s: String) = _uiState.update { it.copy(username = s, error = null) }
    fun onPasswordChange(s: String) = _uiState.update { it.copy(password = s, error = null) }
    fun onRememberToggle(b: Boolean) = _uiState.update { it.copy(rememberPwd = b) }
    fun onNetworkModeChange(m: NetworkMode) = _uiState.update { it.copy(networkMode = m) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onDismissError() = _uiState.update { it.copy(error = null) }
    fun onRetry() = _uiState.update { it.copy(error = null, progressSteps = emptyList()) }

    fun onSync() {
        val st = _uiState.value
        val req = GradesSyncRequest(st.username, st.password, st.networkMode, st.rememberPwd)
        viewModelScope.launch {
            syncUseCase.sync(req).collect { step -> _uiState.update { reduce(it, step) } }
        }
    }

    private fun reduce(st: GradesSyncUiState, step: SyncGradesStep): GradesSyncUiState = when (step) {
        SyncGradesStep.LoggingIn -> st.copy(syncing = true, progressSteps = listOf("登录中..."))
        SyncGradesStep.FetchingGrades -> st.copy(progressSteps = st.progressSteps + "拉取成绩")
        SyncGradesStep.FetchingRanks -> st.copy(progressSteps = st.progressSteps + "整理成绩")
        SyncGradesStep.Persisting -> st.copy(progressSteps = st.progressSteps + "写入本地")
        is SyncGradesStep.Done -> {
            if (st.rememberPwd) credPrefs.save(st.username, st.password, st.networkMode)
            else credPrefs.clear()
            st.copy(syncing = false, done = true)
        }
        is SyncGradesStep.Failed -> {
            if (step.err is GradesSyncError.WrongCredentials || step.err is GradesSyncError.AccountLocked) {
                credPrefs.clear()
            }
            st.copy(syncing = false, error = step.err)
        }
    }
}
