package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.CasApi
import com.example.personal_studio.data.network.bit.CasSecondFactorPage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * The SMS second step, with the same calls as the school's own page: look up the bound phone
 * (an opaque id plus a masked number), send a code to it, check the code. The caller then posts
 * the final `smsLogin` form. Codes are never logged or kept.
 */
internal class CasSmsChallenge(private val api: CasApi, private val page: CasSecondFactorPage) : SmsChallenge {
    override var phoneTail: String? = null
        private set
    private var phone = ""

    /** Finds the phone to send to; false when there is none, and the school's page has to take over. */
    suspend fun prepare(): Boolean {
        val data = try {
            api.post(PHONE, buildJsonObject { put("userId", page.userObjectId) })?.get("data") as? JsonObject
        } catch (_: IOException) {
            null
        }
        phone = data.text("tel").ifBlank { page.phone }
        phoneTail = data.text("maskTel").filter(Char::isDigit).takeLast(4).takeIf { it.length == 4 }
        return phone.isNotBlank()
    }

    override suspend fun send(): SmsSendResult = try {
        val reply = api.post(SEND, buildJsonObject { put("phone", phone); put("businessNo", "0008") })
        when {
            reply == null -> SmsSendResult.Failed("学校没有回应，请稍后重发")
            CasApi.code(reply) == 200 -> SmsSendResult.Sent(COOLDOWN)
            else -> stillValid(reply)?.let { SmsSendResult.Sent(it, stillValid = true) }
                ?: SmsSendResult.Failed(CasApi.message(reply).ifBlank { "验证码没有发出，请稍后重发" })
        }
    } catch (_: IOException) {
        SmsSendResult.Failed("网络不稳定，验证码没有发出，请重发")
    }

    override suspend fun check(code: String, trustDevice: Boolean): SmsCheckResult = try {
        val reply = api.post(CHECK, buildJsonObject {
            put("phone", phone)
            put("token", code)
            put("delete", false)
            put("trustDevice", trustDevice)
        })
        if (reply != null && CasApi.code(reply) == 200) SmsCheckResult.Accepted
        else SmsCheckResult.Rejected(reply?.let(CasApi::message)?.ifBlank { null } ?: "验证码不对或已过期")
    } catch (_: IOException) {
        SmsCheckResult.Rejected("网络不稳定，请重试")
    }

    /**
     * A code sent moments ago is still valid: the school answers 412 / 8000068 with the seconds
     * left, or says so in words. Returns the seconds to wait before resending, or null.
     */
    private fun stillValid(reply: JsonObject): Int? {
        val data = reply["data"] as? JsonObject
        if (CasApi.code(reply) == 412 && (data?.get("errorCode") as? JsonPrimitive)?.intOrNull == 8000068) {
            val remaining = data.text("errorMessage").let { CasApi.parse(it) }?.get("remaining") as? JsonPrimitive
            return remaining?.intOrNull?.takeIf { it > 0 } ?: COOLDOWN
        }
        val words = CasApi.message(reply)
        return if ("验证码" in words && "有效期内" in words) COOLDOWN else null
    }

    private fun JsonObject?.text(key: String): String =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    companion object {
        const val PHONE = "/cas/api/protected/sms/getPhoneNumberByUserId"
        const val SEND = "/cas/api/protected/sms/publicNoToken/sendSmsCode"
        const val CHECK = "/cas/api/protected/sms/checkToken"
        const val COOLDOWN = 60
    }
}
