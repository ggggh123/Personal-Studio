package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.core.util.CasAesCrypto
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasInitDto
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import javax.inject.Inject

/**
 * Drives the BIT CAS login flow.
 *
 * The naive flow (GET /cas/login → POST /cas/login) gets a 401 in production —
 * BIT's CAS is **service-bound**, meaning the POST must go to the EXACT URL
 * the redirect chain landed at, including a `service=<callback>?sessionToken=<nonce>`
 * query parameter that's unique per redirect. So the real flow is:
 *
 *   1. GET the protected resource (jwapp's index endpoint). OkHttp follows the
 *      302 chain → final URL is `sso.bit.edu.cn/cas/login?service=<encoded callback>`
 *      and the response body is the CAS login HTML.
 *   2. Parse execution + croypto from the HTML.
 *   3. AES-encrypt the password and the empty-JSON captcha payload.
 *   4. POST to the captured final URL (via Retrofit `@Url`), preserving the
 *      service param.
 *   5. CAS validates and 302s to the callback URL (with a `ticket=ST-...` param).
 *      OkHttp follows it; the callback validates the ticket and sets a session
 *      cookie scoped to .bit.edu.cn / jxzxehall.bit.edu.cn.
 *   6. Subsequent jwapp calls are now authenticated.
 *
 * Returns a [CasLoginDto] sealed instance so callers can pattern-match without
 * throwing on user-facing failures.
 *
 * **HTML shape**: BIT's CAS page does NOT use the conventional Spring form of
 * `<input name="execution" value="..."/>`. Instead the values live in elements
 * keyed by id:
 *
 *     <p id="login-croypto">SALT_VALUE</p>
 *     <p id="login-page-flowkey">EXECUTION_VALUE</p>
 *
 * The exact tag varies (observed `<p>`, `<span>`, `<input value="...">`).
 * [extractById] handles all three layouts.
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

    /**
     * Production entry point — drives the full service-bound CAS flow.
     *
     * Takes the whole [BitApiClient] (not just [BitCasService]) because step 1
     * needs jwapp's index endpoint to trigger the redirect, and step 4 needs
     * to POST to a dynamically-captured URL.
     */
    suspend operator fun invoke(
        api: BitApiClient,
        username: String,
        password: String,
    ): CasLoginDto {
        // Step 1: GET protected resource. OkHttp follows the 302 chain;
        // we land on the CAS login HTML with `?service=...` in the URL.
        val triggerResp = api.jwapp.getIndex()
        if (!triggerResp.isSuccessful) {
            return CasLoginDto.UnknownFailure("jwapp trigger HTTP ${triggerResp.code()}")
        }
        val landedUrl = triggerResp.raw().request.url.toString()
        val html = triggerResp.body()?.string().orEmpty()

        if ("/cas/login" !in landedUrl) {
            // Cookie jar somehow already has an authenticated session — proceed.
            return CasLoginDto.Success
        }

        val init = parseLoginPage(html)
        val encrypted = CasAesCrypto.encryptPassword(password, salt = init.salt)
        // BIT's CAS form expects `captcha_payload` to be the encrypted empty
        // JSON object `{}`, not an empty string.
        val encryptedCaptchaPayload = CasAesCrypto.encryptPassword("{}", salt = init.salt)

        // Step 2-4: POST to the captured `cas/login?service=...` URL.
        val postResp = api.cas.postLoginAt(
            url = landedUrl,
            username = username,
            encryptedPassword = encrypted,
            execution = init.execution,
            salt = init.salt,
            captchaPayload = encryptedCaptchaPayload,
        )
        // For non-2xx responses (BIT returns 401 on bad credentials), Retrofit
        // routes the body through errorBody(); body() returns null. Read either.
        val postBody = (postResp.body() ?: postResp.errorBody())?.string().orEmpty()
        return classify(postResp.code(), postBody)
    }

    /** Test-only entry point that exercises ONLY the bare CAS init+post pair
     *  against a MockWebServer-served fake login page. Kept for the existing
     *  unit-test suite which does not stand up a fake jwapp endpoint. */
    suspend fun invokeForTests(
        service: BitCasService,
        username: String,
        password: String,
    ): CasLoginDto {
        val initResp = service.getInitLogin()
        if (!initResp.isSuccessful) {
            return CasLoginDto.UnknownFailure("CAS init HTTP ${initResp.code()}")
        }
        val landedUrl = initResp.raw().request.url.toString()
        val init = parseLoginPage(initResp.body()!!.string())
        val encrypted = CasAesCrypto.encryptPassword(password, salt = init.salt)
        val encryptedCaptchaPayload = CasAesCrypto.encryptPassword("{}", salt = init.salt)
        val postResp = service.postLoginAt(
            url = landedUrl,
            username = username,
            encryptedPassword = encrypted,
            execution = init.execution,
            salt = init.salt,
            captchaPayload = encryptedCaptchaPayload,
        )
        val postBody = (postResp.body() ?: postResp.errorBody())?.string().orEmpty()
        return classify(postResp.code(), postBody)
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
