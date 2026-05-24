package com.example.personal_studio.domain.bitgrades

import app.cash.turbine.test
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.GradeListResponse
import com.example.personal_studio.data.network.bit.dto.GradeRankResponse
import com.example.personal_studio.data.network.bit.dto.GradeRowDto
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncGradesUseCaseTest {

    private fun req() = GradesSyncRequest("u", "p", NetworkMode.LOCAL, false)

    @Test fun `wrong password emits Failed and closes session`() = runTest {
        val sso = mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        }
        val api = mockk<BitApiClient>(relaxed = true)
        val useCase = SyncGradesUseCase(api, sso, MapGradeUseCase(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            val f = awaitItem() as SyncGradesStep.Failed
            assertTrue(f.err is GradesSyncError.WrongCredentials)
            awaitComplete()
        }
        coVerify(exactly = 1) { api.close() }
    }

    @Test fun `happy path persists and emits Done with rank degraded gracefully`() = runTest {
        val sso = mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.Success
        }
        val grades = Response.success(GradeListResponse(GradeListResponse.Datas(
            cxstuxqcj = GradeListResponse.Rows(rows = listOf(
                GradeRowDto(termCode = "2024-2025-2", termName = "24春", courseName = "高数",
                    courseCode = "M1", credit = 5.0, score = "92", gradePoint = 4.0),
            )))))
        val cjcx = mockk<com.example.personal_studio.data.network.bit.service.BitCjcxService>(relaxed = true) {
            coEvery { getGrades(any(), any(), any(), any()) } returns grades
            coEvery { getRankDetail(any()) } returns Response.error(500,
                okhttp3.ResponseBody.Companion.create(null, ""))
        }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.cjcx } returns cjcx }
        val replacer = mockk<ReplaceGradesUseCase>(relaxed = true)
        val useCase = SyncGradesUseCase(api, sso, MapGradeUseCase(), replacer)

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue(awaitItem() is SyncGradesStep.FetchingRanks)
            assertTrue(awaitItem() is SyncGradesStep.Persisting)
            val done = awaitItem() as SyncGradesStep.Done
            assertTrue(done.courseCount == 1)
            awaitComplete()
        }
        coVerify(exactly = 1) { replacer.invoke(any(), any()) }
        coVerify(exactly = 1) { api.close() }
    }

    @Test fun `empty grades emits Failed-EmptyGrades`() = runTest {
        val sso = mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.Success
        }
        val empty = Response.success(GradeListResponse(GradeListResponse.Datas(
            cxstuxqcj = GradeListResponse.Rows(rows = emptyList()))))
        val cjcx = mockk<com.example.personal_studio.data.network.bit.service.BitCjcxService>(relaxed = true) {
            coEvery { getGrades(any(), any(), any(), any()) } returns empty
        }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.cjcx } returns cjcx }
        val useCase = SyncGradesUseCase(api, sso, MapGradeUseCase(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue((awaitItem() as SyncGradesStep.Failed).err is GradesSyncError.EmptyGrades)
            awaitComplete()
        }
    }
}
