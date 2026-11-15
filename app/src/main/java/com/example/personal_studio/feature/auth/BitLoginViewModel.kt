package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.bitimport.ValidateCredentialsUseCase
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.domain.bitimport.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import javax.inject.Inject

enum class BitLoginStage {
    CheckingNetwork, Authenticating, OpeningBrowser, BrowserVerification, VerifyingBrowser,
}

data class BitLoginUiState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val stage: BitLoginStage? = null,
    val error: LoginOutcome? = null,
    val webLoginUrl: String? = null,
) {
    val loading: Boolean get() = stage != null
}

sealed interface BitLoginEvent {
    /** [firstSyncPending]=true 表示这是首启入口且尚未做过批量同步(且记住了密码),应导向一键同步页。 */
    data class Succeeded(val firstSyncPending: Boolean) : BitLoginEvent
    object Skipped : BitLoginEvent
}

@HiltViewModel
class BitLoginViewModel @Inject constructor(
    private val validate: ValidateCredentialsUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val loginPrefs: LoginPrefs,
    private val resolveNetworkMode: ResolveNetworkModeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BitLoginUiState())
    val uiState: StateFlow<BitLoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BitLoginEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BitLoginEvent> = _events.asSharedFlow()
    private var webResult: CompletableDeferred<Boolean>? = null
    private var loginJob: Job? = null

    init {
        credPrefs.observeAll().value?.let { saved ->
            _uiState.update {
                it.copy(username = saved.username, password = saved.password)
            }
        }
    }

    fun onUsernameChange(v: String) = _uiState.update { if (it.loading) it else it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { if (it.loading) it else it.copy(password = v) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onDismissError() = _uiState.update { it.copy(error = null) }

    fun onOpenWebLogin() = startLogin(openBrowser = true)

    fun onLogin() = startLogin(openBrowser = false)

    fun onCancelLogin() { loginJob?.cancel() }

    fun onWebLoginFinished(verified: Boolean) { webResult?.complete(verified) }

    private fun startLogin(openBrowser: Boolean) {
        val st = _uiState.value
        if (st.loading || st.username.isBlank() || st.password.isBlank()) return
        // Keep the account fixed throughout password, browser and native verification.
        val username = st.username.trim()
        _uiState.update { it.copy(username = username, stage = BitLoginStage.CheckingNetwork, error = null) }
        loginJob = viewModelScope.launch {
            try {
                if (openBrowser && !verifyInBrowser(username)) return@launch
                var result = verifyCredentials(username, st.password, afterBrowser = openBrowser)
                // Open the official verification flow at most once per user action.
                // An unfinished/expired browser session must not cause an endless loop.
                if (!openBrowser && result.outcome == LoginOutcome.CaptchaRequired) {
                    _uiState.update { it.copy(error = LoginOutcome.CaptchaRequired) }
                    if (!verifyInBrowser(username)) return@launch
                    result = verifyCredentials(username, st.password, afterBrowser = true)
                }
                if (result.outcome == LoginOutcome.Success) {
                    credPrefs.save(username, st.password, result.mode)
                    loginPrefs.setHasSeenLogin(true)
                    _events.emit(BitLoginEvent.Succeeded(firstSyncPending = !loginPrefs.snapshotFirstSyncDone()))
                } else {
                    _uiState.update { it.copy(error = result.outcome) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(error = LoginOutcome.Unexpected("登录暂时未完成，请重试或使用学校网页验证")) }
            } finally {
                webResult = null
                _uiState.update { it.copy(webLoginUrl = null, stage = null) }
            }
        }
    }

    private suspend fun verifyInBrowser(username: String): Boolean {
        val result = CompletableDeferred<Boolean>()
        webResult = result
        _uiState.update { it.copy(stage = BitLoginStage.OpeningBrowser) }
        return try {
            validate.withBrowserLogin(username) { url ->
                _uiState.update { it.copy(webLoginUrl = url, stage = BitLoginStage.BrowserVerification) }
                result.await()
            }
        } finally {
            webResult = null
            _uiState.update { it.copy(webLoginUrl = null) }
        }
    }

    private suspend fun verifyCredentials(username: String, password: String, afterBrowser: Boolean) = run {
        _uiState.update { it.copy(stage = BitLoginStage.CheckingNetwork, error = null) }
        val first = resolveNetworkMode(credPrefs.observeAll().value?.lastMode)
        _uiState.update { it.copy(stage = if (afterBrowser) BitLoginStage.VerifyingBrowser else BitLoginStage.Authenticating) }
        validate.validateAuto(LoginRequest(username, password, first))
    }

    fun onSkip() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            loginPrefs.setHasSeenLogin(true)
            _events.emit(BitLoginEvent.Skipped)
        }
    }
}
