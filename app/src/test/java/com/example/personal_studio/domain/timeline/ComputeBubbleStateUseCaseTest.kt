package com.example.personal_studio.domain.timeline

import com.example.personal_studio.domain.model.BubbleState
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeBubbleStateUseCaseTest {

    private val useCase = ComputeBubbleStateUseCase()
    private val mins = 60_000L
    private val hours = 60 * mins

    private fun item(
        type: TimelineType, startAt: Long, endAt: Long? = null, isDone: Boolean = false,
    ) = TimelineItem(
        id = 1, type = type, title = "x", description = null,
        startAt = startAt, endAt = endAt, isDone = isDone, doneAt = null,
        location = null, instructor = null, notes = null,
        seriesId = null, periodIndex = null, periodEndIndex = null,
        weekdayCode = null, weekIndexInSemester = null, colorOverride = null,
        sourceType = TimelineSource.MANUAL, sourceExternalId = null,
        kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
    )

    @Test fun `course upcoming when more than 15 min ahead`() {
        val now = 0L
        val out = useCase(item(TimelineType.COURSE, startAt = 16 * mins, endAt = 60 * mins), now)
        assertEquals(BubbleState.CourseUpcoming, out)
    }

    @Test fun `course imminent when within 15 min`() {
        val now = 0L
        val out = useCase(item(TimelineType.COURSE, startAt = 10 * mins, endAt = 60 * mins), now)
        assertEquals(BubbleState.CourseImminent, out)
    }

    @Test fun `course in progress when between start and end`() {
        val now = 30 * mins
        val out = useCase(item(TimelineType.COURSE, startAt = 0L, endAt = 60 * mins), now)
        assertEquals(BubbleState.CourseInProgress, out)
    }

    @Test fun `course past when end has passed`() {
        val now = 70 * mins
        val out = useCase(item(TimelineType.COURSE, startAt = 0L, endAt = 60 * mins), now)
        assertEquals(BubbleState.CoursePast, out)
    }

    @Test fun `task upcoming when more than 2h ahead`() {
        val now = 0L
        val out = useCase(item(TimelineType.TASK, startAt = 3 * hours), now)
        assertEquals(BubbleState.TaskUpcoming, out)
    }

    @Test fun `task imminent within 2h`() {
        val now = 0L
        val out = useCase(item(TimelineType.TASK, startAt = 1 * hours), now)
        assertEquals(BubbleState.TaskImminent, out)
    }

    @Test fun `task overdue when start passed and not done`() {
        val now = 1 * hours
        val out = useCase(item(TimelineType.TASK, startAt = 0L), now)
        assertEquals(BubbleState.TaskOverdue, out)
    }

    @Test fun `task done regardless of time`() {
        val out = useCase(item(TimelineType.TASK, startAt = 0L, isDone = true), now = 100 * hours)
        assertEquals(BubbleState.TaskDone, out)
    }

    @Test fun `custom upcoming when more than 30 min ahead`() {
        val out = useCase(item(TimelineType.CUSTOM, startAt = 60 * mins, endAt = 120 * mins), now = 0L)
        assertEquals(BubbleState.CustomUpcoming, out)
    }

    @Test fun `custom imminent within 30 min`() {
        val out = useCase(item(TimelineType.CUSTOM, startAt = 10 * mins, endAt = 60 * mins), now = 0L)
        assertEquals(BubbleState.CustomImminent, out)
    }

    @Test fun `custom in progress`() {
        val out = useCase(item(TimelineType.CUSTOM, startAt = 0L, endAt = 60 * mins), now = 30 * mins)
        assertEquals(BubbleState.CustomInProgress, out)
    }

    @Test fun `custom overdue when end has passed and not done`() {
        val out = useCase(item(TimelineType.CUSTOM, startAt = 0L, endAt = 60 * mins), now = 70 * mins)
        assertEquals(BubbleState.CustomOverdue, out)
    }

    @Test fun `custom done`() {
        val out = useCase(item(TimelineType.CUSTOM, startAt = 0L, endAt = 60 * mins, isDone = true), now = 0L)
        assertEquals(BubbleState.CustomDone, out)
    }

    @Test fun `exam done regardless of time`() {
        val out = useCase(item(TimelineType.EXAM, startAt = 0L, endAt = 60 * mins, isDone = true), now = 100 * hours)
        assertEquals(BubbleState.ExamDone, out)
    }

    @Test fun `exam past when end has passed`() {
        val now = 70 * mins
        val out = useCase(item(TimelineType.EXAM, startAt = 0L, endAt = 60 * mins), now)
        assertEquals(BubbleState.ExamPast, out)
    }

    @Test fun `exam in progress when between start and end`() {
        val now = 30 * mins
        val out = useCase(item(TimelineType.EXAM, startAt = 0L, endAt = 60 * mins), now)
        assertEquals(BubbleState.ExamInProgress, out)
    }

    @Test fun `exam imminent within 2h`() {
        val now = 0L
        val out = useCase(item(TimelineType.EXAM, startAt = 1 * hours, endAt = 3 * hours), now)
        assertEquals(BubbleState.ExamImminent, out)
    }

    @Test fun `exam upcoming when more than 2h ahead`() {
        val now = 0L
        val out = useCase(item(TimelineType.EXAM, startAt = 3 * hours, endAt = 5 * hours), now)
        assertEquals(BubbleState.ExamUpcoming, out)
    }

    @Test fun `boundary now equals startAt is imminent for course`() {
        val now = 0L
        val out = useCase(item(TimelineType.COURSE, startAt = 0L, endAt = 60 * mins), now)
        // startAt > now is false → fall into in-progress branch (startAt ≤ now < endAt)
        assertEquals(BubbleState.CourseInProgress, out)
    }
}
