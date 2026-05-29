package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
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
    val networkMode: NetworkMode = NetworkMode.LOCAL,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(BitLoginUiState())
    val uiState: StateFlow<BitLoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BitLoginEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BitLoginEvent> = _events.asSharedFlow()

    init {
        credPrefs.observeAll().value?.let { saved ->
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

    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onRememberToggle(v: Boolean) = _uiState.update { it.copy(rememberPwd = v) }
    fun onNetworkModeChange(m: NetworkMode) = _uiState.update { it.copy(networkMode = m) }
    fun onDismissError() = _uiState.update { it.copy(error = null) }

    fun onLogin() {
        val st = _uiState.value
        if (st.username.isBlank() || st.password.isBlank() || st.loading) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val outcome = validate.invoke(LoginRequest(st.username, st.password, st.networkMode))
            if (outcome is LoginOutcome.Success) {
                if (st.rememberPwd) credPrefs.save(st.username, st.password, st.networkMode)
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
