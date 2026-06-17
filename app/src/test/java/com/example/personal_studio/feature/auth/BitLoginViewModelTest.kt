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
    ) = BitLoginViewModel(validate, credPrefs, loginPrefs, resolveEcho())

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
