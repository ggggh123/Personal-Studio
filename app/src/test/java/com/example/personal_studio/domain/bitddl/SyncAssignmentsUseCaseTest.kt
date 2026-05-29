package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.service.BitLexueService
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitddl.model.LexueUrlResult
import io.mockk.coEvery
import io.mockk.mockk
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

    @Test fun `uses persisted url and returns Ok`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("URL") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val prefs = mockk<DdlSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns DdlSyncState(true, 12, 0L, emptySet(), "URL")
        }
        val gen = mockk<GenerateLexueIcalUrlUseCase>(relaxed = true)
        val r = SyncAssignmentsUseCase(api, LexueIcalParser(), gen, prefs, mockk(relaxed = true)).syncForBackground(req())
        assertTrue(r is BackgroundDdlResult.Ok)
        assertEquals(listOf("a"), (r as BackgroundDdlResult.Ok).events.map { it.uid })
    }

    @Test fun `derives url when none persisted`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIcs("DERIVED") } returns resp(ics) }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val prefs = mockk<DdlSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns DdlSyncState(true, 12, 0L, emptySet(), null)
        }
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Ok("DERIVED")
        val r = SyncAssignmentsUseCase(api, LexueIcalParser(), gen, prefs, mockk(relaxed = true)).syncForBackground(req())
        assertTrue(r is BackgroundDdlResult.Ok)
    }

    @Test fun `derive failure with wrong password returns Stop`() = runTest {
        val api = mockk<BitApiClient>(relaxed = true)
        val prefs = mockk<DdlSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns DdlSyncState(true, 12, 0L, emptySet(), null)
        }
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Failed(DdlSyncError.WrongCredentials)
        val r = SyncAssignmentsUseCase(api, LexueIcalParser(), gen, prefs, mockk(relaxed = true)).syncForBackground(req())
        assertTrue(r is BackgroundDdlResult.Stop)
        assertEquals(DdlSyncError.WrongCredentials, (r as BackgroundDdlResult.Stop).reason)
    }

    @Test fun `stale url re-derives once then Ok`() = runTest {
        val lexue = mockk<BitLexueService> {
            coEvery { getIcs("OLD") } returns resp("<html>login</html>")  // 非日历
            coEvery { getIcs("NEW") } returns resp(ics)
        }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.lexue } returns lexue }
        val prefs = mockk<DdlSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns DdlSyncState(true, 12, 0L, emptySet(), "OLD")
        }
        val gen = mockk<GenerateLexueIcalUrlUseCase>()
        coEvery { gen.invoke(any()) } returns LexueUrlResult.Ok("NEW")
        val r = SyncAssignmentsUseCase(api, LexueIcalParser(), gen, prefs, mockk(relaxed = true)).syncForBackground(req())
        assertTrue(r is BackgroundDdlResult.Ok)
    }
}
