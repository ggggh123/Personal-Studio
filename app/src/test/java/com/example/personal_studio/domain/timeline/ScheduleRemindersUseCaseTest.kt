package com.example.personal_studio.domain.timeline

import com.example.personal_studio.domain.model.TimelineType
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleRemindersUseCaseTest {

    @Test fun `course slots include single 10-min`() {
        val slots = ScheduleRemindersUseCase.slotsFor(TimelineType.COURSE)
        assertEquals(1, slots.size)
        assertEquals(10, slots[0].minBefore)
        assertEquals(false, slots[0].isOverdue)
    }

    @Test fun `task slots include 1440 120 30 plus overdue`() {
        val slots = ScheduleRemindersUseCase.slotsFor(TimelineType.TASK)
        assertEquals(4, slots.size)
        val overdue = slots.first { it.isOverdue }
        assertEquals(0, overdue.minBefore)
    }

    @Test fun `custom slots include single 30-min`() {
        val slots = ScheduleRemindersUseCase.slotsFor(TimelineType.CUSTOM)
        assertEquals(1, slots.size)
        assertEquals(30, slots[0].minBefore)
    }

    @Test fun `exam slots include 1440 120 30 with no overdue`() {
        val slots = ScheduleRemindersUseCase.slotsFor(TimelineType.EXAM)
        assertEquals(3, slots.size)
        assertEquals(listOf(1440, 120, 30), slots.map { it.minBefore })
        assertEquals(true, slots.none { it.isOverdue })
    }

    @Test fun `work name format is reminder_id_minBefore_isOverdue`() {
        val slot = ScheduleRemindersUseCase.slotsFor(TimelineType.TASK).first { it.isOverdue }
        assertEquals("reminder_42_0_true", slot.workName(42L))
    }
}
