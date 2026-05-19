package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.core.util.AesCbcCrypto
import com.example.personal_studio.data.network.bit.dto.CasInitDto
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import javax.inject.Inject

/**
 * Drives the BIT CAS login flow: GET the login page → extract the execution
 * flow-key and croypto salt via element-id lookup → encrypt the password with
 * AesCbcCrypto → POST the credentials → classify the response by status code
 * and body content.
 *
 * Returns a [CasLoginDto] sealed instance so callers can pattern-match without
 * throwing on user-facing failures (wrong password, captcha required, etc.).
 *
 * **HTML shape**: BIT's CAS page does NOT use the conventional Spring form of
 * `<input name="execution" value="..."/>`. Instead the values live in elements
 * keyed by id:
 *
 *     <span id="login-croypto">SALT_VALUE</span>
 *     <span id="login-page-flowkey">EXECUTION_VALUE</span>
 *
 * The exact tag isn't always `<span>` — observed forms include `<input>` with
 * a `value=` attribute. [extractById] handles both layouts.
 */
class SsoLoginUseCase @Inject constructor() {

    /** Public for unit-test access; production code goes through [invoke]. */
    fun parseLoginPage(html: String): CasInitDto {
        val execution = extractById(html, "login-page-flowkey")
            ?: error(
                "CAS init: '#login-page-flowkey' not found in HTML. " +
                    "Body head: ${html.take(400)}"
            )
        val salt = extractById(html, "login-croypto")
            ?: error(
                "CAS init: '#login-croypto' not found in HTML. " +
                    "Body head: ${html.take(400)}"
            )
        return CasInitDto(execution = execution, salt = salt)
    }

    /** Pulls a value from `<tag id="ID">...</tag>` (text-node form) OR
     *  `<tag id="ID" value="..." ...>` (input-attribute form). */
    private fun extractById(html: String, id: String): String? {
        // Match `<…id="ID"…>TEXT<` first
        val textPattern = Regex(
            """<[a-zA-Z][^>]*\bid=["']${Regex.escape(id)}["'][^>]*>([^<]+)<"""
        )
        textPattern.find(html)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Fall back to `<input … id="ID" … value="VALUE" …>` (attribute order
        // either way — match id-then-value AND value-then-id)
        val idThenValue = Regex(
            """<[a-zA-Z][^>]*\bid=["']${Regex.escape(id)}["'][^>]*\bvalue=["']([^"']+)["']"""
        )
        idThenValue.find(html)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val valueThenId = Regex(
            """<[a-zA-Z][^>]*\bvalue=["']([^"']+)["'][^>]*\bid=["']${Regex.escape(id)}["']"""
        )
        return valueThenId.find(html)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotEmpty() }
    }

    suspend operator fun invoke(
        service: BitCasService,
        username: String,
        password: String,
    ): CasLoginDto {
        val initResp = service.getInitLogin()
        if (!initResp.isSuccessful) {
            return CasLoginDto.UnknownFailure("CAS init HTTP ${initResp.code()}")
        }
        val init = parseLoginPage(initResp.body()!!.string())
        val encrypted = AesCbcCrypto.encryptPassword(password, salt = init.salt)
        val postResp = service.postLogin(
            username = username,
            encryptedPassword = encrypted,
            execution = init.execution,
            salt = init.salt,
        )
        return classify(postResp.code(), postResp.body()?.string().orEmpty())
    }

    private fun classify(code: Int, body: String): CasLoginDto = when {
        code in 300..399 -> CasLoginDto.Success
        code == 200 && "用户名或密码错误" in body -> CasLoginDto.WrongCredentials
        code == 200 && "账号已锁定" in body -> CasLoginDto.AccountLocked
        code == 200 && "验证码" in body -> CasLoginDto.CaptchaRequired
        code in 200..299 -> CasLoginDto.Success
        else -> CasLoginDto.UnknownFailure("HTTP $code: ${body.take(200)}")
    }
}
