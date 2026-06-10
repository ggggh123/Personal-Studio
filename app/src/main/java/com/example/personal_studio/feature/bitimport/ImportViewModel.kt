package com.example.personal_studio.feature.bitimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.TermDto
import com.example.personal_studio.domain.bitimport.ImportCoursesUseCase
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.bitimport.model.ImportCredentials
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.domain.bitimport.model.ImportRequest
import com.example.personal_studio.domain.bitimport.model.ImportStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ImportScreen { Credentials, TermPicker, Progress, Preview }

data class ImportUiState(
    val currentScreen: ImportScreen = ImportScreen.Credentials,
    val username: String = "",
    val password: String = "",
    val rememberPwd: Boolean = false,
    val networkMode: NetworkMode = NetworkMode.LOCAL,
    val showPassword: Boolean = false,
    val terms: List<TermDto> = emptyList(),
    val currentTerm: TermDto? = null,
    val selectedTerm: TermDto? = null,
    val progressSteps: List<String> = emptyList(),
    val previewItems: List<TimelineItemEntity> = emptyList(),
    val previewTerm: TermDto? = null,
    val countToReplace: Int = 0,
    val error: ImportError? = null,
    val writing: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importUseCase: ImportCoursesUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val resolveNetworkMode: ResolveNetworkModeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var confirmChannel: Channel<Boolean>? = null

    init {
        viewModelScope.launch {
            credPrefs.observeAll().collect { saved ->
                if (saved != null) {
                    _uiState.update {
                        it.copy(
                            username = saved.username,
                            password = saved.password,
                            rememberPwd = true,
                            networkMode = saved.lastMode ?: NetworkMode.LOCAL,
                        )
                    }
                }
            }
        }
    }

    fun onUsernameChange(s: String) = _uiState.update { it.copy(username = s, error = null) }
    fun onPasswordChange(s: String) = _uiState.update { it.copy(password = s, error = null) }
    fun onRememberToggle(b: Boolean) = _uiState.update { it.copy(rememberPwd = b) }
    fun onNetworkModeChange(m: NetworkMode) = _uiState.update { it.copy(networkMode = m) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }

    /** 已登录时由 ImportEntryRoute 调用:用存好的凭据直接导入,跳过凭据表单。 */
    fun startWithSavedCreds() {
        val saved = credPrefs.observeAll().value ?: return
        _uiState.update {
            it.copy(
                username = saved.username,
                password = saved.password,
                networkMode = saved.lastMode ?: NetworkMode.LOCAL,
                rememberPwd = true,
            )
        }
        onLogin()
    }

    fun isLoggedIn(): Boolean = credPrefs.observeAll().value != null

    fun onLogin() {
        val st = _uiState.value
        viewModelScope.launch {
            // 校内优先:lastMode=WEBVPN(种进 st.networkMode)时先探一次校内,在校园网下自动脱离"粘校外"。
            val firstMode = resolveNetworkMode(st.networkMode)
            val req = ImportRequest(
                credentials = ImportCredentials(st.username, st.password),
                networkMode = firstMode,
                rememberPwd = st.rememberPwd,
                termCodeOverride = st.selectedTerm?.code,
            )
            importUseCase.importAuto(
                req = req,
                // 每次 attempt 取一个新 channel,并把它设为当前 confirmChannel(onConfirm/onCancel 发往它);
                // 回退只在登录阶段(Preview 之前)触发,故 Preview 时 confirmChannel 必指向成功那次 attempt。
                channelFor = { Channel<Boolean>(Channel.RENDEZVOUS).also { confirmChannel = it } },
                // 成功后用**生效 mode** 持久化(回退到校外时存校外,而非 UI 上的首选 mode)。
                onModeSucceeded = { winning ->
                    viewModelScope.launch {
                        if (st.rememberPwd) credPrefs.save(st.username, st.password, winning)
                        else credPrefs.clear()
                    }
                },
            ).collect { step -> _uiState.update { reduce(it, step) } }
        }
    }

    fun onChangeTerm(t: TermDto) {
        _uiState.update { it.copy(selectedTerm = t) }
    }

    fun onProceedFromTermPicker() {
        // Re-call import with the new term selection. The orchestrator
        // re-runs login (no-op cached cookie) and then resumes.
        onLogin()
    }

    fun onConfirm() { confirmChannel?.trySend(true) }
    fun onCancel() { confirmChannel?.trySend(false) }

    fun onDismissError() = _uiState.update { it.copy(error = null) }

    /** 重试 = 用已存凭据重跑导入(回 Progress),而不是回 Credentials(对已登录用户是 splash 占位)。 */
    fun onRetry() {
        _uiState.update { it.copy(error = null, progressSteps = emptyList()) }
        onLogin()
    }

    private fun reduce(st: ImportUiState, step: ImportStep): ImportUiState = when (step) {
        is ImportStep.LoggingIn -> st.copy(currentScreen = ImportScreen.Progress, progressSteps = listOf("登录中..."))
        is ImportStep.FetchingTerm -> st.copy(progressSteps = st.progressSteps + "拉取学期信息")
        is ImportStep.FetchingWeekDate -> st.copy(progressSteps = st.progressSteps + "反推学期开始日期")
        is ImportStep.FetchingSchedule -> st.copy(progressSteps = st.progressSteps + "抓取 ${step.termCode} 课表")
        is ImportStep.Mapping -> st.copy(progressSteps = st.progressSteps + "字段映射")
        is ImportStep.Preview -> st.copy(
            currentScreen = ImportScreen.Preview,
            previewItems = step.items,
            previewTerm = step.term,
            countToReplace = step.countToReplace,
        )
        is ImportStep.SwitchingMode -> st.copy(
            progressSteps = st.progressSteps + if (step.to == NetworkMode.WEBVPN)
                "校内不可达，改用校外(WEBVPN)重试…" else "校外不可达，改用校内重试…",
        )
        is ImportStep.Writing -> st.copy(writing = true)
        // 凭据持久化(用生效 mode)已移到 onLogin 的 onModeSucceeded 回调,这里只切状态。
        is ImportStep.Done -> st.copy(writing = false, done = true)
        // 取消 = 退出向导(复用 done→onClose 回上级)。不要回 Credentials —— 对已登录用户
        // 它渲染成 TerminalSplash,且推进逻辑只在 LaunchedEffect(Unit) 跑一次,会卡死在裸 logo。
        is ImportStep.Cancelled -> st.copy(done = true)
        is ImportStep.Failed -> {
            if (step.err is ImportError.WrongCredentials || step.err is ImportError.AccountLocked) {
                viewModelScope.launch { credPrefs.clear() }
            }
            // 不回 Credentials(对已登录用户渲染成 splash,error 看不到);保留当前屏,
            // error 由 ImportEntryRoute 优先渲染成 ErrorBanner。
            st.copy(error = step.err)
        }
    }
}
