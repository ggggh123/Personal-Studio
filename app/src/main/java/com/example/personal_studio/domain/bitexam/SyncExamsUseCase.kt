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
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

/**
 * 考试安排同步:ehall wdksapMobile(与课表共用 host 和认证会话)。
 * ssoLogin → warm-up wdkbby 取当前学期 → 初始化 wdksapMobile → cxxsksap → 映射 → 灌 Timeline。
 * 手动触发,无后台轮询。
 */
class SyncExamsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val mapper: ExamRowMapper,
    private val replacer: ReplaceImportedExamUseCase,
) {
    fun sync(req: ExamSyncRequest): Flow<ExamSyncStep> = flow {
        apiClient.acquireSession()
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
            val index = apiClient.jwapp.getExamIndex()
            if (!index.isSuccessful) {
                emit(ExamSyncStep.Failed(ExamSyncError.ParseFail("考试应用初始化失败 HTTP ${index.code()}")))
                return@flow
            }
            index.body()?.close()
            val examResp = apiClient.jwapp.getExamSchedule(term)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(ExamSyncStep.Failed(ExamSyncError.Unexpected(e.message ?: e.javaClass.simpleName)))
        } finally {
            try { apiClient.close() } finally { apiClient.releaseSession() }
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
