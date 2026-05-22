package com.example.personal_studio.data.network.bit.dto

/** Outcome of submitting a CAS login form. Filled in by parsing the response
 *  body and status code; not deserialised directly from JSON. */
sealed class CasLoginDto {
    object Success : CasLoginDto()
    object WrongCredentials : CasLoginDto()
    object AccountLocked : CasLoginDto()
    object CaptchaRequired : CasLoginDto()
    data class UnknownFailure(val body: String) : CasLoginDto()
}
