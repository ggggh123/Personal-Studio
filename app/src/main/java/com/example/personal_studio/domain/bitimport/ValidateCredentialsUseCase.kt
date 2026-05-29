package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.domain.bitimport.model.LoginRequest
import javax.inject.Inject

/** 立即验证 BIT 凭据:open→CAS 登录→映射→close。不落库(存凭据由调用方在 Success 后做)。 */
class ValidateCredentialsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
) {
    suspend fun invoke(req: LoginRequest): LoginOutcome {
        return try {
            apiClient.open(req.networkMode)
            when (val dto = ssoLogin.invoke(apiClient, req.username, req.password)) {
                CasLoginDto.Success -> LoginOutcome.Success
                CasLoginDto.WrongCredentials -> LoginOutcome.WrongCredentials
                CasLoginDto.AccountLocked -> LoginOutcome.AccountLocked
                CasLoginDto.CaptchaRequired -> LoginOutcome.CaptchaRequired
                is CasLoginDto.UnknownFailure -> LoginOutcome.Unexpected(dto.body)
            }
        } catch (io: java.io.IOException) {
            LoginOutcome.NetworkFail(io.message ?: "io")
        } catch (e: Throwable) {
            LoginOutcome.Unexpected(e.message ?: e.javaClass.simpleName)
        } finally {
            apiClient.close()
        }
    }
}
