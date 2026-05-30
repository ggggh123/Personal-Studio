package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.ExamResponse
import com.example.personal_studio.data.network.bit.dto.ExamRowDto
import com.example.personal_studio.data.network.bit.dto.TermDto
import com.example.personal_studio.data.network.bit.dto.TermListResponse
import com.example.personal_studio.data.network.bit.service.BitJwappService
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncExamsUseCaseTest {
    private val term = "2025-2026-2"
    private fun req() = ExamSyncRequest("u", "p", NetworkMode.LOCAL, true)

    private val termResp = TermListResponse(
        TermListResponse.Datas(
            dqxnxq = TermListResponse.Rows(listOf(TermDto(code = term, display = "x", isCurrent = 1))),
        ),
    )
    private val emptyTermResp = TermListResponse(
        TermListResponse.Datas(dqxnxq = TermListResponse.Rows(emptyList())),
    )
    private val examResp = ExamResponse(
        ExamResponse.Datas(
            ExamResponse.Rows(
                listOf(
                    ExamRowDto(
                        kcm = "100074340[人工智能概论]", kch = "100074340",
                        kssjms = "2026-07-01 10:10-12:10(星期三)", ksrq = "2026-07-01 00:00:00",
                        jasmc = "理教楼203", zwh = "78", zjjsxm = "刘峡壁", xnxqdm = term,
                    ),
                ),
            ),
        ),
    )

    private fun jwapp(
        currentTerm: Response<TermListResponse> = Response.success(termResp),
        examSchedule: BitJwappService.() -> Unit = { coEvery { getExamSchedule(any()) } returns Response.success(examResp) },
    ) = mockk<BitJwappService>(relaxed = true) {
        coEvery { getCurrentTerm() } returns currentTerm
        examSchedule()
    }

    private fun useCase(
        sso: SsoLoginUseCase,
        jwapp: BitJwappService = jwapp(),
        replacer: ReplaceImportedExamUseCase = mockk(relaxed = true),
    ): SyncExamsUseCase {
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.jwapp } returns jwapp
        }
        return SyncExamsUseCase(api, sso, ExamRowMapper(), replacer)
    }

    private fun successSso() = mockk<SsoLoginUseCase>().also {
        coEvery { it.invoke(any(), any(), any()) } returns CasLoginDto.Success
    }

    @Test fun `happy path emits Done and replaces`() = runTest {
        val sso = successSso()
        val steps = useCase(sso).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Done && it.total == 1 })
    }

    @Test fun `wrong password emits Failed WrongCredentials`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        val steps = useCase(sso).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Failed && it.error is ExamSyncError.WrongCredentials })
    }

    // FIX 1: a transient HTTP failure must NOT wipe previously-imported exams.
    @Test fun `exam fetch http failure emits Failed and does not call replacer`() = runTest {
        val sso = successSso()
        val replacer = mockk<ReplaceImportedExamUseCase>(relaxed = true)
        val failResp = mockk<Response<ExamResponse>>(relaxed = true) {
            every { isSuccessful } returns false
            every { code() } returns 403
            every { body() } returns null
        }
        val jwapp = jwapp(examSchedule = { coEvery { getExamSchedule(any()) } returns failResp })
        val steps = useCase(sso, jwapp = jwapp, replacer = replacer).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Failed && it.error is ExamSyncError.ParseFail })
        assertTrue(steps.none { it is ExamSyncStep.Done })
        coVerify(exactly = 0) { replacer.invoke(any(), any()) }
    }

    @Test fun `missing current term emits Failed ParseFail`() = runTest {
        val sso = successSso()
        val jwapp = jwapp(currentTerm = Response.success(emptyTermResp))
        val steps = useCase(sso, jwapp = jwapp).sync(req()).toList()
        assertTrue(
            steps.any {
                it is ExamSyncStep.Failed &&
                    it.error is ExamSyncError.ParseFail &&
                    (it.error as ExamSyncError.ParseFail).message.contains("无当前学期")
            },
        )
    }

    @Test fun `IOException emits Failed NetworkFail`() = runTest {
        val sso = successSso()
        val jwapp = jwapp(examSchedule = { coEvery { getExamSchedule(any()) } throws java.io.IOException("boom") })
        val steps = useCase(sso, jwapp = jwapp).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Failed && it.error is ExamSyncError.NetworkFail })
    }

    // The #1 403 risk: warm-up calls must run in the exact order before cxxsksap.
    @Test fun `happy path warms up apps in order before fetching exams`() = runTest {
        val sso = successSso()
        val jwapp = jwapp()
        useCase(sso, jwapp = jwapp).sync(req()).toList()
        coVerifyOrder {
            jwapp.getAppConfig()
            jwapp.switchLang()
            jwapp.getCurrentTerm()
            jwapp.getExamAppConfig()
            jwapp.switchLangExam()
            jwapp.getExamSchedule(any())
        }
    }

    @Test fun `requestParamStr carries term and order`() = runTest {
        val sso = successSso()
        val jwapp = jwapp()
        useCase(sso, jwapp = jwapp).sync(req()).toList()
        coVerify { jwapp.getExamSchedule(match { it.contains(term) && it.contains("*order") }) }
    }
}
