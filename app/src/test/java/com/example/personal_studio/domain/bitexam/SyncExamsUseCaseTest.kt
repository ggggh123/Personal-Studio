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
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncExamsUseCaseTest {
    private fun req() = ExamSyncRequest("u", "p", NetworkMode.LOCAL, true)

    private val termResp = TermListResponse(
        TermListResponse.Datas(
            dqxnxq = TermListResponse.Rows(listOf(TermDto(code = "2025-2026-2", display = "x", isCurrent = 1))),
        ),
    )
    private val examResp = ExamResponse(
        ExamResponse.Datas(
            ExamResponse.Rows(
                listOf(
                    ExamRowDto(
                        kcm = "100074340[人工智能概论]", kch = "100074340",
                        kssjms = "2026-07-01 10:10-12:10(星期三)", ksrq = "2026-07-01 00:00:00",
                        jasmc = "理教楼203", zwh = "78", zjjsxm = "刘峡壁", xnxqdm = "2025-2026-2",
                    ),
                ),
            ),
        ),
    )

    private fun useCase(
        sso: SsoLoginUseCase,
        replacer: ReplaceImportedExamUseCase = mockk(relaxed = true),
    ): SyncExamsUseCase {
        val jwapp = mockk<BitJwappService>(relaxed = true) {
            coEvery { getCurrentTerm() } returns Response.success(termResp)
            coEvery { getExamSchedule(any()) } returns Response.success(examResp)
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.jwapp } returns jwapp
        }
        return SyncExamsUseCase(api, sso, ExamRowMapper(), replacer)
    }

    @Test fun `happy path emits Done and replaces`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.Success
        val steps = useCase(sso).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Done && it.total == 1 })
    }

    @Test fun `wrong password emits Failed WrongCredentials`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        val steps = useCase(sso).sync(req()).toList()
        assertTrue(steps.any { it is ExamSyncStep.Failed && it.error is ExamSyncError.WrongCredentials })
    }
}
