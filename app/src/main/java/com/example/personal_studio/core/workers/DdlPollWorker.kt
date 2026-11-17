package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.DdlNotifier
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.PollResult
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.otherMode
import com.example.personal_studio.domain.bitddl.DetectNewDdlUseCase
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.bitddl.ReplaceImportedDdlUseCase
import com.example.personal_studio.domain.bitddl.SyncAssignmentsUseCase
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncOutcome
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 后台作业 DDL 轮询 Worker(对照 M4 GradePollWorker),乐学和新乐学一起拉,各平台结果独立:
 *   关 → success;无凭据 → 停轮 success;
 *   任一平台 WrongCreds|AccountLocked → 清凭据 + 停轮 + 取消调度 + 通知 + success;
 *   至少一个平台成功 → 按平台落库(没成功的平台保留原数据)+ 已建基线的平台有新增则通知;
 *     更新 lastSeenUids/基线/lastSyncAt;没成功的平台写进结果说明,不停轮;
 *   全部失败:都是暂时性 → retry;否则按原规则(NeedReview 只记结果,其余停轮 + 通知)。
 */
@HiltWorker
class DdlPollWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val pollPrefs: DdlSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val sync: SyncAssignmentsUseCase,
    private val detector: DetectNewDdlUseCase,
    private val replacer: ReplaceImportedDdlUseCase,
    private val apiClient: BitApiClient,
    private val notifier: DdlNotifier,
    private val scheduler: DdlPollScheduler,
    private val resolveNetworkMode: ResolveNetworkModeUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!pollPrefs.snapshot().enabled) return Result.success()
        val creds = credPrefs.observeAll().value ?: run {
            pollPrefs.setEnabled(false); return Result.success()
        }
        val first = resolveNetworkMode(creds.lastMode)
        var outcome = allTransient
        var usedMode = first
        apiClient.acquireSession()
        return try {
            for (mode in listOf(first, otherMode(first))) {
                apiClient.open(mode)
                outcome = try {
                    sync.syncForBackground(DdlSyncRequest(creds.username, creds.password, mode, true))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    allTransient
                }
                usedMode = mode
                if (outcome.bySource.values.any { it !is BackgroundDdlResult.Transient }) break
                apiClient.close()
            }
            handle(outcome, creds, usedMode)
        } finally {
            try { apiClient.close() } finally { apiClient.releaseSession() }
        }
    }

    private suspend fun handle(outcome: DdlSyncOutcome, creds: SavedCredentials, mode: NetworkMode): Result {
        val now = System.currentTimeMillis()
        outcome.accountStop?.let { reason ->
            credPrefs.clear(); pollPrefs.setEnabled(false); scheduler.cancel()
            notifier.notifyStop(appContext, reason)
            pollPrefs.setLastResult(PollResult(now, false, 0, 0, reasonText(reason)))
            return Result.success()
        }
        if (outcome.succeeded.isEmpty()) {
            if (outcome.bySource.values.all { it is BackgroundDdlResult.Transient }) {
                pollPrefs.setLastResult(PollResult(now, false, 0, 0, "网络不可达,稍后自动重试"))
                return Result.retry()
            }
            when (val reason = outcome.primaryError) {
                DdlSyncError.NeedReview -> pollPrefs.setLastResult(PollResult(now, false, 0, 0, "需先在乐学完成评教"))
                else -> {
                    pollPrefs.setEnabled(false); scheduler.cancel()
                    notifier.notifyStop(appContext, reason)
                    pollPrefs.setLastResult(PollResult(now, false, 0, 0, reasonText(reason)))
                }
            }
            return Result.success()
        }
        val diff = detector.invoke(outcome)
        outcome.bySource.forEach { (source, r) -> if (r is BackgroundDdlResult.Ok) replacer.invoke(source, r.events) }
        if (diff.newEvents.isNotEmpty()) notifier.notifyNewDdls(appContext, diff.newEvents)
        pollPrefs.setLastSeenUids(diff.fullUids)
        pollPrefs.setBaselinedSources(diff.baselined.map { it.name }.toSet())
        pollPrefs.setLastSyncAt(now)
        if (mode != creds.lastMode) credPrefs.save(creds.username, creds.password, mode)
        val missed = outcome.failures.entries.joinToString("；") { (source, e) -> "${source.label}未同步：${shortText(e)}" }
        pollPrefs.setLastResult(
            PollResult(now, true, outcome.events.size, diff.newEvents.size, missed.ifEmpty { if (diff.isFirstRun) "已建立基线" else "" }),
        )
        return Result.success()
    }

    private fun shortText(e: DdlSyncError): String = when (e) {
        DdlSyncError.CaptchaRequired -> "学校要求验证"
        DdlSyncError.NeedReview -> "需先完成评教"
        is DdlSyncError.NetworkFail -> "网络不可达"
        else -> "返回异常"
    }

    private fun reasonText(e: DdlSyncError): String = when (e) {
        DdlSyncError.WrongCredentials -> "密码错误,已停轮"
        DdlSyncError.AccountLocked -> "账号锁定,已停轮"
        DdlSyncError.CaptchaRequired -> "需验证码,已停轮"
        DdlSyncError.NeedReview -> "需先完成评教"
        is DdlSyncError.ParseFail -> "作业平台返回异常,已停轮"
        is DdlSyncError.NetworkFail -> "网络不可达"
        is DdlSyncError.Unexpected -> "未知错误,已停轮"
    }

    private companion object {
        val allTransient = DdlSyncOutcome(DdlSource.entries.associateWith { BackgroundDdlResult.Transient })
    }
}
