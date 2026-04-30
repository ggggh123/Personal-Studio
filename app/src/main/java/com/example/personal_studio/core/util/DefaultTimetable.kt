package com.example.personal_studio.core.util

/**
 * Stable seed for the default 13-period university timetable.
 *
 * The user can override the timetable per Settings; this is only the first-launch
 * seed and the "restore default" target. Times are local 24h "HH:mm".
 */
data class TimetablePeriod(
    val index: Int,
    val startHHmm: String,
    val endHHmm: String,
)

object DefaultTimetable {
    val PERIODS: List<TimetablePeriod> = listOf(
        TimetablePeriod(1,  "08:00", "08:45"),
        TimetablePeriod(2,  "08:50", "09:35"),
        TimetablePeriod(3,  "09:55", "10:40"),
        TimetablePeriod(4,  "10:45", "11:30"),
        TimetablePeriod(5,  "11:35", "12:20"),
        TimetablePeriod(6,  "13:20", "14:05"),
        TimetablePeriod(7,  "14:10", "14:55"),
        TimetablePeriod(8,  "15:15", "16:00"),
        TimetablePeriod(9,  "16:05", "16:50"),
        TimetablePeriod(10, "16:55", "17:40"),
        TimetablePeriod(11, "18:30", "19:15"),
        TimetablePeriod(12, "19:20", "20:05"),
        TimetablePeriod(13, "20:10", "20:55"),
    )
}
