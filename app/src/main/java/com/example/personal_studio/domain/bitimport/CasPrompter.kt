package com.example.personal_studio.domain.bitimport

/**
 * Asks the person for an extra step the school wants during an interactive sign-in. The login
 * waits (holding the school session) until the person answers; cancelling the sign-in cancels
 * the wait. Background sync passes none and stops at "verification needed".
 */
interface CasPrompter {
    /** The school sent this sign-in to SMS verification. Drive [challenge] until it accepts a code. */
    suspend fun sms(challenge: SmsChallenge): SmsDecision

    /** The school wants the picture captcha typed before the password is checked. */
    suspend fun captcha(image: ByteArray): CaptchaDecision
}

/** The SMS step of one sign-in: the code goes to the phone bound to the account. */
interface SmsChallenge {
    /** The last digits of the bound phone, when the school tells them. */
    val phoneTail: String?

    suspend fun send(): SmsSendResult

    /** Checks [code]; [trustDevice] asks the school to skip this step on this phone for a while. */
    suspend fun check(code: String, trustDevice: Boolean): SmsCheckResult
}

sealed interface SmsSendResult {
    /** Sent (or [stillValid]: an earlier code is still good); resend after [cooldownSeconds]. */
    data class Sent(val cooldownSeconds: Int, val stillValid: Boolean = false) : SmsSendResult
    data class Failed(val message: String) : SmsSendResult
}

sealed interface SmsCheckResult {
    object Accepted : SmsCheckResult
    data class Rejected(val message: String) : SmsCheckResult
}

sealed interface SmsDecision {
    data class Verified(val code: String, val trustDevice: Boolean) : SmsDecision
    /** Finish in the school's own page instead. */
    object UseWebPage : SmsDecision
}

sealed interface CaptchaDecision {
    data class Answer(val code: String) : CaptchaDecision
    object UseWebPage : CaptchaDecision
}
