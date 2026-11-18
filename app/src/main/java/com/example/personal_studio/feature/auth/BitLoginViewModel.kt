package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitimport.CaptchaDecision
import com.example.personal_studio.domain.bitimport.CasPrompter
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.bitimport.SmsChallenge
import com.example.personal_studio.domain.bitimport.SmsCheckResult
import com.example.personal_studio.domain.bitimport.SmsDecision
import com.example.personal_studio.domain.bitimport.SmsSendResult
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
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject

enum class BitLoginStage {
    CheckingNetwork, Authenticating, OpeningBrowser, BrowserVerification, VerifyingBrowser,
    SmsVerification, CaptchaVerification,
}

/**
 * The SMS step on screen. [sentCount] goes up with each code the school sends (the sheet restarts
 * its [cooldownSeconds] countdown from it); [stillValid] means the last code still works.
 */
data class SmsPromptUi(
    val phoneTail: String?,
    val sending: Boolean = true,
    val sentCount: Int = 0,
    val cooldownSeconds: Int = 0,
    val stillValid: Boolean = false,
    val checking: Boolean = false,
    val error: String? = null,
)

/** The picture captcha on screen. */
class CaptchaPromptUi(val image: ByteArray)

data class BitLoginUiState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val stage: BitLoginStage? = null,
    val error: LoginOutcome? = null,
    val webLoginUrl: String? = null,
    val sms: SmsPromptUi? = null,
    val captcha: CaptchaPromptUi? = null,
) {
    val loading: Boolean get() = stage != null
    /** Waiting on the person (a sheet or the school's page), not on the school. */
    val waitingForPerson: Boolean get() = webLoginUrl != null || sms != null || captcha != null
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
    /** The person's answers to the sheet on screen; null when none is open. */
    private var promptActions: Channel<PromptAction>? = null

    private sealed interface PromptAction {
        object Resend : PromptAction
        data class SubmitSms(val code: String, val trustDevice: Boolean) : PromptAction
        data class SubmitCaptcha(val code: String) : PromptAction
        object UseWebPage : PromptAction
    }

    /** Asks in the app for what the school wants during this sign-in; the login waits meanwhile. */
    private val prompter = object : CasPrompter {
        override suspend fun sms(challenge: SmsChallenge): SmsDecision {
            val actions = Channel<PromptAction>(Channel.UNLIMITED).also { promptActions = it }
            _uiState.update { it.copy(stage = BitLoginStage.SmsVerification, sms = SmsPromptUi(challenge.phoneTail)) }
            try {
                sendCode(challenge)
                for (action in actions) {
                    when (action) {
                        PromptAction.Resend -> sendCode(challenge)
                        is PromptAction.SubmitSms -> {
                            updateSms { it.copy(checking = true, error = null) }
                            when (val r = challenge.check(action.code, action.trustDevice)) {
                                SmsCheckResult.Accepted -> return SmsDecision.Verified(action.code, action.trustDevice)
                                is SmsCheckResult.Rejected -> updateSms { it.copy(checking = false, error = r.message) }
                            }
                        }
                        PromptAction.UseWebPage -> return SmsDecision.UseWebPage
                        is PromptAction.SubmitCaptcha -> Unit
                    }
                }
                return SmsDecision.UseWebPage
            } finally {
                promptActions = null
                _uiState.update { it.copy(sms = null, stage = BitLoginStage.Authenticating) }
            }
        }

        override suspend fun captcha(image: ByteArray): CaptchaDecision {
            val actions = Channel<PromptAction>(Channel.UNLIMITED).also { promptActions = it }
            _uiState.update { it.copy(stage = BitLoginStage.CaptchaVerification, captcha = CaptchaPromptUi(image)) }
            try {
                for (action in actions) {
                    when (action) {
                        is PromptAction.SubmitCaptcha -> return CaptchaDecision.Answer(action.code)
                        PromptAction.UseWebPage -> return CaptchaDecision.UseWebPage
                        else -> Unit
                    }
                }
                return CaptchaDecision.UseWebPage
            } finally {
                promptActions = null
                _uiState.update { it.copy(captcha = null, stage = BitLoginStage.Authenticating) }
            }
        }

        private suspend fun sendCode(challenge: SmsChallenge) {
            updateSms { it.copy(sending = true, error = null) }
            when (val r = challenge.send()) {
                is SmsSendResult.Sent -> updateSms {
                    it.copy(sending = false, sentCount = it.sentCount + 1, cooldownSeconds = r.cooldownSeconds, stillValid = r.stillValid)
                }
                is SmsSendResult.Failed -> updateSms { it.copy(sending = false, error = r.message) }
            }
        }

        private fun updateSms(change: (SmsPromptUi) -> SmsPromptUi) =
            _uiState.update { st -> st.copy(sms = st.sms?.let(change)) }
    }

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

    fun onSmsResend() { promptActions?.trySend(PromptAction.Resend) }

    fun onSmsSubmit(code: String, trustDevice: Boolean) {
        val digits = code.trim()
        val sms = _uiState.value.sms ?: return
        if (digits.isEmpty() || sms.checking) return
        promptActions?.trySend(PromptAction.SubmitSms(digits, trustDevice))
    }

    fun onCaptchaSubmit(code: String) {
        if (code.isNotBlank()) promptActions?.trySend(PromptAction.SubmitCaptcha(code.trim()))
    }

    /** Leave the sheet for the school's own page; the sign-in continues there. */
    fun onPromptUseWebPage() { promptActions?.trySend(PromptAction.UseWebPage) }

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
        validate.validateAuto(LoginRequest(username, password, first), prompter)
    }

    fun onSkip() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            loginPrefs.setHasSeenLogin(true)
            _events.emit(BitLoginEvent.Skipped)
        }
    }
}
