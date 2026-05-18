package com.example.personal_studio.domain.import

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
        assertEquals(CasInitDto(execution = "EXEC_TOKEN_42", salt = "0123456789abcdef"), dto)
    }

    @Test fun `successful login returns Success`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",
            "https://app.bit.edu.cn/?ticket=ST-12345"))

        val result = useCase.invoke(service, username = "20210000", password = "pw")
        assertTrue(result is CasLoginDto.Success)
    }

    @Test fun `wrong password returns WrongCredentials`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "<div class=\"login-error\">用户名或密码错误</div>"
        ))
        val result = useCase.invoke(service, "20210000", "pw")
        assertEquals(CasLoginDto.WrongCredentials, result)
    }

    @Test fun `captcha-required returns CaptchaRequired`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "<div class=\"login-error\">请输入验证码</div>"
        ))
        val result = useCase.invoke(service, "20210000", "pw")
        assertEquals(CasLoginDto.CaptchaRequired, result)
    }

    @Test fun `account-locked returns AccountLocked`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixtureHtml()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "<div class=\"login-error\">账号已锁定</div>"
        ))
        val result = useCase.invoke(service, "20210000", "pw")
        assertEquals(CasLoginDto.AccountLocked, result)
    }
}
