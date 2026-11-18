package com.example.personal_studio.feature.auth

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.CasPrompter
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.bitimport.SmsChallenge
import com.example.personal_studio.domain.bitimport.SmsCheckResult
import com.example.personal_studio.domain.bitimport.SmsDecision
import com.example.personal_studio.domain.bitimport.SmsSendResult
import com.example.personal_studio.domain.bitimport.CaptchaDecision
import com.example.personal_studio.domain.bitimport.ValidateCredentialsUseCase
import com.example.personal_studio.domain.bitimport.model.AutoLoginResult
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BitLoginViewModelTest {
    @Test fun `finishing browser verification still requires successful native login`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.withBrowserLogin<Boolean>(any(), any()) } coAnswers {
            secondArg<suspend (String) -> Boolean>().invoke("https://sso.bit.edu.cn/cas/login")
        }
        coEvery { validate.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.CaptchaRequired, NetworkMode.WEBVPN)
        val creds = creds()
        val sut = vm(validate, creds)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onOpenWebLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.webLoginUrl != null)
        coVerify(exactly = 0) { creds.save(any(), any(), any()) }
        sut.onWebLoginFinished(true); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.webLoginUrl)
        assertEquals(LoginOutcome.CaptchaRequired, sut.uiState.value.error)
        coVerify(exactly = 1) { validate.validateAuto(any(), any()) }
        coVerify(exactly = 0) { creds.save(any(), any(), any()) }
    }

    @Test fun `cancelling browser verification releases UI without logging in`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.withBrowserLogin<Boolean>(any(), any()) } coAnswers {
            secondArg<suspend (String) -> Boolean>().invoke("https://sso.bit.edu.cn/cas/login")
        }
        val sut = vm(validate)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onOpenWebLogin(); advanceUntilIdle()
        sut.onWebLoginFinished(false); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.webLoginUrl)
        assertEquals(false, sut.uiState.value.loading)
        coVerify(exactly = 0) { validate.validateAuto(any(), any()) }
    }
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun creds(): ImportCredentialPrefs = mockk(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(null)
    }

    /** resolve mock:回显 lastMode(?: LOCAL),不触发探测。 */
    private fun resolveEcho(): ResolveNetworkModeUseCase {
        val m = mockk<ResolveNetworkModeUseCase>()
        coEvery { m.invoke(any()) } returns NetworkMode.LOCAL
        return m
    }

    private fun vm(
        validate: ValidateCredentialsUseCase,
        credPrefs: ImportCredentialPrefs = creds(),
        loginPrefs: LoginPrefs = mockk(relaxed = true),
        resolver: ResolveNetworkModeUseCase = resolveEcho(),
    ) = BitLoginViewModel(validate, credPrefs, loginPrefs, resolver)

    private fun browser(validate: ValidateCredentialsUseCase) {
        coEvery { validate.withBrowserLogin<Boolean>(any(), any()) } coAnswers {
            secondArg<suspend (String) -> Boolean>().invoke("https://sso.bit.edu.cn/cas/login")
        }
    }

    @Test fun `SMS requirement opens browser and resumes native verification once`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        browser(validate)
        coEvery { validate.validateAuto(any(), any()) } returnsMany listOf(
            AutoLoginResult(LoginOutcome.CaptchaRequired, NetworkMode.WEBVPN),
            AutoLoginResult(LoginOutcome.Success, NetworkMode.WEBVPN),
        )
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.webLoginUrl != null)
        coVerify(exactly = 0) { credentials.save(any(), any(), any()) }
        sut.onWebLoginFinished(true); advanceUntilIdle()
        coVerify(exactly = 2) { validate.validateAuto(any(), any()) }
        coVerify(exactly = 1) { validate.withBrowserLogin<Boolean>(any(), any()) }
        coVerify(exactly = 1) { credentials.save("student", "pw", NetworkMode.WEBVPN) }
        assertEquals(false, sut.uiState.value.loading)
    }

    @Test fun `unfinished browser verification does not automatically reopen or save credentials`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        browser(validate)
        coEvery { validate.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.CaptchaRequired, NetworkMode.WEBVPN)
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        sut.onWebLoginFinished(true); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.webLoginUrl)
        assertEquals(LoginOutcome.CaptchaRequired, sut.uiState.value.error)
        coVerify(exactly = 2) { validate.validateAuto(any(), any()) }
        coVerify(exactly = 1) { validate.withBrowserLogin<Boolean>(any(), any()) }
        coVerify(exactly = 0) { credentials.save(any(), any(), any()) }
    }

    @Test fun `cancel pending login then retry without saving a cancelled attempt`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any(), any()) } coAnswers { awaitCancellation() }
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.loading)
        sut.onCancelLogin(); advanceUntilIdle()
        assertEquals(false, sut.uiState.value.loading)
        assertEquals(null, sut.uiState.value.error)
        coVerify(exactly = 0) { credentials.save(any(), any(), any()) }
        coEvery { validate.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        sut.onLogin(); advanceUntilIdle()
        coVerify(exactly = 1) { credentials.save("student", "pw", NetworkMode.LOCAL) }
    }

    @Test fun `cancelling while waiting for browser session frees login UI`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.withBrowserLogin<Boolean>(any(), any()) } coAnswers { awaitCancellation() }
        val sut = vm(validate)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onOpenWebLogin(); advanceUntilIdle()
        sut.onCancelLogin(); advanceUntilIdle()
        assertEquals(false, sut.uiState.value.loading)
        assertEquals(null, sut.uiState.value.webLoginUrl)
        coVerify(exactly = 0) { validate.validateAuto(any(), any()) }
    }

    @Test fun `repeated taps and skip cannot launch competing login operations`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any(), any()) } coAnswers { awaitCancellation() }
        val loginPrefs = mockk<LoginPrefs>(relaxed = true)
        val sut = vm(validate, loginPrefs = loginPrefs)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); sut.onLogin(); sut.onOpenWebLogin(); sut.onSkip()
        advanceUntilIdle()
        coVerify(exactly = 1) { validate.validateAuto(any(), any()) }
        coVerify(exactly = 0) { validate.withBrowserLogin<Boolean>(any(), any()) }
        coVerify(exactly = 0) { loginPrefs.setHasSeenLogin(any()) }
        sut.onCancelLogin(); advanceUntilIdle()
    }

    @Test fun `probe exception does not leave loading stuck`() = runTest {
        val resolver = mockk<ResolveNetworkModeUseCase>()
        coEvery { resolver.invoke(any()) } throws IllegalStateException("probe")
        val sut = vm(mockk(), resolver = resolver)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        assertEquals(false, sut.uiState.value.loading)
        assertTrue(sut.uiState.value.error is LoginOutcome.Unexpected)
    }

    @Test fun `username is trimmed while password whitespace remains unchanged`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("  student  "); sut.onPasswordChange(" pw ")
        sut.onLogin(); advanceUntilIdle()
        coVerify { validate.validateAuto(match { it.username == "student" && it.password == " pw " }, any()) }
        coVerify { credentials.save("student", " pw ", NetworkMode.LOCAL) }
    }

    @Test fun `successful login saves creds + sets flag + emits Succeeded`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        val credPrefs = creds()
        val loginPrefs = mockk<LoginPrefs>(relaxed = true)
        val sut = vm(validate, credPrefs, loginPrefs)
        sut.onUsernameChange("2024xx"); sut.onPasswordChange("pw")
        sut.events.test {
            sut.onLogin()
            advanceUntilIdle()
            assertEquals(BitLoginEvent.Succeeded(firstSyncPending = true), awaitItem())
        }
        coVerify { credPrefs.save("2024xx", "pw", NetworkMode.LOCAL) }
        coVerify { loginPrefs.setHasSeenLogin(true) }
    }

    @Test fun `auto login fallback persists the reachable mode`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        // 自动:校内不可达、回退校外成功 → validateAuto 回告 WEBVPN。
        coEvery { validate.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.WEBVPN)
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p")
        sut.onLogin(); advanceUntilIdle()
        coVerify { credPrefs.save("u", "p", NetworkMode.WEBVPN) }
    }

    @Test fun `login always persists creds and never clears (remember-password removed)`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase> { coEvery { this@mockk.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL) }
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p")
        sut.onLogin(); advanceUntilIdle()
        coVerify { credPrefs.save("u", "p", NetworkMode.LOCAL) }
        coVerify(exactly = 0) { credPrefs.clear() }
    }

    @Test fun `wrong credentials sets error and does not save`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase> { coEvery { this@mockk.validateAuto(any(), any()) } returns AutoLoginResult(LoginOutcome.WrongCredentials, NetworkMode.LOCAL) }
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p")
        sut.onLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.error is LoginOutcome.WrongCredentials)
        assertEquals(false, sut.uiState.value.loading)
        coVerify(exactly = 0) { credPrefs.save(any(), any(), any()) }
    }

    @Test fun `skip sets flag and emits Skipped without saving`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>(relaxed = true)
        val credPrefs = creds()
        val loginPrefs = mockk<LoginPrefs>(relaxed = true)
        val sut = vm(validate, credPrefs, loginPrefs)
        sut.events.test {
            sut.onSkip()
            advanceUntilIdle()
            assertEquals(BitLoginEvent.Skipped, awaitItem())
        }
        coVerify { loginPrefs.setHasSeenLogin(true) }
        coVerify(exactly = 0) { credPrefs.save(any(), any(), any()) }
    }

    /** A school that wants SMS: the first code is wrong, the second is right. */
    private class FakeChallenge : SmsChallenge {
        override val phoneTail = "1234"
        var sends = 0
        val checked = mutableListOf<Pair<String, Boolean>>()
        override suspend fun send(): SmsSendResult = SmsSendResult.Sent(60).also { sends++ }
        override suspend fun check(code: String, trustDevice: Boolean): SmsCheckResult {
            checked += code to trustDevice
            return if (code == "123456") SmsCheckResult.Accepted else SmsCheckResult.Rejected("验证码错误")
        }
    }

    @Test fun `SMS second step is answered in the sheet and the login finishes`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        val challenge = FakeChallenge()
        var decision: SmsDecision? = null
        coEvery { validate.validateAuto(any(), any()) } coAnswers {
            decision = secondArg<CasPrompter>().sms(challenge)
            AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        }
        val creds = creds()
        val sut = vm(validate, creds)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()

        val sheet = sut.uiState.value.sms!!
        assertEquals("1234", sheet.phoneTail)
        assertEquals(1, sheet.sentCount)
        assertEquals(false, sheet.sending)
        assertEquals(BitLoginStage.SmsVerification, sut.uiState.value.stage)
        assertTrue(sut.uiState.value.waitingForPerson)

        sut.onSmsSubmit("000000", trustDevice = true); advanceUntilIdle()
        assertEquals("验证码错误", sut.uiState.value.sms!!.error)
        sut.onSmsResend(); advanceUntilIdle()
        assertEquals(2, sut.uiState.value.sms!!.sentCount)
        coVerify(exactly = 0) { creds.save(any(), any(), any()) }

        sut.onSmsSubmit("123456", trustDevice = false); advanceUntilIdle()
        assertEquals(SmsDecision.Verified("123456", false), decision)
        assertEquals(null, sut.uiState.value.sms)
        assertEquals(listOf("000000" to true, "123456" to false), challenge.checked)
        coVerify { creds.save("student", "pw", NetworkMode.LOCAL) }
    }

    @Test fun `choosing the school's page from the sheet opens it`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any(), any()) } coAnswers {
            val choice = secondArg<CasPrompter>().sms(FakeChallenge())
            assertEquals(SmsDecision.UseWebPage, choice)
            AutoLoginResult(LoginOutcome.CaptchaRequired, NetworkMode.LOCAL)
        }
        coEvery { validate.withBrowserLogin<Boolean>(any(), any()) } coAnswers {
            secondArg<suspend (String) -> Boolean>().invoke("https://sso.bit.edu.cn/cas/login")
        }
        val sut = vm(validate)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        sut.onPromptUseWebPage(); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.sms)
        assertTrue(sut.uiState.value.webLoginUrl != null)
    }

    @Test fun `closing the SMS sheet cancels the sign-in`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any(), any()) } coAnswers {
            secondArg<CasPrompter>().sms(FakeChallenge())
            AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        }
        val creds = creds()
        val sut = vm(validate, creds)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        sut.onCancelLogin(); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.sms)
        assertEquals(false, sut.uiState.value.loading)
        coVerify(exactly = 0) { creds.save(any(), any(), any()) }
    }

    @Test fun `picture captcha is typed in its sheet`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        var answer: CaptchaDecision? = null
        coEvery { validate.validateAuto(any(), any()) } coAnswers {
            answer = secondArg<CasPrompter>().captcha(byteArrayOf(1, 2, 3))
            AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        }
        val sut = vm(validate)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        assertEquals(BitLoginStage.CaptchaVerification, sut.uiState.value.stage)
        sut.onCaptchaSubmit(" ab12 "); advanceUntilIdle()
        assertEquals(CaptchaDecision.Answer("ab12"), answer)
        assertEquals(null, sut.uiState.value.captcha)
    }

    @Test fun `the sheet says where the code went`() {
        assertEquals("验证码已发送至尾号 1234 的手机。", com.example.personal_studio.feature.auth.ui.smsSentText(SmsPromptUi("1234", sending = false, sentCount = 1)))
        assertEquals("刚才发给绑定的手机的验证码仍然有效，请输入它。", com.example.personal_studio.feature.auth.ui.smsSentText(SmsPromptUi(null, sending = false, sentCount = 1, stillValid = true)))
        assertTrue(com.example.personal_studio.feature.auth.ui.smsSentText(SmsPromptUi("1234")).contains("正在向尾号 1234 的手机发送"))
    }
}
