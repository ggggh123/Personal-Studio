package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.network.bit.dto.WeekDateDto
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resolves the semester anchor (week-1 Monday) using the smart default C:
 * - If [SemesterPreferences.startDate] is already set, return it verbatim.
 * - Otherwise read it directly from BIT's cxzkbrq.do response. The caller
 *   requests `"ZC":"1"` (week 1), so the response lists all seven days of
 *   week 1 with dates; the entry with `weekday == 1` (Monday) is the
 *   semester's first day. Persist it and return.
 *
 * (The first iteration tried to back-solve from a `currentWeek` index, but the
 * real cxzkbrq.do response carries dates directly when ZC=1 — no math needed.)
 */
class ResolveSemesterAnchorUseCase @Inject constructor(
    private val semesterPrefs: SemesterPreferences,
) {
    suspend operator fun invoke(week1Days: List<WeekDateDto>): LocalDate {
        semesterPrefs.startDate.first()?.let { return it }
        require(week1Days.isNotEmpty()) { "week-1 day list must be non-empty" }

        val monday = week1Days.firstOrNull { it.weekday == 1 }
            ?: throw IllegalStateException("cxzkbrq response had no Monday (XQ=1) entry")
        val semesterMonday = LocalDate.parse(monday.date)
        semesterPrefs.setStartDate(semesterMonday)
        return semesterMonday
    }
}
