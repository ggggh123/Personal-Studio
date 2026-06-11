package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.otherMode
import com.example.personal_studio.domain.bitimport.model.AutoLoginResult
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

    /**
     * Auto 验证:先按 [req].networkMode(通常取 lastMode)试,仅连接级失败([LoginOutcome.NetworkFail])
     * 自动换另一网络模式重试一次。返回最终 outcome + 产生它的模式(Success 时即生效 mode,供持久化
     * lastMode)。非连接级结果(成功/密码错/锁定/验证码/Unexpected)立即返回,不换网。
     */
    suspend fun validateAuto(req: LoginRequest): AutoLoginResult {
        val modes = listOf(req.networkMode, otherMode(req.networkMode))
        var last = AutoLoginResult(LoginOutcome.NetworkFail("unreachable"), req.networkMode)
        for (mode in modes) {
            val outcome = invoke(req.copy(networkMode = mode))
            if (outcome !is LoginOutcome.NetworkFail) return AutoLoginResult(outcome, mode)
            last = AutoLoginResult(outcome, mode)
        }
        return last
    }
}
