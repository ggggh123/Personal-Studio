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
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台作业 DDL 轮询 Worker(对照 M4 GradePollWorker)。
 *   关 → success;无凭据 → 停轮 success;
 *   Stop(WrongCreds|AccountLocked) → 清凭据 + 停轮 + 取消调度 + 通知 + success;
 *   Stop(其他) → 停轮 + 取消调度 + 通知 + success;
 *   Transient → retry;
 *   Ok → replace(始终落库,首次也建 Timeline)+ 非首次新增则通知;更新 lastSeenUids/lastSyncAt。
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
        var result: BackgroundDdlResult = BackgroundDdlResult.Transient
        var usedMode = first
        return try {
            for (mode in listOf(first, otherMode(first))) {
                apiClient.open(mode)
                result = try {
                    sync.syncForBackground(DdlSyncRequest(creds.username, creds.password, mode, true))
                } catch (e: Throwable) {
                    BackgroundDdlResult.Transient
                }
                usedMode = mode
                if (result !is BackgroundDdlResult.Transient) break
                apiClient.close()
            }
            handle(result, creds, usedMode)
        } finally {
            apiClient.close()
        }
    }

    private suspend fun handle(result: BackgroundDdlResult, creds: SavedCredentials, mode: NetworkMode): Result {
        val now = System.currentTimeMillis()
        return when (result) {
            is BackgroundDdlResult.Stop -> {
                when (val reason = result.reason) {
                    DdlSyncError.WrongCredentials, DdlSyncError.AccountLocked -> {
                        credPrefs.clear(); pollPrefs.setEnabled(false); scheduler.cancel()
                        notifier.notifyStop(appContext, reason)
                        pollPrefs.setLastResult(PollResult(now, false, 0, 0, reasonText(reason)))
                    }
                    DdlSyncError.NeedReview -> {
                        pollPrefs.setLastResult(PollResult(now, false, 0, 0, "需先在乐学完成评教"))
                    }
                    else -> {
                        pollPrefs.setEnabled(false); scheduler.cancel()
                        notifier.notifyStop(appContext, reason)
                        pollPrefs.setLastResult(PollResult(now, false, 0, 0, reasonText(reason)))
                    }
                }
                Result.success()
            }
            is BackgroundDdlResult.Transient -> {
                pollPrefs.setLastResult(PollResult(now, false, 0, 0, "网络不可达,稍后自动重试"))
                Result.retry()
            }
            is BackgroundDdlResult.Ok -> {
                val diff = detector.invoke(result.events)
                replacer.invoke(result.events)
                val newCount = if (diff.isFirstRun) 0 else diff.newEvents.size
                if (!diff.isFirstRun && diff.newEvents.isNotEmpty()) {
                    notifier.notifyNewDdls(appContext, diff.newEvents)
                }
                pollPrefs.setLastSeenUids(diff.fullUids)
                pollPrefs.setLastSyncAt(now)
                if (mode != creds.lastMode) credPrefs.save(creds.username, creds.password, mode)
                pollPrefs.setLastResult(
                    PollResult(now, true, result.events.size, newCount, if (diff.isFirstRun) "已建立基线" else ""),
                )
                Result.success()
            }
        }
    }

    private fun reasonText(e: DdlSyncError): String = when (e) {
        DdlSyncError.WrongCredentials -> "密码错误,已停轮"
        DdlSyncError.AccountLocked -> "账号锁定,已停轮"
        DdlSyncError.CaptchaRequired -> "需验证码,已停轮"
        DdlSyncError.NeedReview -> "需先完成评教"
        is DdlSyncError.ParseFail -> "乐学返回异常,已停轮"
        is DdlSyncError.NetworkFail -> "网络不可达"
        is DdlSyncError.Unexpected -> "未知错误,已停轮"
    }
}
