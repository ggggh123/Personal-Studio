package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.network.bit.dto.WeekDateDto
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resolves & persists the semester anchor (week-1 Monday) from BIT's cxzkbrq.do
 * response. The caller requests `"ZC":"1"` for the picked term, so the response
 * lists week 1's seven days with dates; the `weekday == 1` (Monday) entry is the
 * semester's first day.
 *
 * Always overwrites [SemesterPreferences.startDate] with the BIT-derived date so
 * a re-import refreshes the anchor to the picked term (fixes stale anchor across
 * semesters). Manual override, if any, is intentionally superseded by import.
 */
class ResolveSemesterAnchorUseCase @Inject constructor(
    private val semesterPrefs: SemesterPreferences,
) {
    suspend operator fun invoke(week1Days: List<WeekDateDto>): LocalDate {
        require(week1Days.isNotEmpty()) { "week-1 day list must be non-empty" }

        val monday = week1Days.firstOrNull { it.weekday == 1 }
            ?: throw IllegalStateException("cxzkbrq response had no Monday (XQ=1) entry")
        val semesterMonday = LocalDate.parse(monday.date)
        semesterPrefs.setStartDate(semesterMonday)
        return semesterMonday
    }
}
