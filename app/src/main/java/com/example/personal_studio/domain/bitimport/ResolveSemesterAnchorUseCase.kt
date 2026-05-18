package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.network.bit.dto.WeekDateDto
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resolves the semester anchor (week-1 Monday) using the smart default C:
 * - If [SemesterPreferences.startDate] is already set, return it verbatim.
 * - Otherwise back-solve it from BIT's getWeekAndDate() response:
 *     anchor = (earliest day in response) - (its weekday - 1) days
 *              - (currentWeek - 1) weeks
 *   then persist into prefs and return.
 */
class ResolveSemesterAnchorUseCase @Inject constructor(
    private val semesterPrefs: SemesterPreferences,
) {
    suspend operator fun invoke(
        currentWeek: Int,
        weekDays: List<WeekDateDto>,
    ): LocalDate {
        semesterPrefs.startDate.first()?.let { return it }
        require(weekDays.isNotEmpty()) { "weekDays must be non-empty to backsolve" }

        val earliest = weekDays.minByOrNull { it.date }!!
        val earliestDate = LocalDate.parse(earliest.date)
        val thisWeekMonday = earliestDate.minusDays((earliest.weekday - 1).toLong())
        val semesterMonday = thisWeekMonday.minusWeeks((currentWeek - 1).toLong())
        semesterPrefs.setStartDate(semesterMonday)
        return semesterMonday
    }
}
