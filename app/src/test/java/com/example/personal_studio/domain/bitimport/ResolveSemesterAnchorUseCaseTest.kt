package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.network.bit.dto.WeekDateDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ResolveSemesterAnchorUseCaseTest {

    @Test fun `respects existing startDate when already set`() = runBlocking {
        val existing = LocalDate.of(2026, 2, 24)
        val prefs = mockk<SemesterPreferences> {
            coEvery { startDate } returns flowOf(existing)
        }
        val useCase = ResolveSemesterAnchorUseCase(prefs)

        val anchor = useCase.invoke(
            currentWeek = 7,
            weekDays = listOf(WeekDateDto(weekday = 1, date = "2026-05-18")),
        )

        assertEquals(existing, anchor)
        coVerify(exactly = 0) { prefs.setStartDate(any()) }
    }

    @Test fun `backsolves week-1 Monday when prefs unset`() = runBlocking {
        val prefs = mockk<SemesterPreferences>(relaxed = true) {
            coEvery { startDate } returns flowOf(null)
        }
        val useCase = ResolveSemesterAnchorUseCase(prefs)

        // 2026-05-18 is a Monday. If today is "week 7, day 1", week-1 Monday =
        // 2026-05-18 - 6 weeks = 2026-04-06.
        val anchor = useCase.invoke(
            currentWeek = 7,
            weekDays = listOf(
                WeekDateDto(weekday = 1, date = "2026-05-18"),
                WeekDateDto(weekday = 2, date = "2026-05-19"),
                WeekDateDto(weekday = 7, date = "2026-05-24"),
            ),
        )

        assertEquals(LocalDate.of(2026, 4, 6), anchor)
        coVerify(exactly = 1) { prefs.setStartDate(LocalDate.of(2026, 4, 6)) }
    }

    @Test fun `non-Monday earliest day correctly walked back to Monday`() = runBlocking {
        val prefs = mockk<SemesterPreferences>(relaxed = true) {
            coEvery { startDate } returns flowOf(null)
        }
        val useCase = ResolveSemesterAnchorUseCase(prefs)

        // If the smallest date in the response is Wednesday 2026-05-20, walk
        // back 2 days to Monday 2026-05-18, then back 6 weeks → 2026-04-06.
        val anchor = useCase.invoke(
            currentWeek = 7,
            weekDays = listOf(
                WeekDateDto(weekday = 3, date = "2026-05-20"),
                WeekDateDto(weekday = 4, date = "2026-05-21"),
            ),
        )

        assertEquals(LocalDate.of(2026, 4, 6), anchor)
    }
}
