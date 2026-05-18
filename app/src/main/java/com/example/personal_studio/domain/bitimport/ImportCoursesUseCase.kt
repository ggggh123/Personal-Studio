package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.BitApiClient
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
            val loginResult = ssoLogin.invoke(
                apiClient.cas,
                req.credentials.username,
                req.credentials.password,
            )
            val loginErr = loginResult.toImportError()
            if (loginErr != null) { emit(ImportStep.Failed(loginErr)); return@flow }

            // jwapp warm-up — BIT requires these calls to set session cookies
            apiClient.jwapp.getIndex()
            apiClient.jwapp.getAppConfig()
            apiClient.jwapp.switchLang()

            emit(ImportStep.FetchingTerm)
            val termResp = apiClient.jwapp.getCurrentTerm()
            val currentTerm = termResp.body()?.datas?.dqxnxq?.rows?.firstOrNull()
                ?: run { emit(ImportStep.Failed(ImportError.NoCurrentTerm)); return@flow }
            val pickedTermCode = req.termCodeOverride ?: currentTerm.code

            emit(ImportStep.FetchingWeekDate)
            val weekDate = apiClient.jwapp.getWeekAndDate(
                requestParamStr = """{"XNXQDM":"$pickedTermCode"}""",
            ).body() ?: run {
                emit(ImportStep.Failed(ImportError.ParseFail("week-and-date empty body")))
                return@flow
            }
            val resolvedAnchor = anchor.invoke(weekDate.currentWeek ?: 1, weekDate.data)

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

    private fun CasLoginDto.toImportError(): ImportError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> ImportError.WrongCredentials
        CasLoginDto.AccountLocked -> ImportError.AccountLocked
        CasLoginDto.CaptchaRequired -> ImportError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> ImportError.ParseFail("CAS: $body")
    }
}
