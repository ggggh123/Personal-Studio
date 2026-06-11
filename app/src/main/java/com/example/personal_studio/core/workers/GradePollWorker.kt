package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.GradesNotifier
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.PollResult
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.otherMode
import com.example.personal_studio.domain.bitgrades.DetectNewGradesUseCase
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
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
    private val resolveNetworkMode: ResolveNetworkModeUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!pollPrefs.snapshot().enabled) return Result.success()
        val creds = credPrefs.observeAll().value ?: run {
            pollPrefs.setEnabled(false); return Result.success()
        }
        // 校内优先 + 校外回退:先试解析出的 mode,连接级失败(Transient)换另一个;成功记住生效 mode。
        val first = resolveNetworkMode(creds.lastMode)
        var result: BackgroundSyncResult = BackgroundSyncResult.Transient
        var usedMode = first
        return try {
            for (mode in listOf(first, otherMode(first))) {
                apiClient.open(mode)
                result = try {
                    sync.syncForBackground(GradesSyncRequest(creds.username, creds.password, mode, true))
                } catch (e: Throwable) {
                    // 登录阶段 IOException 不被 syncForBackground 内部 try 捕获,在此归为 Transient。
                    BackgroundSyncResult.Transient
                }
                usedMode = mode
                if (result !is BackgroundSyncResult.Transient) break  // 非连接级 → 停(会话留给 handle 增量拉详情)
                apiClient.close()                                     // 该 mode 连接级失败,关掉再试下一个
            }
            handle(result, creds, usedMode)
        } finally {
            apiClient.close()
        }
    }

    private suspend fun handle(result: BackgroundSyncResult, creds: SavedCredentials, mode: NetworkMode): Result {
        val now = System.currentTimeMillis()
        return when (result) {
            is BackgroundSyncResult.Stop -> {
                when (val reason = result.reason) {
                    GradesSyncError.WrongCredentials, GradesSyncError.AccountLocked -> {
                        credPrefs.clear(); pollPrefs.setEnabled(false); scheduler.cancel()
                        notifier.notifyStop(appContext, reason)
                        pollPrefs.setLastResult(PollResult(now, false, 0, 0, reasonText(reason)))
                    }
                    GradesSyncError.NeedReview -> {
                        // 评教未完成是临时状态(评完即出分)→ 不停轮、不通知,只记状态。
                        pollPrefs.setLastResult(PollResult(now, false, 0, 0, "需先在教务完成评教"))
                    }
                    else -> {  // Captcha / ParseFail / 其他 → 停轮 + 通知(需手动处理)
                        pollPrefs.setEnabled(false); scheduler.cancel()
                        notifier.notifyStop(appContext, reason)
                        pollPrefs.setLastResult(PollResult(now, false, 0, 0, reasonText(reason)))
                    }
                }
                Result.success()
            }
            is BackgroundSyncResult.Transient -> {
                pollPrefs.setLastResult(PollResult(now, false, 0, 0, "网络不可达,稍后自动重试"))
                Result.retry()
            }
            is BackgroundSyncResult.Ok -> {
                val diff = detector.invoke(result.entries)
                val newCount = if (diff.isFirstRun) 0 else diff.newEntries.size
                if (!diff.isFirstRun && diff.newEntries.isNotEmpty()) {
                    val enriched = enrichDetails(diff.newEntries)
                    gradesDao.upsertAll(enriched)
                    notifier.notifyNewGrades(appContext, enriched)
                }
                pollPrefs.setLastSeenSignature(diff.fullSignature)
                pollPrefs.setLastSyncAt(now)
                if (mode != creds.lastMode) credPrefs.save(creds.username, creds.password, mode)
                pollPrefs.setLastResult(
                    PollResult(now, true, result.entries.size, newCount, if (diff.isFirstRun) "已建立基线" else ""),
                )
                Result.success()
            }
        }
    }

    private fun reasonText(e: GradesSyncError): String = when (e) {
        GradesSyncError.WrongCredentials -> "密码错误,已停轮"
        GradesSyncError.AccountLocked -> "账号锁定,已停轮"
        GradesSyncError.CaptchaRequired -> "需验证码,已停轮"
        GradesSyncError.NeedReview -> "需先完成评教"
        GradesSyncError.EmptyGrades -> "未查到成绩"
        is GradesSyncError.ParseFail -> "教务返回异常,已停轮"
        is GradesSyncError.NetworkFail -> "网络不可达"
        is GradesSyncError.Unexpected -> "未知错误,已停轮"
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
            classSize = info.classSize,
            majorSize = info.majorSize,
            schoolRankText = info.schoolRankText,
        )
    }
}
