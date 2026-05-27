package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.GpaCalculator
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import com.example.personal_studio.data.network.bit.BitApiClient
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
            apiClient.cas.activateService(JWMS_SERVICE)

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

    /** 拉一门课的 cjfx 详情并合并平均分/排名。无 detailPath 或任何失败都非致命：
     *  原样返回该条目（不阻断整次同步）。 */
    private suspend fun enrich(e: GradeEntryEntity): GradeEntryEntity {
        val path = e.detailPath ?: return e
        val info = runCatching {
            val r = apiClient.jwms.getCourseDetailHtml(path)
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

    private fun CasLoginDto.toGradesError(): GradesSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> GradesSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> GradesSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> GradesSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> GradesSyncError.ParseFail("CAS: $body")
    }

    companion object {
        /** 必须与 CAS 注册的 jwms service 完全一致（http + 末尾斜杠）。 */
        const val JWMS_SERVICE = "http://jwms.bit.edu.cn/"
    }
}
