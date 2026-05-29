package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.domain.bitimport.model.LoginRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateCredentialsUseCaseTest {
    private fun req() = LoginRequest("u", "p", NetworkMode.LOCAL)

    @Test fun `success maps to Success`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.Success
        assertEquals(LoginOutcome.Success, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `wrong credentials maps`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        assertEquals(LoginOutcome.WrongCredentials, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `account locked maps`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.AccountLocked
        assertEquals(LoginOutcome.AccountLocked, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `captcha maps`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.CaptchaRequired
        assertEquals(LoginOutcome.CaptchaRequired, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `unknown failure maps to Unexpected`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.UnknownFailure("boom")
        assert(ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()) is LoginOutcome.Unexpected)
    }

    @Test fun `io exception maps to NetworkFail`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } throws java.io.IOException("net")
        assert(ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()) is LoginOutcome.NetworkFail)
    }

    @Test fun `close is always called`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.Success
        val api = mockk<BitApiClient>(relaxed = true)
        ValidateCredentialsUseCase(api, sso).invoke(req())
        coVerify { api.close() }
    }
}
