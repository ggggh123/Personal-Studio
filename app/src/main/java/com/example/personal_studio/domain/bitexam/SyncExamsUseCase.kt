package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.autoNetworkFallback
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
            val examResp = apiClient.jwapp.getExamSchedule(reqStr)
            val examBody = examResp.body()
            if (!examResp.isSuccessful || examBody == null) {
                emit(ExamSyncStep.Failed(ExamSyncError.ParseFail("考试查询失败 HTTP ${examResp.code()}")))
                return@flow
            }
            val rows = examBody.datas.cxxsksap.rows
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

    /** Auto 模式:先按 [req].networkMode 试,连接级失败(NetworkFail)自动换另一网络模式重试一次,
     *  成功后回告生效模式。其余失败不回退。 */
    fun syncAuto(req: ExamSyncRequest, onModeSucceeded: (NetworkMode) -> Unit): Flow<ExamSyncStep> =
        autoNetworkFallback(
            first = req.networkMode,
            isConnFail = { it is ExamSyncStep.Failed && it.error is ExamSyncError.NetworkFail },
            isDone = { it is ExamSyncStep.Done },
            switchingStep = { ExamSyncStep.SwitchingMode(it) },
            onModeSucceeded = onModeSucceeded,
        ) { mode -> sync(req.copy(networkMode = mode)) }

    private fun CasLoginDto.toExamError(): ExamSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> ExamSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> ExamSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> ExamSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> ExamSyncError.ParseFail("CAS: $body")
    }
}
