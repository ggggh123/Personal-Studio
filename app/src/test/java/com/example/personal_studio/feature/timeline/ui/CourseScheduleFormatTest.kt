package com.example.personal_studio.feature.timeline.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseScheduleFormatTest {

    @Test fun `full schedule with multiple weekdays period range and credits`() {
        val result = formatCourseSchedule(
            weekdays = listOf(1, 3), periodStart = 1, periodEnd = 2,
            minWeek = 1, maxWeek = 16, occurrenceCount = 32, credits = 3.5f,
        )
        assertEquals("周一/周三 第1-2节 · 第1-16周 · 共32节 · 3.5学分", result)
    }

    @Test fun `single period single weekday no credits`() {
        val result = formatCourseSchedule(
            weekdays = listOf(2), periodStart = 3, periodEnd = 3,
            minWeek = 1, maxWeek = 18, occurrenceCount = 18, credits = null,
        )
        assertEquals("周二 第3节 · 第1-18周 · 共18节", result)
    }

    @Test fun `single week and whole-number credits drop trailing zero`() {
        val result = formatCourseSchedule(
            weekdays = listOf(5), periodStart = 1, periodEnd = 2,
            minWeek = 4, maxWeek = 4, occurrenceCount = 1, credits = 2.0f,
        )
        assertEquals("周五 第1-2节 · 第4周 · 共1节 · 2学分", result)
    }

    @Test fun `missing weekday and period omits the when segment`() {
        val result = formatCourseSchedule(
            weekdays = emptyList(), periodStart = null, periodEnd = null,
            minWeek = 1, maxWeek = 16, occurrenceCount = 16, credits = null,
        )
        assertEquals("第1-16周 · 共16节", result)
    }

    @Test fun `weekdayCn maps codes to chinese`() {
        assertEquals("周一", weekdayCn(1))
        assertEquals("周日", weekdayCn(7))
    }
}
