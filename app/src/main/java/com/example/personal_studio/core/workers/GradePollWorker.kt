package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.GradesNotifier
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.DetectNewGradesUseCase
import com.example.personal_studio.domain.bitgrades.JsxsdDetailParser
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 后台出分轮询 Worker。流程:
 *   1. 检查 pref enabled,关则直接 success(无副作用)。
 *   2. 读凭据,无则停轮(setEnabled(false))并 success——避免无凭据死循环。
 *   3. open(LOCAL) → syncForBackground → 按结果分支:
 *      - Stop(WrongCreds|AccountLocked) → clear creds + 停轮 + 取消调度 + 通知 + success。
 *      - Stop(其他)                       → 停轮 + 取消调度 + 通知 + success。
 *      - Transient                        → retry(WorkManager 按指数回退重试)。
 *      - Ok                               → 比对签名,非首次则对新增条目并发拉 cjfx 详情,
 *                                            落库+通知;更新 lastSeenSignature 和 lastSyncAt。
 *   4. finally close()。
 *
 * 设计要点(见 docs/superpowers/specs/2026-05-27-p6-m4-grade-poll-design.md):
 *   - 后台不并发拉所有课的 cjfx 详情(M1 那条路径仅 UI 触发用),只对"本次新增"增量拉。
 *   - 凭据存的 lastMode 不一定可达,Worker 固定走 LOCAL(校园网/Wi-Fi 触发居多)。
 *   - 异常吞 retry:WorkManager 自带退避,避免每次都走 Stop 分支误关用户。
 */
@HiltWorker
class GradePollWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val pollPrefs: GradesSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val sync: SyncGradesUseCase,
    private val detector: DetectNewGradesUseCase,
    private val detailParser: JsxsdDetailParser,
    private val apiClient: BitApiClient,
    private val gradesDao: GradesDao,
    private val notifier: GradesNotifier,
    private val scheduler: GradesPollScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!pollPrefs.snapshot().enabled) return Result.success()
        val creds = credPrefs.observeAll().value ?: run {
            pollPrefs.setEnabled(false); return Result.success()
        }
        return try {
            apiClient.open(NetworkMode.LOCAL)
            val result = sync.syncForBackground(
                GradesSyncRequest(
                    username = creds.username,
                    password = creds.password,
                    networkMode = NetworkMode.LOCAL,
                    rememberPwd = true,
                ),
            )
            handle(result)
        } catch (e: Throwable) {
            Result.retry()
        } finally {
            apiClient.close()
        }
    }

    private suspend fun handle(result: BackgroundSyncResult): Result = when (result) {
        is BackgroundSyncResult.Stop -> {
            if (result.reason is GradesSyncError.WrongCredentials
                || result.reason is GradesSyncError.AccountLocked) {
                credPrefs.clear()
            }
            pollPrefs.setEnabled(false)
            scheduler.cancel()
            notifier.notifyStop(appContext, result.reason)
            Result.success()
        }
        is BackgroundSyncResult.Transient -> Result.retry()
        is BackgroundSyncResult.Ok -> {
            val diff = detector.invoke(result.entries)
            if (!diff.isFirstRun && diff.newEntries.isNotEmpty()) {
                val enriched = enrichDetails(diff.newEntries)
                gradesDao.upsertAll(enriched)
                notifier.notifyNewGrades(appContext, enriched)
            }
            pollPrefs.setLastSeenSignature(diff.fullSignature)
            pollPrefs.setLastSyncAt(System.currentTimeMillis())
            Result.success()
        }
    }

    /** 仅对本次新增的条目并发拉 cjfx 详情(智能增量)。失败保留原条目不阻断。 */
    private suspend fun enrichDetails(entries: List<GradeEntryEntity>): List<GradeEntryEntity> =
        coroutineScope {
            entries.map { e -> async { enrichOne(e) } }.awaitAll()
        }

    private suspend fun enrichOne(e: GradeEntryEntity): GradeEntryEntity {
        val path = e.detailPath ?: return e
        val info = runCatching {
            // 去掉前导 '/' → 相对路径,保住 WEBVPN base 的 /http/<编码>/ 前缀
            // (同 SyncGradesUseCase.enrich;@Url 传绝对路径会丢前缀)。
            val r = apiClient.jwms.getCourseDetailHtml(path.removePrefix("/"))
            if (r.isSuccessful) detailParser.parse((r.body() ?: r.errorBody())?.string().orEmpty())
            else null
        }.getOrNull() ?: return e
        return e.copy(
            courseAvg = info.courseAvg,
            courseMaxScore = info.courseMaxScore,
            courseStudyCount = info.courseStudyCount,
            classRankText = info.classRankText,
            majorRankText = info.majorRankText,
        )
    }
}
