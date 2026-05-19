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
        // BIT's CAS form expects `captcha_payload` to be the encrypted empty
        // JSON object `{}` — not an empty string. BIT101's source comment said
        // "removing it seems to also work", but real-device testing in P5 DoD
        // showed BIT rejects an empty payload and re-renders the login form.
        val encryptedCaptchaPayload = AesCbcCrypto.encryptPassword("{}", salt = init.salt)
        val postResp = service.postLogin(
            username = username,
            encryptedPassword = encrypted,
            execution = init.execution,
            salt = init.salt,
            captchaPayload = encryptedCaptchaPayload,
        )
        return classify(postResp.code(), postResp.body()?.string().orEmpty())
    }

    /**
     * Classify the CAS POST response.
     *
     * **Success heuristic** (mirroring BIT101's reference behaviour): the
     * string "用户名密码" appears ONLY in BIT's login-form re-render — i.e. when
     * the server is asking us to log in again. So:
     *
     *   - body contains "用户名密码"  → login failed; check sub-reason
     *   - body does NOT contain it   → success (we landed on the post-login
     *                                  service page after the followed 302)
     *
     * The previous "see the word 验证码 ⇒ CaptchaRequired" rule was too broad —
     * BIT's nav / help text can contain "验证码" even on successful pages,
     * which caused false positives.
     */
    private fun classify(code: Int, body: String): CasLoginDto {
        if ("用户名密码" !in body) return CasLoginDto.Success

        // Login form is being re-rendered — read the sub-reason from the page.
        return when {
            "用户名或密码错误" in body -> CasLoginDto.WrongCredentials
            "账号已锁定" in body -> CasLoginDto.AccountLocked
            // Captcha-required is signalled by the form's captcha-input section
            // being expanded; look for phrases unique to that state.
            "请输入验证码" in body ||
                "验证码不能为空" in body ||
                "id=\"captcha-img\"" in body ||
                "name=\"captcha_code\"" in body -> CasLoginDto.CaptchaRequired
            else -> CasLoginDto.UnknownFailure("HTTP $code: ${body.take(1000)}")
        }
    }
}
