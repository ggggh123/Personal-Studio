package com.example.personal_studio.domain.import

import com.example.personal_studio.core.util.AesCbcCrypto
import com.example.personal_studio.data.network.bit.dto.CasInitDto
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService

/**
 * Drives the BIT CAS login flow: GET the login page → extract `execution` and
 * `croypto` (salt) via regex → encrypt the password with AesCbcCrypto → POST
 * the credentials → classify the response by status code + body content.
 *
 * Returns a [CasLoginDto] sealed instance so callers can pattern-match without
 * throwing on user-facing failures (wrong password, captcha required, etc.).
 *
 * Note: `@Inject` constructor is intentionally omitted because the `import`
 * package segment is a Java reserved word, and Hilt would generate an
 * unparseable `_Factory.java` file. Construct directly (or via a Hilt
 * `@Provides` method in a separate Kotlin module) when wiring into the graph.
 */
class SsoLoginUseCase {

    private val executionRegex = Regex("""name="execution"\s+value="([^"]+)"""")
    private val croyptoRegex = Regex("""name="croypto"\s+value="([^"]+)"""")

    /** Public for unit-test access; production code goes through [invoke]. */
    fun parseLoginPage(html: String): CasInitDto {
        val execution = executionRegex.find(html)?.groupValues?.get(1)
            ?: error("CAS init: 'execution' field not found in HTML")
        val salt = croyptoRegex.find(html)?.groupValues?.get(1)
            ?: error("CAS init: 'croypto' field not found in HTML")
        return CasInitDto(execution = execution, salt = salt)
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
