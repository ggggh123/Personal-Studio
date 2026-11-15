package com.example.personal_studio.feature.auth

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
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
        coEvery { validate.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.CaptchaRequired, NetworkMode.WEBVPN)
        val creds = creds()
        val sut = vm(validate, creds)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onOpenWebLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.webLoginUrl != null)
        coVerify(exactly = 0) { creds.save(any(), any(), any()) }
        sut.onWebLoginFinished(true); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.webLoginUrl)
        assertEquals(LoginOutcome.CaptchaRequired, sut.uiState.value.error)
        coVerify(exactly = 1) { validate.validateAuto(any()) }
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
        coVerify(exactly = 0) { validate.validateAuto(any()) }
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
        coEvery { validate.validateAuto(any()) } returnsMany listOf(
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
        coVerify(exactly = 2) { validate.validateAuto(any()) }
        coVerify(exactly = 1) { validate.withBrowserLogin<Boolean>(any(), any()) }
        coVerify(exactly = 1) { credentials.save("student", "pw", NetworkMode.WEBVPN) }
        assertEquals(false, sut.uiState.value.loading)
    }

    @Test fun `unfinished browser verification does not automatically reopen or save credentials`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        browser(validate)
        coEvery { validate.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.CaptchaRequired, NetworkMode.WEBVPN)
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        sut.onWebLoginFinished(true); advanceUntilIdle()
        assertEquals(null, sut.uiState.value.webLoginUrl)
        assertEquals(LoginOutcome.CaptchaRequired, sut.uiState.value.error)
        coVerify(exactly = 2) { validate.validateAuto(any()) }
        coVerify(exactly = 1) { validate.withBrowserLogin<Boolean>(any(), any()) }
        coVerify(exactly = 0) { credentials.save(any(), any(), any()) }
    }

    @Test fun `cancel pending login then retry without saving a cancelled attempt`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any()) } coAnswers { awaitCancellation() }
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.loading)
        sut.onCancelLogin(); advanceUntilIdle()
        assertEquals(false, sut.uiState.value.loading)
        assertEquals(null, sut.uiState.value.error)
        coVerify(exactly = 0) { credentials.save(any(), any(), any()) }
        coEvery { validate.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
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
        coVerify(exactly = 0) { validate.validateAuto(any()) }
    }

    @Test fun `repeated taps and skip cannot launch competing login operations`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any()) } coAnswers { awaitCancellation() }
        val loginPrefs = mockk<LoginPrefs>(relaxed = true)
        val sut = vm(validate, loginPrefs = loginPrefs)
        sut.onUsernameChange("student"); sut.onPasswordChange("pw")
        sut.onLogin(); sut.onLogin(); sut.onOpenWebLogin(); sut.onSkip()
        advanceUntilIdle()
        coVerify(exactly = 1) { validate.validateAuto(any()) }
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
        coEvery { validate.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
        val credentials = creds()
        val sut = vm(validate, credentials)
        sut.onUsernameChange("  student  "); sut.onPasswordChange(" pw ")
        sut.onLogin(); advanceUntilIdle()
        coVerify { validate.validateAuto(match { it.username == "student" && it.password == " pw " }) }
        coVerify { credentials.save("student", " pw ", NetworkMode.LOCAL) }
    }

    @Test fun `successful login saves creds + sets flag + emits Succeeded`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL)
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
        coEvery { validate.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.WEBVPN)
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p")
        sut.onLogin(); advanceUntilIdle()
        coVerify { credPrefs.save("u", "p", NetworkMode.WEBVPN) }
    }

    @Test fun `login always persists creds and never clears (remember-password removed)`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase> { coEvery { this@mockk.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.Success, NetworkMode.LOCAL) }
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p")
        sut.onLogin(); advanceUntilIdle()
        coVerify { credPrefs.save("u", "p", NetworkMode.LOCAL) }
        coVerify(exactly = 0) { credPrefs.clear() }
    }

    @Test fun `wrong credentials sets error and does not save`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase> { coEvery { this@mockk.validateAuto(any()) } returns AutoLoginResult(LoginOutcome.WrongCredentials, NetworkMode.LOCAL) }
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
}
