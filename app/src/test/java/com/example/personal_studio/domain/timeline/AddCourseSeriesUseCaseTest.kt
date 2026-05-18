package com.example.personal_studio.domain.timeline

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.CourseSeriesDraft
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AddCourseSeriesUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val repo = FakeTimelineRepository()
    private val useCase = AddCourseSeriesUseCase(
        repo = repo,
        timetableProvider = { DefaultTimetable.PERIODS },
        zone = zone,
        nowProvider = { 0L },
    )

    private val mondayStart = LocalDate.of(2026, 9, 7)

    @Test fun `monday-and-wednesday for weeks 1 to 16 yields 32 rows`() = runTest {
        val draft = CourseSeriesDraft(
            title = "高数", instructor = "李老师", location = "A-101", notes = null,
            weekdays = listOf(1, 3),
            periodStart = 1, periodEnd = 2,
            weekStart = 1, weekEnd = 16,
        )
        val (sid, count) = useCase.invoke(draft, mondayStart)
        assertEquals(32, count)
        val rows = repo.snapshot().filter { it.seriesId == sid }
        assertEquals(32, rows.size)
        assertTrue(rows.all { it.type == TimelineType.COURSE })
        assertTrue(rows.all { it.title == "高数" })
        assertTrue(rows.all { it.weekdayCode == 1 || it.weekdayCode == 3 })
        assertNotNull(rows.first().endAt)
    }

    @Test fun `single week and single weekday yields 1 row`() = runTest {
        val draft = CourseSeriesDraft(
            title = "选修", instructor = null, location = null, notes = null,
            weekdays = listOf(2),
            periodStart = 5, periodEnd = 5,
            weekStart = 8, weekEnd = 8,
        )
        val (_, count) = useCase.invoke(draft, mondayStart)
        assertEquals(1, count)
    }

    @Test fun `weekRange of 0 throws`() = runTest {
        val draft = CourseSeriesDraft(
            title = "X", instructor = null, location = null, notes = null,
            weekdays = listOf(1),
            periodStart = 1, periodEnd = 1,
            weekStart = 5, weekEnd = 4, // inverted
        )
        try {
            useCase.invoke(draft, mondayStart)
            assert(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun `crosses month boundary correctly`() = runTest {
        val mondayStart = LocalDate.of(2026, 8, 31) // Mon
        val draft = CourseSeriesDraft(
            title = "Y", instructor = null, location = null, notes = null,
            weekdays = listOf(3), // Wed
            periodStart = 1, periodEnd = 1,
            weekStart = 1, weekEnd = 1,
        )
        val (_, count) = useCase.invoke(draft, mondayStart)
        assertEquals(1, count)
        val row = repo.snapshot().first { it.weekdayCode == 3 }
        // Wed of week 1 == 2026-09-02
        // assert via local conversion not exact epoch
        val expectedStart = java.time.LocalDateTime.of(2026, 9, 2, 8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedStart, row.startAt)
    }
}
