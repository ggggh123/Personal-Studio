package com.example.personal_studio.domain.bitimport

import app.cash.turbine.test
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.ScheduleResponse
import com.example.personal_studio.data.network.bit.dto.TermDto
import com.example.personal_studio.data.network.bit.dto.TermListResponse
import com.example.personal_studio.data.network.bit.dto.WeekDateDto
import com.example.personal_studio.data.network.bit.dto.WeekDateResponse
import com.example.personal_studio.domain.bitimport.model.ImportCredentials
import com.example.personal_studio.domain.bitimport.model.ImportError
import com.example.personal_studio.domain.bitimport.model.ImportRequest
import com.example.personal_studio.domain.bitimport.model.ImportStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ImportCoursesUseCaseTest {

    @Test fun `wrong password emits Failed-WrongCredentials and never proceeds`() = runTest {
        val ssoLogin = mockk<SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        }
        val apiClient = mockk<BitApiClient>(relaxed = true)
        val useCase = ImportCoursesUseCase(
            apiClient = apiClient,
            ssoLogin = ssoLogin,
            anchor = mockk(relaxed = true),
            mapper = mockk(relaxed = true),
            replacer = mockk(relaxed = true),
            timelineDao = mockk(relaxed = true),
        )

        val req = ImportRequest(
            credentials = ImportCredentials("u", "p"),
            networkMode = NetworkMode.LOCAL,
            rememberPwd = false,
        )

        useCase.import(req, confirmChannel = Channel(Channel.RENDEZVOUS)).test {
            assertTrue(awaitItem() is ImportStep.LoggingIn)
            val failed = awaitItem() as ImportStep.Failed
            assertTrue(failed.err is ImportError.WrongCredentials)
            awaitComplete()
        }
        coVerify(exactly = 1) { apiClient.close() }
    }

    @Test fun `cancel at Preview produces Cancelled`() = runTest {
        val ssoLogin = mockk<SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.Success
        }
        val termResp = Response.success(TermListResponse(
            datas = TermListResponse.Datas(dqxnxq = TermListResponse.Rows(
                rows = listOf(TermDto(code = "2024-2025-2"))
            ))
        ))
        val weekResp = Response.success(WeekDateResponse(
            data = listOf(WeekDateDto(weekday = 1, date = "2026-05-18")), currentWeek = 7))
        val schedResp = Response.success(ScheduleResponse(
            datas = ScheduleResponse.Datas(cxxszhxqkb = ScheduleResponse.Rows(rows = emptyList()))
        ))
        val apiClient = mockk<BitApiClient>(relaxed = true) {
            coEvery { jwapp.getCurrentTerm() } returns termResp
            coEvery { jwapp.getWeekAndDate(any()) } returns weekResp
            coEvery { jwapp.getSchedule(any()) } returns schedResp
        }
        val anchor = mockk<ResolveSemesterAnchorUseCase> {
            coEvery { this@mockk.invoke(any(), any()) } returns java.time.LocalDate.of(2026, 2, 23)
        }
        val useCase = ImportCoursesUseCase(
            apiClient = apiClient,
            ssoLogin = ssoLogin,
            anchor = anchor,
            mapper = mockk(relaxed = true),
            replacer = mockk(relaxed = true),
            timelineDao = mockk(relaxed = true) {
                coEvery { countImportedInRange(any(), any()) } returns 0
                coEvery { maxSeriesId() } returns 50L
            },
        )

        val channel = Channel<Boolean>(Channel.RENDEZVOUS)
        val req = ImportRequest(
            credentials = ImportCredentials("u", "p"),
            networkMode = NetworkMode.LOCAL,
            rememberPwd = false,
        )

        useCase.import(req, channel).test {
            assertTrue(awaitItem() is ImportStep.LoggingIn)
            assertTrue(awaitItem() is ImportStep.FetchingTerm)
            assertTrue(awaitItem() is ImportStep.FetchingWeekDate)
            assertTrue(awaitItem() is ImportStep.FetchingSchedule)
            assertTrue(awaitItem() is ImportStep.Mapping)
            assertTrue(awaitItem() is ImportStep.Preview)
            channel.send(false)
            assertTrue(awaitItem() is ImportStep.Cancelled)
            awaitComplete()
        }
        coVerify(exactly = 1) { apiClient.close() }
    }
}
