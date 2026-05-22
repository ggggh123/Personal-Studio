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
            week1Days = listOf(WeekDateDto(weekday = 1, date = "2026-02-23")),
        )

        assertEquals(existing, anchor)
        coVerify(exactly = 0) { prefs.setStartDate(any()) }
    }

    @Test fun `reads week-1 Monday date directly when prefs unset`() = runBlocking {
        val prefs = mockk<SemesterPreferences>(relaxed = true) {
            coEvery { startDate } returns flowOf(null)
        }
        val useCase = ResolveSemesterAnchorUseCase(prefs)

        // cxzkbrq.do with ZC=1 returns week-1's seven days WITH dates.
        // The XQ=1 (Monday) entry is the semester start, taken verbatim.
        val anchor = useCase.invoke(
            week1Days = listOf(
                WeekDateDto(weekday = 1, date = "2026-02-23"),
                WeekDateDto(weekday = 2, date = "2026-02-24"),
                WeekDateDto(weekday = 7, date = "2026-03-01"),
            ),
        )

        assertEquals(LocalDate.of(2026, 2, 23), anchor)
        coVerify(exactly = 1) { prefs.setStartDate(LocalDate.of(2026, 2, 23)) }
    }

    @Test fun `picks the Monday entry regardless of list order`() = runBlocking {
        val prefs = mockk<SemesterPreferences>(relaxed = true) {
            coEvery { startDate } returns flowOf(null)
        }
        val useCase = ResolveSemesterAnchorUseCase(prefs)

        val anchor = useCase.invoke(
            week1Days = listOf(
                WeekDateDto(weekday = 3, date = "2026-02-25"),
                WeekDateDto(weekday = 1, date = "2026-02-23"),
                WeekDateDto(weekday = 5, date = "2026-02-27"),
            ),
        )

        assertEquals(LocalDate.of(2026, 2, 23), anchor)
    }
}
