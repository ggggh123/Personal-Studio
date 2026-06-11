package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.autoNetworkFallback
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.domain.bitimport.model.ImportRequest
import com.example.personal_studio.domain.bitimport.model.ImportResult
import com.example.personal_studio.domain.bitimport.model.ImportStep
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject

/**
 * Top-level orchestrator that drives the BIT import flow. Returns a Flow that
 * emits ImportStep events; suspends at Preview until the caller (ViewModel)
 * sends `true` (confirm) or `false` (cancel) into [confirmChannel].
 *
 * Always closes the BitApiClient session in `finally`, so cookies + Retrofit
 * are dropped regardless of success/failure/cancel.
 */
class ImportCoursesUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val anchor: ResolveSemesterAnchorUseCase,
    private val mapper: MapBitCourseUseCase,
    private val replacer: ReplaceImportedCoursesUseCase,
    private val timelineDao: TimelineDao,
) {

    fun import(req: ImportRequest, confirmChannel: Channel<Boolean>): Flow<ImportStep> = flow {
        try {
            apiClient.open(req.networkMode)

            emit(ImportStep.LoggingIn)
            // SsoLoginUseCase.invoke handles the full service-bound CAS flow:
            // GET jwapp index → triggers 302 to CAS login (with service param)
            // → parse + AES-encrypt → POST credentials to the captured URL
            // → CAS issues a ticket → follows back to jxzxehall's callback
            // → cookies set for parent .bit.edu.cn / jxzxehall.bit.edu.cn.
            // After this returns Success, jwapp endpoints are authenticated.
            val loginResult = ssoLogin.invoke(
                apiClient,
                req.credentials.username,
                req.credentials.password,
            )
            val loginErr = loginResult.toImportError()
            if (loginErr != null) { emit(ImportStep.Failed(loginErr)); return@flow }

            // jwapp warm-up. Per the BIT101 reference, EVERY wdkbby module call
            // must be preceded by getAppConfig() — it establishes the per-app
            // permission token, without which the module endpoints (dqxnxq.do
            // etc.) return 403 Forbidden at the openresty edge. switchLang sets
            // the locale cookie. Calling once after login is enough since the
            // resulting cookies persist across the rest of this session.
            apiClient.jwapp.getAppConfig()
            apiClient.jwapp.switchLang()

            emit(ImportStep.FetchingTerm)
            val termResp = apiClient.jwapp.getCurrentTerm()
            val currentTerm = termResp.body()?.datas?.dqxnxq?.rows?.firstOrNull()
                ?: run { emit(ImportStep.Failed(ImportError.NoCurrentTerm)); return@flow }
            val pickedTermCode = req.termCodeOverride ?: currentTerm.code

            emit(ImportStep.FetchingWeekDate)
            // ZC=1 asks for week 1's seven days WITH their calendar dates, so we
            // can read the semester's Monday directly (no back-solving).
            val weekDate = apiClient.jwapp.getWeekAndDate(
                requestParamStr = """{"XNXQDM":"$pickedTermCode","ZC":"1"}""",
            ).body() ?: run {
                emit(ImportStep.Failed(ImportError.ParseFail("week-and-date empty body")))
                return@flow
            }
            val resolvedAnchor = anchor.invoke(weekDate.data)

            emit(ImportStep.FetchingSchedule(pickedTermCode))
            val scheduleResp = apiClient.jwapp.getSchedule(pickedTermCode)
            val rows = scheduleResp.body()?.datas?.cxxszhxqkb?.rows ?: emptyList()

            emit(ImportStep.Mapping)
            val baseSeries = (timelineDao.maxSeriesId() ?: 0L) + 1L
            val kchToSeries = mutableMapOf<String, Long>()
            val items = rows.flatMap { mapper.invoke(it, baseSeries, kchToSeries) }
            val zone = ZoneId.systemDefault()
            val countToReplace = run {
                val start = resolvedAnchor.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = resolvedAnchor.plusWeeks(25).atStartOfDay(zone).toInstant().toEpochMilli()
                timelineDao.countImportedInRange(start, end)
            }

            emit(ImportStep.Preview(items, currentTerm, countToReplace))
            val confirmed = confirmChannel.receive()
            if (!confirmed) {
                emit(ImportStep.Cancelled); return@flow
            }
            if (items.isEmpty()) {
                emit(ImportStep.Failed(ImportError.EmptySchedule)); return@flow
            }

            emit(ImportStep.Writing)
            val replaced = replacer.invoke(resolvedAnchor, zone, items)
            emit(ImportStep.Done(ImportResult(items.size, replaced, pickedTermCode)))
        } catch (io: IOException) {
            emit(ImportStep.Failed(ImportError.NetworkFail(io)))
        } catch (e: Throwable) {
            emit(ImportStep.Failed(ImportError.Unexpected(e)))
        } finally {
            apiClient.close()
        }
    }

    /** Auto 模式:先按 [req].networkMode 试,连接级失败(NetworkFail,登录阶段、Preview 之前)自动换
     *  另一网络模式重试一次。每次 attempt 用 [channelFor] 取一个**新** confirmChannel(避免跨 attempt
     *  复用已消费的 channel);成功后回告生效模式。其余失败/Cancelled 不回退。 */
    fun importAuto(
        req: ImportRequest,
        channelFor: (NetworkMode) -> Channel<Boolean>,
        onModeSucceeded: (NetworkMode) -> Unit,
    ): Flow<ImportStep> = autoNetworkFallback(
        first = req.networkMode,
        isConnFail = { it is ImportStep.Failed && it.err is ImportError.NetworkFail },
        isDone = { it is ImportStep.Done },
        switchingStep = { ImportStep.SwitchingMode(it) },
        onModeSucceeded = onModeSucceeded,
    ) { mode -> import(req.copy(networkMode = mode), channelFor(mode)) }

    private fun CasLoginDto.toImportError(): ImportError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> ImportError.WrongCredentials
        CasLoginDto.AccountLocked -> ImportError.AccountLocked
        CasLoginDto.CaptchaRequired -> ImportError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> ImportError.ParseFail("CAS: $body")
    }
}
