package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.CasApi
import com.example.personal_studio.data.network.bit.CasSecondFactorPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CasSmsChallengeTest {
    private val page = CasSecondFactorPage("flow2", "https://sso.bit.edu.cn/cas/login".toHttpUrl(), "obj-9", phone = "")
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

    private fun api(phoneReply: String?) = mockk<CasApi> {
        coEvery { post(CasSmsChallenge.PHONE, any()) } returns phoneReply?.let(::obj)
    }

    @Test fun `finds the bound phone and shows only its last four digits`() = runTest {
        val challenge = CasSmsChallenge(api("""{"code":200,"data":{"tel":"opaque-7","maskTel":"138****1234"}}"""), page)
        assertTrue(challenge.prepare())
        assertEquals("1234", challenge.phoneTail)
    }

    @Test fun `without a phone the school's page has to take over`() = runTest {
        assertFalse(CasSmsChallenge(api("""{"code":500}"""), page).prepare())
        val offline = mockk<CasApi> { coEvery { post(CasSmsChallenge.PHONE, any()) } throws IOException("reset") }
        assertFalse(CasSmsChallenge(offline, page).prepare())
        // The page's own phone id still works, with no digits to show.
        val withPagePhone = CasSmsChallenge(api(null), page.copy(phone = "opaque-8"))
        assertTrue(withPagePhone.prepare())
        assertNull(withPagePhone.phoneTail)
    }

    @Test fun `sending reports success, a still-valid code, or the school's reason`() = runTest {
        val api = api("""{"code":200,"data":{"tel":"opaque-7","maskTel":"138****1234"}}""")
        val challenge = CasSmsChallenge(api, page).also { it.prepare() }
        coEvery { api.post(CasSmsChallenge.SEND, any()) } returnsMany listOf(
            obj("""{"code":200,"message":"OK"}"""),
            obj("""{"code":412,"data":{"errorCode":8000068,"errorMessage":"{\"remaining\":42}"}}"""),
            obj("""{"code":500,"message":"今日发送次数已达上限"}"""),
        )
        assertEquals(SmsSendResult.Sent(60), challenge.send())
        assertEquals(SmsSendResult.Sent(42, stillValid = true), challenge.send())
        assertEquals(SmsSendResult.Failed("今日发送次数已达上限"), challenge.send())
        coVerify { api.post(CasSmsChallenge.SEND, obj("""{"phone":"opaque-7","businessNo":"0008"}""")) }
    }

    @Test fun `checking sends the code with the trust choice`() = runTest {
        val api = api("""{"code":200,"data":{"tel":"opaque-7"}}""")
        val challenge = CasSmsChallenge(api, page).also { it.prepare() }
        coEvery { api.post(CasSmsChallenge.CHECK, any()) } returnsMany listOf(
            obj("""{"code":400,"message":"验证码错误"}"""),
            obj("""{"code":200}"""),
        )
        assertEquals(SmsCheckResult.Rejected("验证码错误"), challenge.check("000000", trustDevice = false))
        assertEquals(SmsCheckResult.Accepted, challenge.check("123456", trustDevice = true))
        coVerify {
            api.post(CasSmsChallenge.CHECK, obj("""{"phone":"opaque-7","token":"123456","delete":false,"trustDevice":true}"""))
        }
    }

    @Test fun `network trouble is a retryable message, never a crash`() = runTest {
        val api = api("""{"code":200,"data":{"tel":"opaque-7"}}""")
        val challenge = CasSmsChallenge(api, page).also { it.prepare() }
        coEvery { api.post(CasSmsChallenge.SEND, any()) } throws IOException("reset")
        coEvery { api.post(CasSmsChallenge.CHECK, any()) } throws IOException("reset")
        assertTrue(challenge.send() is SmsSendResult.Failed)
        assertTrue(challenge.check("123456", true) is SmsCheckResult.Rejected)
    }
}
