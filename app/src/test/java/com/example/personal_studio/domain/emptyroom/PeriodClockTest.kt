package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodClockTest {
    private val clock = PeriodClock(DefaultTimetable.PERIODS)

    @Test fun `start and end minute of period`() {
        assertEquals(8 * 60, clock.startMinute(1))
        assertEquals(8 * 60 + 45, clock.endMinute(1))
        assertEquals(13 * 60 + 20, clock.startMinute(6))
    }

    @Test fun `periodAt maps minute-of-day to period`() {
        assertEquals(1, clock.periodAt(8 * 60 + 10))
        assertEquals(6, clock.periodAt(13 * 60 + 30))
        assertNull(clock.periodAt(7 * 60))
        assertNull(clock.periodAt(12 * 60 + 40))
    }
}
