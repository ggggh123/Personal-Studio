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
import javax.inject.Inject

data class BitLoginUiState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val error: LoginOutcome? = null,
)

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

    init {
        credPrefs.observeAll().value?.let { saved ->
            // lastMode 只作 Auto 的首选 mode,在 onLogin 时按需读取。
            _uiState.update {
                it.copy(username = saved.username, password = saved.password)
            }
        }
    }

    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onDismissError() = _uiState.update { it.copy(error = null) }

    fun onLogin() {
        val st = _uiState.value
        if (st.username.isBlank() || st.password.isBlank() || st.loading) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // 网络全自动:校内优先(lastMode=WEBVPN 时先探一次校内),再从该 mode 起 try-fallback。
            val first = resolveNetworkMode(credPrefs.observeAll().value?.lastMode)
            val r = validate.validateAuto(LoginRequest(st.username, st.password, first))
            if (r.outcome is LoginOutcome.Success) {
                // 默认始终记住密码(Keystore 加密):凭据落库 → 登录态/后台同步均依赖它。
                credPrefs.save(st.username, st.password, r.mode)
                loginPrefs.setHasSeenLogin(true)
                val firstSyncPending = !loginPrefs.snapshotFirstSyncDone()
                _uiState.update { it.copy(loading = false) }
                _events.emit(BitLoginEvent.Succeeded(firstSyncPending))
            } else {
                _uiState.update { it.copy(loading = false, error = r.outcome) }
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
