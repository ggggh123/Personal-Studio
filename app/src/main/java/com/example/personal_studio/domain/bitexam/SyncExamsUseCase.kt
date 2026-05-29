package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 考试安排同步:ehall studentWdksapApp(jwapp,同 host 同会话,复用 P5 课表基建)。
 * ssoLogin → warm-up wdkbby 取当前学期 → warm-up studentWdksapApp → cxxsksap → 映射 → 灌 Timeline。
 * 手动触发,无后台轮询。
 */
class SyncExamsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val mapper: ExamRowMapper,
    private val replacer: ReplaceImportedExamUseCase,
) {
    fun sync(req: ExamSyncRequest): Flow<ExamSyncStep> = flow {
        try {
            apiClient.open(req.networkMode)
            emit(ExamSyncStep.LoggingIn)
            val login = ssoLogin.invoke(apiClient, req.username, req.password)
            login.toExamError()?.let { emit(ExamSyncStep.Failed(it)); return@flow }

            // warm-up wdkbby 取当前学年学期(与 app 无关,全校统一)
            apiClient.jwapp.getAppConfig()
            apiClient.jwapp.switchLang()
            val term = apiClient.jwapp.getCurrentTerm().body()?.datas?.dqxnxq?.rows?.firstOrNull()?.code
                ?: run { emit(ExamSyncStep.Failed(ExamSyncError.ParseFail("无当前学期"))); return@flow }

            emit(ExamSyncStep.FetchingExams)
            // warm-up studentWdksapApp(否则 cxxsksap 403)
            apiClient.jwapp.getExamAppConfig()
            apiClient.jwapp.switchLangExam()
            val reqStr = """{"XNXQDM":"$term","*order":"-KSRQ,-KSSJMS"}"""
            val rows = apiClient.jwapp.getExamSchedule(reqStr).body()?.datas?.cxxsksap?.rows ?: emptyList()
            val exams = rows.mapNotNull { mapper.invoke(it, term) }
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
