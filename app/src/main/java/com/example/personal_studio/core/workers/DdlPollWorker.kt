package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.DdlNotifier
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitddl.DetectNewDdlUseCase
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!pollPrefs.snapshot().enabled) return Result.success()
        val creds = credPrefs.observeAll().value ?: run {
            pollPrefs.setEnabled(false); return Result.success()
        }
        return try {
            apiClient.open(NetworkMode.LOCAL)
            val result = sync.syncForBackground(
                DdlSyncRequest(creds.username, creds.password, NetworkMode.LOCAL, rememberPwd = true),
            )
            handle(result)
        } catch (e: Throwable) {
            Result.retry()
        } finally {
            apiClient.close()
        }
    }

    private suspend fun handle(result: BackgroundDdlResult): Result = when (result) {
        is BackgroundDdlResult.Stop -> {
            if (result.reason is DdlSyncError.WrongCredentials
                || result.reason is DdlSyncError.AccountLocked) {
                credPrefs.clear()
            }
            pollPrefs.setEnabled(false)
            scheduler.cancel()
            notifier.notifyStop(appContext, result.reason)
            Result.success()
        }
        is BackgroundDdlResult.Transient -> Result.retry()
        is BackgroundDdlResult.Ok -> {
            val diff = detector.invoke(result.events)
            replacer.invoke(result.events)
            if (!diff.isFirstRun && diff.newEvents.isNotEmpty()) {
                notifier.notifyNewDdls(appContext, diff.newEvents)
            }
            pollPrefs.setLastSeenUids(diff.fullUids)
            pollPrefs.setLastSyncAt(System.currentTimeMillis())
            Result.success()
        }
    }
}
