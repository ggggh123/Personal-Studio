package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
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
import javax.inject.Inject

data class BitLoginUiState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val rememberPwd: Boolean = true,
    /** null = 自动(默认,按需校内↔校外回退);非 null = 手动覆盖固定该模式。 */
    val modeOverride: NetworkMode? = null,
    val loading: Boolean = false,
    val error: LoginOutcome? = null,
)

sealed interface BitLoginEvent {
    object Succeeded : BitLoginEvent
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

    init {
        // 加载持久化的手动覆盖(null = 自动);跨会话保留用户的网络选择,使其全程生效而非只在登录那一下。
        _uiState.update { it.copy(modeOverride = credPrefs.modeOverride()) }
        credPrefs.observeAll().value?.let { saved ->
            // lastMode 只作 Auto 的首选 mode,在 onLogin 时按需读取。
            _uiState.update {
                it.copy(username = saved.username, password = saved.password, rememberPwd = true)
            }
        }
    }

    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onRememberToggle(v: Boolean) = _uiState.update { it.copy(rememberPwd = v) }
    /** null = 自动;LOCAL/WEBVPN = 手动覆盖。**持久化**,使其全程(各功能同步,不止登录)生效、跳过探测。 */
    fun onNetworkModeChange(m: NetworkMode?) {
        credPrefs.setModeOverride(m)
        _uiState.update { it.copy(modeOverride = m) }
    }
    fun onDismissError() = _uiState.update { it.copy(error = null) }

    fun onLogin() {
        val st = _uiState.value
        if (st.username.isBlank() || st.password.isBlank() || st.loading) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val override = st.modeOverride
            val (outcome, mode) = if (override != null) {
                // 手动覆盖:单次该 mode,不回退。
                validate.invoke(LoginRequest(st.username, st.password, override)) to override
            } else {
                // 自动:校内优先(lastMode=WEBVPN 时先探一次校内),再从该 mode 起 try-fallback。
                val first = resolveNetworkMode(credPrefs.observeAll().value?.lastMode)
                val r = validate.validateAuto(LoginRequest(st.username, st.password, first))
                r.outcome to r.mode
            }
            if (outcome is LoginOutcome.Success) {
                if (st.rememberPwd) credPrefs.save(st.username, st.password, mode)
                else credPrefs.clear()
                loginPrefs.setHasSeenLogin(true)
                _uiState.update { it.copy(loading = false) }
                _events.emit(BitLoginEvent.Succeeded)
            } else {
                _uiState.update { it.copy(loading = false, error = outcome) }
            }
        }
    }

    fun onSkip() {
        viewModelScope.launch {
            loginPrefs.setHasSeenLogin(true)
            _events.emit(BitLoginEvent.Skipped)
        }
    }
}
