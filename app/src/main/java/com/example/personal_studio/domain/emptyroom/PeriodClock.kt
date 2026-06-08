package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.TimetablePeriod
import java.time.LocalTime

/** 基于一份作息表快照,提供 节次 ↔ 当天分钟数(minute-of-day) 的换算。 */
class PeriodClock(periods: List<TimetablePeriod>) {
    private val byIndex = periods.associateBy { it.index }

    private fun toMin(hhmm: String): Int = LocalTime.parse(hhmm).let { it.hour * 60 + it.minute }

    fun startMinute(period: Int): Int = byIndex[period]?.let { toMin(it.startHHmm) } ?: 0
    fun endMinute(period: Int): Int = byIndex[period]?.let { toMin(it.endHHmm) } ?: 0

    /** 给定当天分钟数,返回它落在哪一节(上课时段内);课间/课前课后返回 null。 */
    fun periodAt(minuteOfDay: Int): Int? =
        byIndex.values.firstOrNull { minuteOfDay >= toMin(it.startHHmm) && minuteOfDay < toMin(it.endHHmm) }?.index
}
