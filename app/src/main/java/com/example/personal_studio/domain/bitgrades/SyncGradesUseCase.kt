package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.GpaCalculator
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 成绩同步编排。成绩在 BIT 正方教务 (jsxsd, host jwms.bit.edu.cn) 里以 HTML 表呈现
 * （不是 ehall 的 cjcx JSON——那个 app 无成绩权限，DoD 时一直 403）。流程：
 *
 *   1. CAS 用户名密码登录（复用 P5 SsoLoginUseCase，建立 CAS TGC）。
 *   2. 用 TGC 向 CAS 换取 jwms 的服务会话（[BitCasService.activateService]）。
 *   3. GET jsxsd/kscj/cjcx_list → HTML，[JsxsdGradeParser] 解析成 GradeEntryEntity。
 *   4. 按学期算加权 GPA 落 term_ranks（M1 不抓排名——正方不直接给专业总排名）。
 *
 * 始终在 finally 里 close()。
 */
class SyncGradesUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val parser: JsxsdGradeParser,
    private val detailParser: JsxsdDetailParser,
    private val replacer: ReplaceGradesUseCase,
) {
    fun sync(req: GradesSyncRequest): Flow<SyncGradesStep> = flow {
        try {
            apiClient.open(req.networkMode)

            emit(SyncGradesStep.LoggingIn)
            val login = ssoLogin.invoke(apiClient, req.username, req.password)
            login.toGradesError()?.let { emit(SyncGradesStep.Failed(it)); return@flow }

            // 用 CAS TGC 换取正方(jwms)服务会话；OkHttp 跟随 302→jwms?ticket→落地。
            // CAS 端点按 networkMode 走：校外(WEBVPN)时必须穿 webvpn 网关，否则 ticket
            // 回跳会逃出网关、落到不可达/错域 host，jwms 会话建不起来（详见 activateServiceAt）。
            apiClient.cas.activateServiceAt(apiClient.casLoginEndpoint(), JWMS_SERVICE)

            emit(SyncGradesStep.FetchingGrades)
            val resp = apiClient.jwms.getScoreListHtml()
            val html = (resp.body() ?: resp.errorBody())?.string().orEmpty()
            if (parser.isReviewGated(html)) {
                emit(SyncGradesStep.Failed(GradesSyncError.NeedReview)); return@flow
            }
            val now = System.currentTimeMillis()
            val entries = parser.parse(html, now)
            if (entries.isEmpty()) { emit(SyncGradesStep.Failed(GradesSyncError.EmptyGrades)); return@flow }

            emit(SyncGradesStep.FetchingRanks)   // 并发拉每门课的 cjfx 详情(平均分/排名)
            val enriched: List<GradeEntryEntity> = coroutineScope {
                entries.map { e -> async { enrich(e) } }.awaitAll()
            }
            val ranks = buildTermRanks(enriched, now)

            emit(SyncGradesStep.Persisting)
            replacer.invoke(enriched, ranks)

            val termCount = enriched.map { it.termCode }.distinct().size
            emit(SyncGradesStep.Done(termCount, enriched.size))
        } catch (io: IOException) {
            emit(SyncGradesStep.Failed(GradesSyncError.NetworkFail(io)))
        } catch (e: Throwable) {
            emit(SyncGradesStep.Failed(GradesSyncError.Unexpected(e)))
        } finally {
            apiClient.close()
        }
    }

    /**
     * Auto 模式同步:先按 [req].networkMode(通常取 lastMode)试,若失败原因是**网络不可达**
     * ([GradesSyncError.NetworkFail]),自动换另一种网络模式(校内↔校外)重试一次。其余失败
     * (密码错/评教/空表…)不重试——换网络也没用。成功后通过 [onModeSucceeded] 回告最终生效
     * 的模式,供调用方持久化 lastMode(下次直接走对的,只在失败时再翻)。
     */
    fun syncAuto(
        req: GradesSyncRequest,
        onModeSucceeded: (NetworkMode) -> Unit,
    ): Flow<SyncGradesStep> =
        autoGradesFallback(req.networkMode, onModeSucceeded) { mode ->
            sync(req.copy(networkMode = mode))
        }

    /** 拉一门课的 cjfx 详情并合并平均分/排名。无 detailPath 或任何失败都非致命：
     *  原样返回该条目（不阻断整次同步）。 */
    private suspend fun enrich(e: GradeEntryEntity): GradeEntryEntity {
        val path = e.detailPath ?: return e
        val info = runCatching {
            // detailPath 是 /jsxsd/... 绝对路径；@Url 传以 '/' 开头的路径会被 HttpUrl.resolve
            // 用来替换整段 path,丢掉 WEBVPN base 的 /http/<编码>/ 前缀(校外详情拉不到→"无详情")。
            // 去掉前导 '/' 变相对路径,才会拼到完整 base(含 webvpn 前缀)后面;LOCAL 结果不变。
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

    /** 每学期一条 TermRankEntity（仅 weightedGpa；排名字段 M1 留 null）+ OVERALL 一条。 */
    private fun buildTermRanks(
        entries: List<GradeEntryEntity>, now: Long,
    ): List<TermRankEntity> {
        val rows = entries.groupBy { it.termCode }.map { (code, list) ->
            TermRankEntity(
                termCode = code, termName = list.first().termName,
                weightedGpa = GpaCalculator.weightedGpa(list.map { it.credit to it.gradePoint }),
                classRank = null, classTotal = null, majorRank = null, majorTotal = null,
                fetchedAt = now,
            )
        }
        val overallGpa = GpaCalculator.weightedGpa(entries.map { it.credit to it.gradePoint })
        return rows + TermRankEntity("OVERALL", "总计", overallGpa, null, null, null, null, now)
    }

    /** 后台静默同步。假设 apiClient 已 open;不发 Flow,直接返回结果。
     *  不并发拉 cjfx 详情(Worker 拿到 newEntries 后只对新增条目增量拉)。 */
    suspend fun syncForBackground(req: GradesSyncRequest): com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult {
        val login = ssoLogin.invoke(apiClient, req.username, req.password)
        login.toGradesError()?.let { return com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop(it) }
        return try {
            apiClient.cas.activateServiceAt(apiClient.casLoginEndpoint(), JWMS_SERVICE)
            val resp = apiClient.jwms.getScoreListHtml()
            val html = (resp.body() ?: resp.errorBody())?.string().orEmpty()
            if (parser.isReviewGated(html)) {
                return com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop(GradesSyncError.NeedReview)
            }
            val now = System.currentTimeMillis()
            val entries = parser.parse(html, now)
            com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Ok(entries)
        } catch (io: java.io.IOException) {
            com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Transient
        } catch (e: Throwable) {
            com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Transient
        }
    }

    private fun CasLoginDto.toGradesError(): GradesSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> GradesSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> GradesSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> GradesSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> GradesSyncError.ParseFail("CAS: $body")
    }

    companion object {
        /** 作为 CAS `?service=` 参数传入（Retrofit 按 @Query 编码）。无论校内/校外，
         *  service 都是裸的 jwms 根地址（http + 末尾斜杠，须与 CAS 注册值逐字符一致）；
         *  校内/校外的差异在于打哪个 CAS 端点，见 [BitApiClient.casLoginEndpoint]。 */
        const val JWMS_SERVICE = "http://jwms.bit.edu.cn/"
    }
}

/**
 * 纯编排:首选 [first] 模式,经 [attempt] 跑一次同步;若以 NetworkFail 收尾且还有另一种模式,
 * 抑制那个失败、emit [SyncGradesStep.SwitchingMode] 再换模式重试。Done 即停并回告 [onModeSucceeded];
 * 非网络失败也停(换网络无用)。不依赖 BitApiClient,纯函数式,便于单测。
 *
 * **有意取舍**:回退**只在连接级失败**([GradesSyncError.NetworkFail],即 sync() 捕到的
 * IOException——UnknownHost/Connect/Timeout)触发。校外用校内 host 的主流失败正是连接级,能正确
 * 回退。而 EmptyGrades/ParseFail/Unexpected **不**触发回退:它们可能是真实状态(无成绩/评教未完成/
 * 页面结构变动),盲目翻网会掩盖真因。代价是校外极少数"伪可达"(captive portal 返 200 垃圾页)不
 * 自动换网,用户可手动切。见 AutoGradesFallbackTest 里固化此边界的用例。
 */
internal fun autoGradesFallback(
    first: NetworkMode,
    onModeSucceeded: (NetworkMode) -> Unit,
    attempt: (NetworkMode) -> Flow<SyncGradesStep>,
): Flow<SyncGradesStep> = flow {
    val modes = listOf(first, otherMode(first))
    for ((idx, mode) in modes.withIndex()) {
        val hasFallbackLeft = idx < modes.lastIndex
        var terminal: SyncGradesStep? = null
        attempt(mode).collect { step ->
            terminal = step
            // 首选模式的网络失败先憋住(后面会换模式重试),避免 UI 闪一下"失败"再成功。
            val suppress = step is SyncGradesStep.Failed &&
                step.err is GradesSyncError.NetworkFail && hasFallbackLeft
            if (!suppress) emit(step)
        }
        when (val t = terminal) {
            is SyncGradesStep.Done -> { onModeSucceeded(mode); return@flow }
            is SyncGradesStep.Failed ->
                if (t.err is GradesSyncError.NetworkFail && hasFallbackLeft) {
                    emit(SyncGradesStep.SwitchingMode(otherMode(mode)))
                } else return@flow
            else -> return@flow
        }
    }
}

private fun otherMode(m: NetworkMode): NetworkMode =
    if (m == NetworkMode.LOCAL) NetworkMode.WEBVPN else NetworkMode.LOCAL
