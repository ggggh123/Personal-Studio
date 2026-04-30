package com.example.personal_studio.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure mapping from a (semester start, week index, weekday, period range) tuple
 * to local epoch-ms timestamps.
 *
 * Conventions:
 * - `semesterStart` is the Monday of week 1 (callers normalise non-Mondays via
 *   `normalizeSemesterStart` before passing in).
 * - `weekIndex` is 1-based.
 * - `weekday` is 1..7 with 1=Mon, 7=Sun (matches ISO + our entity field).
 * - `timetable` lookup is by `index`; out-of-range indices throw IllegalArgumentException.
 */
object SemesterTimeMapper {

    fun normalizeSemesterStart(picked: LocalDate): LocalDate {
        // Roll back to the Monday of `picked`'s ISO week.
        val dow = picked.dayOfWeek.value // 1..7 with Mon=1
        return picked.minusDays((dow - 1).toLong())
    }

    fun dateOf(semesterStart: LocalDate, weekIndex: Int, weekday: Int): LocalDate {
        require(weekIndex >= 1) { "weekIndex must be >= 1, was $weekIndex" }
        require(weekday in 1..7) { "weekday must be 1..7, was $weekday" }
        return semesterStart.plusWeeks((weekIndex - 1).toLong()).plusDays((weekday - 1).toLong())
    }

    fun mapPeriodRange(
        semesterStart: LocalDate,
        weekIndex: Int,
        weekday: Int,
        periodStart: Int,
        periodEnd: Int,
        timetable: List<TimetablePeriod>,
        zone: ZoneId,
    ): Pair<Long, Long> {
        require(periodStart >= 1 && periodEnd >= periodStart) {
            "periodStart=$periodStart, periodEnd=$periodEnd"
        }
        val startTime = timetable.firstOrNull { it.index == periodStart }?.startHHmm
            ?: error("Timetable has no period $periodStart")
        val endTime = timetable.firstOrNull { it.index == periodEnd }?.endHHmm
            ?: error("Timetable has no period $periodEnd")
        val date = dateOf(semesterStart, weekIndex, weekday)
        val startEpoch = LocalDateTime.of(date, LocalTime.parse(startTime)).atZone(zone).toInstant().toEpochMilli()
        val endEpoch = LocalDateTime.of(date, LocalTime.parse(endTime)).atZone(zone).toInstant().toEpochMilli()
        return startEpoch to endEpoch
    }

    /** Suppressed warning lint helper: convert ISO DayOfWeek (Mon=1..Sun=7). */
    fun dayOfWeekToCode(dow: DayOfWeek): Int = dow.value
}
