package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SemesterTimeMapperTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val timetable = DefaultTimetable.PERIODS

    /** Helper: epoch ms at the given local date+time in Asia/Shanghai. */
    private fun epoch(d: LocalDate, t: LocalTime): Long =
        LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()

    @Test fun `week 1 monday period 1 starts at 08-00 of the semester start date`() {
        val semesterStart = LocalDate.of(2026, 9, 7) // Mon 2026-09-07
        val (start, end) = SemesterTimeMapper.mapPeriodRange(
            semesterStart = semesterStart,
            weekIndex = 1,
            weekday = 1, // Mon
            periodStart = 1,
            periodEnd = 1,
            timetable = timetable,
            zone = zone,
        )
        assertEquals(epoch(semesterStart, LocalTime.of(8, 0)), start)
        assertEquals(epoch(semesterStart, LocalTime.of(8, 45)), end)
    }

    @Test fun `period range merges contiguous endings`() {
        val semesterStart = LocalDate.of(2026, 9, 7)
        val (start, end) = SemesterTimeMapper.mapPeriodRange(
            semesterStart, weekIndex = 1, weekday = 1, periodStart = 1, periodEnd = 3,
            timetable, zone,
        )
        assertEquals(epoch(semesterStart, LocalTime.of(8, 0)), start)
        assertEquals(epoch(semesterStart, LocalTime.of(10, 40)), end)
    }

    @Test fun `week 16 wednesday period 6 lands 15 weeks plus 2 days after start`() {
        val semesterStart = LocalDate.of(2026, 9, 7) // Mon
        val expected = LocalDate.of(2026, 9, 7).plusWeeks(15).plusDays(2) // Wed of week 16
        val (start, _) = SemesterTimeMapper.mapPeriodRange(
            semesterStart, weekIndex = 16, weekday = 3, periodStart = 6, periodEnd = 7,
            timetable, zone,
        )
        assertEquals(epoch(expected, LocalTime.of(13, 20)), start)
    }

    @Test fun `crossing month boundary works without special-casing`() {
        val semesterStart = LocalDate.of(2026, 8, 31) // Mon
        val (start, _) = SemesterTimeMapper.mapPeriodRange(
            semesterStart, weekIndex = 1, weekday = 3, periodStart = 1, periodEnd = 1,
            timetable, zone,
        )
        // Wed of week 1 = 2026-09-02
        assertEquals(epoch(LocalDate.of(2026, 9, 2), LocalTime.of(8, 0)), start)
    }
}
