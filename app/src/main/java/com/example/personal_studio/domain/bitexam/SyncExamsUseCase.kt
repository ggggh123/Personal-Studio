package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 考试安排同步:open→sso→activateService(jwms)→GET xsksap_query 抠学期→POST xsksap_list→解析→灌 Timeline。
 * 复用 grades 的 jwms 会话(同 host)。手动触发,无后台轮询。
 */
class SyncExamsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val parser: JsxsdExamParser,
    private val replacer: ReplaceImportedExamUseCase,
) {
    fun sync(req: ExamSyncRequest): Flow<ExamSyncStep> = flow {
        try {
            apiClient.open(req.networkMode)
            emit(ExamSyncStep.LoggingIn)
            val login = ssoLogin.invoke(apiClient, req.username, req.password)
            login.toExamError()?.let { emit(ExamSyncStep.Failed(it)); return@flow }

            apiClient.cas.activateService(SyncGradesUseCase.JWMS_SERVICE)

            emit(ExamSyncStep.FetchingExams)
            val queryHtml = (apiClient.jwms.getExamQueryHtml().let { it.body() ?: it.errorBody() })?.string().orEmpty()
            val term = JsxsdExamParser.extractCurrentTerm(queryHtml)
                ?: run { emit(ExamSyncStep.Failed(ExamSyncError.ParseFail("无学期"))); return@flow }
            val listHtml = (apiClient.jwms.getExamScheduleHtml(term).let { it.body() ?: it.errorBody() })?.string().orEmpty()
            val exams = parser.parse(listHtml, term)
            replacer.invoke(exams)
            emit(ExamSyncStep.Done(exams.size))
        } catch (io: IOException) {
            emit(ExamSyncStep.Failed(ExamSyncError.NetworkFail(io.message ?: "io")))
        } catch (e: Throwable) {
            emit(ExamSyncStep.Failed(ExamSyncError.Unexpected(e.message ?: e.javaClass.simpleName)))
        } finally {
            apiClient.close()
        }
    }

    private fun CasLoginDto.toExamError(): ExamSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> ExamSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> ExamSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> ExamSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> ExamSyncError.ParseFail("CAS: $body")
    }
}
