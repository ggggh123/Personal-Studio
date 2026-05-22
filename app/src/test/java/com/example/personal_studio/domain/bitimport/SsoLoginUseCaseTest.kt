package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.dto.CasInitDto
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class SsoLoginUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var service: BitCasService
    private lateinit var useCase: SsoLoginUseCase

    private fun fixtureHtml() = javaClass.getResourceAsStream("/bit-fixtures/cas-login-page.html")!!
        .bufferedReader().readText()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        // followRedirects(false) so the 302 success response doesn't trigger a
        // real network hop to the unreachable BIT service URL during tests.
        val client = OkHttpClient.Builder().followRedirects(false).build()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
        useCase = SsoLoginUseCase()
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `parseLoginPage extracts execution and salt`() {
        val dto = useCase.parseLoginPage(fixtureHtml())
        assertEquals(
            CasInitDto(execution = "EXEC_TOKEN_42", salt = "MDEyMzQ1Njc4OWFiY2RlZg=="),
            dto,
        )
    }

    @Test fun `successful login returns Success`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",
            "https://app.bit.edu.cn/?ticket=ST-12345"))

        val result = useCase.invokeForTests(service, username = "20210000", password = "pw")
        assertTrue(result is CasLoginDto.Success)
    }

    // Failure responses must include the login-form marker "用户名密码" — that's
    // BIT's only reliable signal that the login form is being re-rendered.
    private fun failureBody(extraInner: String) = """
        <html><body>
        <form><span>请输入用户名密码</span>$extraInner</form>
        </body></html>
    """.trimIndent()

    @Test fun `wrong password returns WrongCredentials`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            failureBody("""<div class="login-error">用户名或密码错误</div>""")
        ))
        val result = useCase.invokeForTests(service, "20210000", "pw")
        assertEquals(CasLoginDto.WrongCredentials, result)
    }

    @Test fun `captcha-required returns CaptchaRequired`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            failureBody("""<input id="captcha-img"/><div>请输入验证码</div>""")
        ))
        val result = useCase.invokeForTests(service, "20210000", "pw")
        assertEquals(CasLoginDto.CaptchaRequired, result)
    }

    @Test fun `account-locked returns AccountLocked`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            failureBody("""<div class="login-error">账号已锁定</div>""")
        ))
        val result = useCase.invokeForTests(service, "20210000", "pw")
        assertEquals(CasLoginDto.AccountLocked, result)
    }

    @Test fun `wrong password with HTTP 401 status still classifies as WrongCredentials`() = runBlocking {
        // Regression test: BIT's CAS returns 401 (not 200) on bad credentials,
        // with the failure HTML in the response body. Retrofit routes the body
        // of non-2xx responses through errorBody() rather than body(); reading
        // only body() yields null → empty string → false Success.
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(401).setBody(
            failureBody("""<div class="login-error">用户名或密码错误</div>""")
        ))
        val result = useCase.invokeForTests(service, "20210000", "wrongpw")
        assertEquals(CasLoginDto.WrongCredentials, result)
    }

    @Test fun `success page WITHOUT login-form marker is Success even if it mentions 验证码 elsewhere`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        // Post-login service page that happens to contain "验证码" in a nav link or help text —
        // must NOT be misclassified as CaptchaRequired.
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """<html><body>Welcome! <a href="/captcha-management">验证码管理</a></body></html>"""
        ))
        val result = useCase.invokeForTests(service, "20210000", "pw")
        assertEquals(CasLoginDto.Success, result)
    }
}
