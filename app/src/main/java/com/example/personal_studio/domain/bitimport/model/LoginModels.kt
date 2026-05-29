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
