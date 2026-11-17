package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.EclassCourseDto
import com.example.personal_studio.data.network.bit.dto.EclassHomeworkDto
import com.example.personal_studio.data.network.bit.dto.EclassSemesterDto
import com.example.personal_studio.data.network.bit.dto.EclassSemestersDto
import com.example.personal_studio.data.network.bit.service.BitEclassService
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** Response bodies follow the shapes zy-eclass.bit.edu.cn returns; ids and titles are made up. */
class FetchEclassAssignmentsUseCaseTest {
    private fun json(body: String) = Response.success(body.toResponseBody("application/json".toMediaType()))
    private fun html() = Response.success("<html><div id=\"login-page-flowkey\">x</div></html>".toResponseBody("text/html".toMediaType()))
    private fun req() = DdlSyncRequest("u", "p", NetworkMode.LOCAL, true)

    private val semesters = """{"semesters":[
        {"academic_year_id":4,"id":10,"is_active":true,"name":"2026-20271季学期","real_name":"1季学期","sort":6},
        {"academic_year_id":3,"id":6,"is_active":false,"name":"2025-20262季学期","real_name":"2季学期","sort":5}]}"""
    private val courses = """{"courses":[{"id":301,"name":"计算机视觉"},{"id":302,"name":"操作系统"}],
        "page":1,"page_size":100,"pages":1,"total":2}"""
    private val visionPage1 = """{"homework_activities":[
        {"id":11,"title":"特征点匹配","end_time":"2026-11-24T15:55:00Z",
         "deadline":"2026-11-24T15:55:00Z","submitted":true,"published":true,"is_closed":false,
         "data":{"description":"<p>提交&nbsp;代码<br/>和报告</p>","mode":"normal"}},
        {"id":12,"title":" 大作业 ","end_time":"2027-01-03T15:55:00Z",
         "submitted":false,"published":true,"data":{"description":""}}],"page":1,"page_size":100,"pages":2,"total":3}"""
    private val visionPage2 = """{"homework_activities":[
        {"id":13,"title":"草稿","end_time":"2026-12-01T00:00:00Z","submitted":false,"published":false,"data":null}],
        "page":2,"page_size":100,"pages":2,"total":3}"""
    private val osEmpty = """{"homework_activities":[],"page":1,"page_size":100,"pages":1,"total":0}"""

    private fun signedIn(service: BitEclassService = mockk()) = service.apply {
        coEvery { semesters() } returns json(semesters)
        coEvery { courses(any()) } returns json(courses)
        coEvery { homework(301, 1, 100) } returns json(visionPage1)
        coEvery { homework(301, 2, 100) } returns json(visionPage2)
        coEvery { homework(302, 1, 100) } returns json(osEmpty)
    }

    private fun api(service: BitEclassService) = mockk<BitApiClient>(relaxed = true) { every { eclass } returns service }

    @Test fun `reads this semester's homework across courses and pages`() = runTest {
        val service = signedIn()
        val query = slot<RequestBody>()
        coEvery { service.courses(capture(query)) } returns json(courses)
        val sso = mockk<SsoLoginUseCase>()
        val r = FetchEclassAssignmentsUseCase(api(service), sso).invoke(req()) as BackgroundDdlResult.Ok
        assertEquals(listOf("eclass-homework-11", "eclass-homework-12"), r.events.map { it.uid })
        val sift = r.events[0]
        assertEquals("特征点匹配", sift.title)
        assertEquals("计算机视觉", sift.course)
        assertEquals(java.time.Instant.parse("2026-11-24T15:55:00Z").toEpochMilli(), sift.dueAt)
        assertEquals("提交 代码\n和报告", sift.description)
        assertEquals(DdlSource.ECLASS, sift.source)
        assertTrue(sift.submitted)
        assertEquals("大作业", r.events[1].title)
        assertEquals(false, r.events[1].submitted)
        // Only the active semester is asked for.
        val sent = Json.parseToJsonElement(Buffer().also { query.captured.writeTo(it) }.readUtf8()) as JsonObject
        assertEquals("[10]", (sent["conditions"] as JsonObject)["semester_id"].toString())
        coVerify(exactly = 0) { sso.loginEclass(any(), any(), any()) }
    }

    @Test fun `a sign-in page means log in once, then read`() = runTest {
        val service = mockk<BitEclassService>()
        var loggedIn = false
        signedIn(service)
        coEvery { service.semesters() } answers { if (loggedIn) json(semesters) else html() }
        val api = api(service)
        val sso = mockk<SsoLoginUseCase> {
            coEvery { loginEclass(api, "u", "p") } answers { loggedIn = true; CasLoginDto.Success }
        }
        val r = FetchEclassAssignmentsUseCase(api, sso).invoke(req())
        assertEquals(2, (r as BackgroundDdlResult.Ok).events.size)
        coVerify(exactly = 1) { sso.loginEclass(api, "u", "p") }
    }

    @Test fun `login problems map onto sync errors`() = runTest {
        val service = mockk<BitEclassService> { coEvery { semesters() } returns html() }
        val api = api(service)
        fun withLogin(result: CasLoginDto) = FetchEclassAssignmentsUseCase(
            api, mockk { coEvery { loginEclass(any(), any(), any()) } returns result },
        )
        assertEquals(BackgroundDdlResult.Stop(DdlSyncError.WrongCredentials), withLogin(CasLoginDto.WrongCredentials).invoke(req()))
        assertEquals(BackgroundDdlResult.Stop(DdlSyncError.CaptchaRequired), withLogin(CasLoginDto.CaptchaRequired).invoke(req()))
        // Signed in by CAS but 新乐学 still shows a sign-in page.
        assertTrue((withLogin(CasLoginDto.Success).invoke(req()) as BackgroundDdlResult.Stop).reason is DdlSyncError.ParseFail)
    }

    @Test fun `network trouble is transient, a changed format is a stop`() = runTest {
        val down = mockk<BitEclassService> { coEvery { semesters() } throws IOException("reset") }
        assertEquals(BackgroundDdlResult.Transient, FetchEclassAssignmentsUseCase(api(down), mockk()).invoke(req()))
        val changed = mockk<BitEclassService> { coEvery { semesters() } returns json("""{"semesters":"oops"}""") }
        val r = FetchEclassAssignmentsUseCase(api(changed), mockk()).invoke(req())
        assertTrue((r as BackgroundDdlResult.Stop).reason is DdlSyncError.ParseFail)
    }

    @Test fun `current semester falls back to the latest when none is active`() {
        val dto = EclassSemestersDto(listOf(EclassSemesterDto(5, sort = 3), EclassSemesterDto(6, sort = 5), EclassSemesterDto(7, sort = 1)))
        assertEquals(listOf(6L), EclassAssignments.currentSemesters(dto))
        assertEquals(emptyList<Long>(), EclassAssignments.currentSemesters(EclassSemestersDto()))
    }

    @Test fun `homework without a deadline or unpublished is skipped`() {
        val course = EclassCourseDto(1, "数据库")
        val items = listOf(
            EclassHomeworkDto(1, "无截止"),
            EclassHomeworkDto(2, "未发布", endTime = "2026-12-01T00:00:00Z", published = false),
            EclassHomeworkDto(3, "只有 deadline", deadline = "2026-12-02T00:00:00Z",
                data = JsonObject(mapOf("description" to JsonPrimitive("要求&lt;3页&gt;")))),
        )
        val events = EclassAssignments.toEvents(course, items)
        assertEquals(listOf("eclass-homework-3"), events.map { it.uid })
        assertEquals("要求<3页>", events.single().description)
        assertNull(EclassAssignments.parseInstant("not a time"))
    }
}
