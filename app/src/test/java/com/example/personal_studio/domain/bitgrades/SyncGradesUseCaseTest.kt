package com.example.personal_studio.domain.bitgrades

import app.cash.turbine.test
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import com.example.personal_studio.data.network.bit.service.BitJwmsService
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncGradesUseCaseTest {

    private fun req() = GradesSyncRequest("u", "p", NetworkMode.LOCAL, false)

    private fun html(body: String) =
        Response.success(body.toResponseBody("text/html".toMediaType()))

    /** Minimal jsxsd #dataList table with one course. */
    private fun scoreHtml(rows: String) = html(
        """<table id="dataList">
           <tr><th>学年学期</th><th>课程名称</th><th>学分</th><th>成绩</th><th>绩点</th></tr>
           $rows</table>""",
    )

    private fun ssoMock(result: CasLoginDto) =
        mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns result
        }

    @Test fun `wrong password emits Failed and closes session`() = runTest {
        val api = mockk<BitApiClient>(relaxed = true)
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.WrongCredentials), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            val f = awaitItem() as SyncGradesStep.Failed
            assertTrue(f.err is GradesSyncError.WrongCredentials)
            awaitComplete()
        }
        coVerify(exactly = 1) { api.close() }
    }

    @Test fun `happy path parses jsxsd html, persists and emits Done`() = runTest {
        val cas = mockk<BitCasService>(relaxed = true)
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns scoreHtml(
                "<tr><td>2024-2025-2</td><td>高数</td><td>5.0</td><td>92</td><td>4.0</td></tr>",
            )
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns cas
            coEvery { this@mockk.jwms } returns jwms
        }
        val replacer = mockk<ReplaceGradesUseCase>(relaxed = true)
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), replacer)

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

    @Test fun `cjfx detail fetched concurrently and merged into persisted entry`() = runTest {
        val cas = mockk<BitCasService>(relaxed = true)
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns scoreHtml(
                """<tr><td>2024-2025-2</td><td>高数</td><td>5.0</td><td>92</td>
                   <td><a onclick="JsMod('/jsxsd/kscj/cjfx?kch=100028016&cjfs=C')">成绩分析</a></td></tr>""",
            )
            coEvery { getCourseDetailHtml(any()) } returns html(
                """<table><tr><td>平均分：88</td><td>本人成绩在班级中占：前10%</td></tr></table>""",
            )
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns cas
            coEvery { this@mockk.jwms } returns jwms
        }
        val replacer = mockk<ReplaceGradesUseCase>(relaxed = true)
        val captured = slot<List<GradeEntryEntity>>()
        coEvery { replacer.invoke(capture(captured), any<List<TermRankEntity>>()) } returns Unit
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), replacer)

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue(awaitItem() is SyncGradesStep.FetchingRanks)
            assertTrue(awaitItem() is SyncGradesStep.Persisting)
            assertTrue(awaitItem() is SyncGradesStep.Done)
            awaitComplete()
        }
        coVerify(exactly = 1) { jwms.getCourseDetailHtml(any()) }
        val entry = captured.captured.single()
        assertEquals(88.0, entry.courseAvg!!, 0.001)
        assertEquals("前10%", entry.classRankText)
    }

    @Test fun `empty table emits Failed-EmptyGrades`() = runTest {
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns html("<html><body>no table here</body></html>")
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk(relaxed = true)
            coEvery { this@mockk.jwms } returns jwms
        }
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue((awaitItem() as SyncGradesStep.Failed).err is GradesSyncError.EmptyGrades)
            awaitComplete()
        }
    }

    @Test fun `review-gated page emits Failed-NeedReview`() = runTest {
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns html("<html><body>请先完成评教</body></html>")
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk(relaxed = true)
            coEvery { this@mockk.jwms } returns jwms
        }
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue((awaitItem() as SyncGradesStep.Failed).err is GradesSyncError.NeedReview)
            awaitComplete()
        }
    }

    // —— syncForBackground —— //

    @Test fun `syncForBackground wrong password returns Stop with WrongCredentials`() = runTest {
        val api = mockk<BitApiClient>(relaxed = true)
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.WrongCredentials), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))
        val r = useCase.syncForBackground(req())
        assertTrue(r is com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop)
        assertTrue((r as com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop).reason is GradesSyncError.WrongCredentials)
    }

    @Test fun `syncForBackground happy path returns Ok with entries`() = runTest {
        val cas = mockk<BitCasService>(relaxed = true)
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns scoreHtml(
                "<tr><td>2024-2025-2</td><td>高数</td><td>5.0</td><td>92</td><td>4.0</td></tr>",
            )
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns cas
            coEvery { this@mockk.jwms } returns jwms
        }
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))
        val r = useCase.syncForBackground(req())
        assertTrue(r is com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Ok)
        assertEquals(1, (r as com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Ok).entries.size)
    }

    @Test fun `syncForBackground review-gated returns Stop NeedReview`() = runTest {
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns html("<html><body>请先完成评教</body></html>")
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk(relaxed = true)
            coEvery { this@mockk.jwms } returns jwms
        }
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))
        val r = useCase.syncForBackground(req())
        assertTrue(r is com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop)
        assertTrue((r as com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop).reason is GradesSyncError.NeedReview)
    }
}
