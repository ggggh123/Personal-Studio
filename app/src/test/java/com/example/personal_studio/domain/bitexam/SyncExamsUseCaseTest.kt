package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import com.example.personal_studio.data.network.bit.service.BitJwmsService
import com.example.personal_studio.domain.bitexam.model.ExamSyncError
import com.example.personal_studio.domain.bitexam.model.ExamSyncRequest
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncExamsUseCaseTest {
    private fun resp(body: String) = Response.success(body.toResponseBody("text/html".toMediaType()))
    private fun req() = ExamSyncRequest("u", "p", NetworkMode.LOCAL, true)

    private val queryHtml = """<select id="xnxqid"><option value="2025-2026-1" selected>x</option></select>"""
    private val listHtml = """<table id="dataList"><tr><th>课程名称</th><th>考试时间</th><th>考点</th><th>座位号</th></tr>
        <tr><td>高数</td><td>2026-01-05 08:00~10:00</td><td>中教401</td><td>23</td></tr></table>"""

    private fun useCase(
        sso: SsoLoginUseCase,
        replacer: ReplaceImportedExamUseCase = mockk(relaxed = true),
    ): SyncExamsUseCase {
        val jwms = mockk<BitJwmsService> {
            coEvery { getExamQueryHtml() } returns resp(queryHtml)
            coEvery { getExamScheduleHtml(any()) } returns resp(listHtml)
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk<BitCasService>(relaxed = true)
            coEvery { this@mockk.jwms } returns jwms
        }
        return SyncExamsUseCase(api, sso, JsxsdExamParser(), replacer)
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
