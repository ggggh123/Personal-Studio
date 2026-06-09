package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitddl.model.LexueUrlResult
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import java.io.IOException
import javax.inject.Inject

/**
 * 一次性派生乐学 iCal 订阅 URL。假设 apiClient 已 open(由调用方管 open/close)。
 * 流程:CAS 登录 → 激活乐学服务会话 → 抠 sesskey → POST 导出 → 抠 .ics URL。
 */
class GenerateLexueIcalUrlUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
) {
    suspend fun invoke(req: DdlSyncRequest): LexueUrlResult {
        val login = ssoLogin.invoke(apiClient, req.username, req.password)
        login.toDdlError()?.let { return LexueUrlResult.Failed(it) }
        return try {
            // CAS 激活须穿当前 mode 的 CAS 端点(校外走 webvpn 网关,否则 webvpn 域的 TGC 带不上)。
            // 乐学 host 本身公网可直连,故 service 仍是裸乐学(不给乐学配网关前缀)。
            apiClient.cas.activateServiceAt(apiClient.casLoginEndpoint(), LEXUE_SERVICE)
            val indexHtml = (apiClient.lexue.getIndexHtml().let { it.body() ?: it.errorBody() })?.string().orEmpty()
            val sesskey = extractSesskey(indexHtml)
                ?: return LexueUrlResult.Failed(DdlSyncError.ParseFail("no sesskey"))
            val exportHtml = (apiClient.lexue.exportCalendar(sesskey).let { it.body() ?: it.errorBody() })?.string().orEmpty()
            val url = extractIcalUrl(exportHtml)
                ?: return LexueUrlResult.Failed(DdlSyncError.ParseFail("no calendar url"))
            LexueUrlResult.Ok(url)
        } catch (io: IOException) {
            LexueUrlResult.Failed(DdlSyncError.NetworkFail(io.message ?: "io"))
        } catch (e: Throwable) {
            LexueUrlResult.Failed(DdlSyncError.Unexpected(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun CasLoginDto.toDdlError(): DdlSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> DdlSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> DdlSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> DdlSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> DdlSyncError.ParseFail("CAS: $body")
    }

    companion object {
        /** 必须与 CAS 注册的乐学 service 完全一致——待真机抓包确认。 */
        const val LEXUE_SERVICE = "https://lexue.bit.edu.cn/login/index.php"

        private val SESSKEY = Regex("""["']sesskey["']\s*:\s*["']([^"']+)["']""")
        private val ICAL_URL = Regex("""https?://[^\s"'<>]*export_execute\.php[^\s"'<>]*""")

        fun extractSesskey(html: String): String? = SESSKEY.find(html)?.groupValues?.get(1)

        fun extractIcalUrl(html: String): String? =
            ICAL_URL.find(html)?.value?.replace("&amp;", "&")
    }
}
