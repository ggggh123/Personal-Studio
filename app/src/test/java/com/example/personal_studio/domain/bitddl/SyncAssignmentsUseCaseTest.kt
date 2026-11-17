package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.service.BitLexueService
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitddl.model.DdlSyncStep
import com.example.personal_studio.domain.bitddl.model.LexueUrlResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncAssignmentsUseCaseTest {
    private val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:a\r\nSUMMARY:t\r\nDTSTART:20260101T000000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
    private fun resp(body: String) = Response.success(body.toResponseBody("text/calendar".toMediaType()))
    private fun req() = DdlSyncRequest("u", "p", NetworkMode.LOCAL, true)
    private val homework = DdlEvent("eclass-homework-1", "SIFT", "", "计算机视觉", 5L, DdlSource.ECLASS, submitted = true)

    private fun prefs(url: String?) = mockk<DdlSyncPrefs>(relaxed = true) {
        coEvery { snapshot() } returns DdlSyncState(true, 12, 0L, emptySet(), url)
    }
    private fun eclass(result: BackgroundDdlResult) = mockk<FetchEclassAssignmentsUseCase> {
        coEvery { this@mockk.invoke(any()) } returns result
    }
    private fun useCase(
        api: BitApiClient,
        prefs: DdlSyncPrefs,
        gen: GenerateLexueIcalUrlUseCase = mockk(relaxed = true),
        replacer: ReplaceImportedDdlUseCase = mockk(relaxed = true),
        eclass: FetchEclassAssignmentsUseCase = eclass(BackgroundDdlResult.Ok(emptyList())),
    ) = SyncAssignmentsUseCase(api, LexueIcalParser(), gen, prefs, replacer, eclass, DetectNewDdlUseCase(prefs))

    @Test fun `uses persisted url and returns Ok`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("URL") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val r = useCase(api, prefs("URL")).syncForBackground(req()).bySource[DdlSource.LEXUE]
        assertTrue(r is BackgroundDdlResult.Ok)
        assertEquals(listOf("a"), (r as BackgroundDdlResult.Ok).events.map { it.uid })
    }

    @Test fun `derives url when none persisted`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("DERIVED") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Ok("DERIVED")
        val r = useCase(api, prefs(null), gen).syncForBackground(req()).bySource[DdlSource.LEXUE]
        assertTrue(r is BackgroundDdlResult.Ok)
    }

    @Test fun `derive failure with wrong password returns Stop`() = runTest {
        val api = mockk<BitApiClient>(relaxed = true)
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Failed(DdlSyncError.WrongCredentials)
        val outcome = useCase(api, prefs(null), gen).syncForBackground(req())
        val r = outcome.bySource[DdlSource.LEXUE]
        assertTrue(r is BackgroundDdlResult.Stop)
        assertEquals(DdlSyncError.WrongCredentials, (r as BackgroundDdlResult.Stop).reason)
        assertEquals(DdlSyncError.WrongCredentials, outcome.accountStop)
    }

    @Test fun `stale url re-derives once then Ok`() = runTest {
        val lexue = mockk<BitLexueService> {
            coEvery { getIcs("OLD") } returns resp("<html>login</html>")  // 非日历
            coEvery { getIcs("NEW") } returns resp(ics)
        }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Ok("NEW")
        val r = useCase(api, prefs("OLD"), gen).syncForBackground(req()).bySource[DdlSource.LEXUE]
        assertTrue(r is BackgroundDdlResult.Ok)
    }

    @Test fun `background fetch carries both platforms, and a crash in one stays that one's`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("URL") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val broken = mockk<FetchEclassAssignmentsUseCase> { coEvery { this@mockk.invoke(any()) } throws IllegalStateException("boom") }
        val outcome = useCase(api, prefs("URL"), eclass = broken).syncForBackground(req())
        assertEquals(setOf(DdlSource.LEXUE), outcome.succeeded)
        assertEquals(BackgroundDdlResult.Transient, outcome.bySource[DdlSource.ECLASS])
    }

    @Test fun `manual sync stores each platform and reports both counts`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("URL") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val replacer = mockk<ReplaceImportedDdlUseCase>(relaxed = true)
        val prefs = prefs("URL")
        val steps = useCase(api, prefs, replacer = replacer, eclass = eclass(BackgroundDdlResult.Ok(listOf(homework))))
            .sync(req()).toList()
        assertEquals(DdlSyncStep.FetchingCalendar, steps[0])
        assertEquals(DdlSyncStep.FetchingEclass, steps[1])
        val done = steps.last() as DdlSyncStep.Done
        assertEquals(2, done.total)
        assertEquals(mapOf(DdlSource.LEXUE to 1, DdlSource.ECLASS to 1), done.counts)
        assertTrue(done.failed.isEmpty())
        coVerify { replacer.invoke(DdlSource.LEXUE, match { it.single().uid == "a" }, any()) }
        coVerify { replacer.invoke(DdlSource.ECLASS, listOf(homework), any()) }
        coVerify { prefs.setLastSeenUids(setOf("a", "eclass-homework-1")) }
        coVerify { prefs.setBaselinedSources(setOf("LEXUE", "ECLASS")) }
    }

    @Test fun `manual sync keeps 乐学 when 新乐学 fails, and says so`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("URL") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val replacer = mockk<ReplaceImportedDdlUseCase>(relaxed = true)
        val steps = useCase(
            api, prefs("URL"), replacer = replacer,
            eclass = eclass(BackgroundDdlResult.Stop(DdlSyncError.CaptchaRequired)),
        ).sync(req()).toList()
        val done = steps.last() as DdlSyncStep.Done
        assertEquals(mapOf(DdlSource.LEXUE to 1), done.counts)
        assertEquals(mapOf(DdlSource.ECLASS to DdlSyncError.CaptchaRequired), done.failed)
        coVerify(exactly = 0) { replacer.invoke(DdlSource.ECLASS, any(), any()) }
    }

    @Test fun `manual sync fails only when both platforms fail, account problems first`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("URL") } returns resp("<html>login</html>") }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Failed(DdlSyncError.ParseFail("no sesskey"))
        val steps = useCase(
            api, prefs("URL"), gen,
            eclass = eclass(BackgroundDdlResult.Stop(DdlSyncError.AccountLocked)),
        ).sync(req()).toList()
        assertEquals(DdlSyncStep.Failed(DdlSyncError.AccountLocked), steps.last())
    }
}
