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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 成绩同步编排。复用 P5 的 SsoLoginUseCase + BitApiClient。两步拉取：
 * 成绩列表(致命失败)→ 每学期排名详情(非致命，失败则 rank 留 null)。始终 close()。
 */
class SyncGradesUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val mapper: MapGradeUseCase,
    private val replacer: ReplaceGradesUseCase,
) {
    fun sync(req: GradesSyncRequest): Flow<SyncGradesStep> = flow {
        try {
            apiClient.open(req.networkMode)

            emit(SyncGradesStep.LoggingIn)
            val login = ssoLogin.invoke(apiClient, req.username, req.password)
            login.toGradesError()?.let { emit(SyncGradesStep.Failed(it)); return@flow }

            // cjcx warm-up（每个 ehall app 各需一次，否则 403）
            apiClient.cjcx.getIndex()
            apiClient.cjcx.getAppConfig()
            apiClient.cjcx.switchLang()

            emit(SyncGradesStep.FetchingGrades)
            val gradeResp = apiClient.cjcx.getGrades()
            val rows = gradeResp.body()?.datas?.cxstuxqcj?.rows ?: emptyList()
            if (rows.isEmpty()) { emit(SyncGradesStep.Failed(GradesSyncError.EmptyGrades)); return@flow }
            val now = System.currentTimeMillis()
            val entries = rows.mapNotNull { mapper.invoke(it, now) }
            if (entries.isEmpty()) { emit(SyncGradesStep.Failed(GradesSyncError.EmptyGrades)); return@flow }

            emit(SyncGradesStep.FetchingRanks)
            val ranks = buildTermRanks(entries, now)

            emit(SyncGradesStep.Persisting)
            replacer.invoke(entries, ranks)

            val termCount = entries.map { it.termCode }.distinct().size
            emit(SyncGradesStep.Done(termCount, entries.size))
        } catch (io: IOException) {
            emit(SyncGradesStep.Failed(GradesSyncError.NetworkFail(io)))
        } catch (e: Throwable) {
            emit(SyncGradesStep.Failed(GradesSyncError.Unexpected(e)))
        } finally {
            apiClient.close()
        }
    }

    /** 每学期一条 TermRankEntity（weightedGpa 必有；排名 best-effort）+ OVERALL 一条。 */
    private suspend fun buildTermRanks(
        entries: List<GradeEntryEntity>, now: Long,
    ): List<TermRankEntity> {
        val byTerm = entries.groupBy { it.termCode }
        val rows = byTerm.map { (code, list) ->
            val gpa = GpaCalculator.weightedGpa(list.map { it.credit to it.gradePoint })
            val detail = runCatching {
                apiClient.cjcx.getRankDetail("""{"XNXQDM":"$code"}""")
                    .takeIf { it.isSuccessful }?.body()?.datas?.cxstupm?.rows?.firstOrNull()
            }.getOrNull()
            TermRankEntity(
                termCode = code, termName = list.first().termName, weightedGpa = gpa,
                classRank = detail?.classRank, classTotal = detail?.classTotal,
                majorRank = detail?.majorRank, majorTotal = detail?.majorTotal, fetchedAt = now,
            )
        }
        val overallGpa = GpaCalculator.weightedGpa(entries.map { it.credit to it.gradePoint })
        val overall = TermRankEntity("OVERALL", "总计", overallGpa, null, null, null, null, now)
        return rows + overall
    }

    private fun CasLoginDto.toGradesError(): GradesSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> GradesSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> GradesSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> GradesSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> GradesSyncError.ParseFail("CAS: $body")
    }
}
