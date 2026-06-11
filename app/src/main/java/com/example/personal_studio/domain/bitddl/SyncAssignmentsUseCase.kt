package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.autoNetworkFallback
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitddl.model.DdlSyncStep
import com.example.personal_studio.domain.bitddl.model.LexueUrlResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 作业 DDL 同步编排。策略 Y:优先用持久化 .ics URL 免登录直拉;无/失效则登录派生。
 *   - sync(req): Flow<DdlSyncStep>      前台手动刷新(自管 open/close,落库 + 设基线)。
 *   - syncForBackground(req)            后台静默(假设 apiClient 已 open,仅返回 fetch 结果)。
 */
class SyncAssignmentsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val parser: LexueIcalParser,
    private val generateUrl: GenerateLexueIcalUrlUseCase,
    private val prefs: DdlSyncPrefs,
    private val replacer: ReplaceImportedDdlUseCase,
) {
    fun sync(req: DdlSyncRequest): Flow<DdlSyncStep> = flow {
        try {
            apiClient.open(req.networkMode)
            emit(DdlSyncStep.FetchingCalendar)
            when (val result = fetchEvents(req)) {
                is BackgroundDdlResult.Stop -> emit(DdlSyncStep.Failed(result.reason))
                is BackgroundDdlResult.Transient ->
                    emit(DdlSyncStep.Failed(DdlSyncError.NetworkFail("网络错误,请重试")))
                is BackgroundDdlResult.Ok -> {
                    replacer.invoke(result.events)
                    prefs.setLastSeenUids(result.events.map { it.uid }.toSet())
                    prefs.setLastSyncAt(System.currentTimeMillis())
                    emit(DdlSyncStep.Done(result.events.size, 0))
                }
            }
        } finally {
            apiClient.close()
        }
    }

    /** Auto 模式:先按 [req].networkMode 试,连接级失败(NetworkFail)自动换另一网络模式重试一次,
     *  成功后回告生效模式。其余失败不回退。 */
    fun syncAuto(req: DdlSyncRequest, onModeSucceeded: (NetworkMode) -> Unit): Flow<DdlSyncStep> =
        autoNetworkFallback(
            first = req.networkMode,
            isConnFail = { it is DdlSyncStep.Failed && it.error is DdlSyncError.NetworkFail },
            isDone = { it is DdlSyncStep.Done },
            switchingStep = { DdlSyncStep.SwitchingMode(it) },
            onModeSucceeded = onModeSucceeded,
        ) { mode -> sync(req.copy(networkMode = mode)) }

    /** 后台:假设 apiClient 已 open。 */
    suspend fun syncForBackground(req: DdlSyncRequest): BackgroundDdlResult = fetchEvents(req)

    private suspend fun fetchEvents(req: DdlSyncRequest): BackgroundDdlResult {
        var url = prefs.snapshot().icalUrl
        if (url == null) {
            when (val r = generateUrl.invoke(req)) {
                is LexueUrlResult.Failed -> return r.toBackgroundFail()
                is LexueUrlResult.Ok -> { url = r.url; prefs.setIcalUrl(url) }
            }
        }
        return try {
            val body = fetchIcs(url!!)
            if (looksLikeCalendar(body)) {
                BackgroundDdlResult.Ok(parser.parse(body))
            } else {
                when (val r = generateUrl.invoke(req)) {
                    is LexueUrlResult.Failed -> r.toBackgroundFail()
                    is LexueUrlResult.Ok -> {
                        prefs.setIcalUrl(r.url)
                        val body2 = fetchIcs(r.url)
                        if (looksLikeCalendar(body2)) BackgroundDdlResult.Ok(parser.parse(body2))
                        else BackgroundDdlResult.Stop(DdlSyncError.ParseFail("非日历响应"))
                    }
                }
            }
        } catch (io: IOException) {
            BackgroundDdlResult.Transient
        } catch (e: Throwable) {
            BackgroundDdlResult.Transient
        }
    }

    private suspend fun fetchIcs(url: String): String {
        val resp = apiClient.lexue.getIcs(url)
        return (resp.body() ?: resp.errorBody())?.string().orEmpty()
    }

    private fun looksLikeCalendar(body: String): Boolean = body.contains("BEGIN:VCALENDAR")

    /** 派生 URL(含登录)失败:连接级(NetworkFail)归为 Transient(可换网络/重试),其余(密码错/评教等)Stop。 */
    private fun LexueUrlResult.Failed.toBackgroundFail(): BackgroundDdlResult =
        if (error is DdlSyncError.NetworkFail) BackgroundDdlResult.Transient
        else BackgroundDdlResult.Stop(error)
}
