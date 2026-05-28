package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.service.BitCasService
import com.example.personal_studio.data.network.bit.service.BitLexueService
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitddl.model.LexueUrlResult
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class GenerateLexueIcalUrlUseCaseTest {

    private fun ok(body: String) = Response.success(body.toResponseBody("text/html".toMediaType()))
    private fun req() = DdlSyncRequest("u", "p", NetworkMode.LOCAL, true)

    /** SsoLoginUseCase.invoke is an `operator fun`; stub it via an explicit
     *  receiver so the call doesn't collide with MockK's matcher-scope DSL. */
    private fun ssoReturning(result: CasLoginDto): SsoLoginUseCase {
        val sso = mockk<SsoLoginUseCase>()
        coEvery { sso.invoke(any(), any(), any()) } returns result
        return sso
    }

    @Test fun `extractSesskey pulls value from moodle config json`() {
        val html = """...,"sesskey":"AbC123xyz",..."""
        assertEquals("AbC123xyz", GenerateLexueIcalUrlUseCase.extractSesskey(html))
    }

    @Test fun `extractIcalUrl pulls export_execute url and unescapes amp`() {
        val html = """<div class="calendarurl">https://lexue.bit.edu.cn/calendar/export_execute.php?userid=1&amp;authtoken=t&amp;preset_what=all</div>"""
        assertEquals(
            "https://lexue.bit.edu.cn/calendar/export_execute.php?userid=1&authtoken=t&preset_what=all",
            GenerateLexueIcalUrlUseCase.extractIcalUrl(html),
        )
    }

    @Test fun `wrong password returns Failed WrongCredentials`() = runTest {
        val sso = ssoReturning(CasLoginDto.WrongCredentials)
        val uc = GenerateLexueIcalUrlUseCase(mockk(relaxed = true), sso)
        val r = uc.invoke(req())
        assertTrue(r is LexueUrlResult.Failed)
        assertEquals(DdlSyncError.WrongCredentials, (r as LexueUrlResult.Failed).error)
    }

    @Test fun `happy path returns Ok url`() = runTest {
        val lexue = mockk<BitLexueService> {
            coEvery { getIndexHtml() } returns ok("""x"sesskey":"KEY1"x""")
            coEvery { exportCalendar(any(), any(), any(), any(), any()) } returns
                ok("""<div class="calendarurl">https://lexue.bit.edu.cn/calendar/export_execute.php?authtoken=T</div>""")
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk<BitCasService>(relaxed = true)
            coEvery { this@mockk.lexue } returns lexue
        }
        val sso = ssoReturning(CasLoginDto.Success)
        val r = GenerateLexueIcalUrlUseCase(api, sso).invoke(req())
        assertTrue(r is LexueUrlResult.Ok)
        assertEquals("https://lexue.bit.edu.cn/calendar/export_execute.php?authtoken=T", (r as LexueUrlResult.Ok).url)
    }

    @Test fun `missing sesskey returns Failed ParseFail`() = runTest {
        val lexue = mockk<BitLexueService> { coEvery { getIndexHtml() } returns ok("<html>no key</html>") }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk<BitCasService>(relaxed = true)
            coEvery { this@mockk.lexue } returns lexue
        }
        val sso = ssoReturning(CasLoginDto.Success)
        val r = GenerateLexueIcalUrlUseCase(api, sso).invoke(req())
        assertTrue(r is LexueUrlResult.Failed)
        assertTrue((r as LexueUrlResult.Failed).error is DdlSyncError.ParseFail)
    }
}
