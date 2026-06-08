package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.network.bit.dto.RoomOccupancyDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeFreeSlotsUseCaseTest {
    private val clock = PeriodClock(DefaultTimetable.PERIODS)
    private val uc = ComputeFreeSlotsUseCase()

    @Test fun `parses busy periods and inverts to free ranges`() {
        val r = uc.invoke(RoomOccupancyDto(roomName = "101", busyPeriods = "1,2,3,7,8"), "J1", "理教", clock, nowMinute = 0)
        assertEquals(setOf(1, 2, 3, 7, 8), r.busyPeriods)
        assertEquals(listOf(4..6, 9..13), r.freeRanges)
    }

    @Test fun `null busy means free all day`() {
        val r = uc.invoke(RoomOccupancyDto(roomName = "102", busyPeriods = null), "J1", "理教", clock, nowMinute = 0)
        assertEquals(emptySet<Int>(), r.busyPeriods)
        assertEquals(listOf(1..13), r.freeRanges)
    }

    @Test fun `freeNow true when now is in a free period`() {
        val r = uc.invoke(RoomOccupancyDto("103", "1,2"), "J1", "理教", clock, nowMinute = 9 * 60 + 40)
        assertEquals(true, r.status.freeNow)
        assertEquals(20 * 60 + 55, r.status.freeUntilMinuteOfDay)
    }

    @Test fun `freeNow false during a busy period gives next free time`() {
        val r = uc.invoke(RoomOccupancyDto("104", "3"), "J1", "理教", clock, nowMinute = 10 * 60 + 10)
        assertEquals(false, r.status.freeNow)
        assertEquals(10 * 60 + 45, r.status.nextFreeMinuteOfDay)
    }
}
