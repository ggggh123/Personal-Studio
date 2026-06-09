package com.example.personal_studio.domain.bitimport.model

import com.example.personal_studio.data.network.bit.NetworkMode

data class LoginRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
)

sealed interface LoginOutcome {
    object Success : LoginOutcome
    object WrongCredentials : LoginOutcome
    object AccountLocked : LoginOutcome
    object CaptchaRequired : LoginOutcome
    data class NetworkFail(val cause: String) : LoginOutcome
    data class Unexpected(val cause: String) : LoginOutcome
}

/** validateAuto 的结果:最终 outcome + 产生它的网络模式(Success 时即生效 mode,用于持久化 lastMode)。 */
data class AutoLoginResult(val outcome: LoginOutcome, val mode: NetworkMode)
