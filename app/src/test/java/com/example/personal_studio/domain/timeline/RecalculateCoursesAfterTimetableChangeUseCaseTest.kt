package com.example.personal_studio.domain.timeline

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecalculateCoursesAfterTimetableChangeUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val semester = LocalDate.of(2026, 9, 7)

    private fun epoch(date: LocalDate, h: Int, m: Int) =
        LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    @Test fun `future course rows recompute against new timetable`() = runTest {
        val courseEpochOld = epoch(semester, 9, 55) // period 3 = 09:55 in default
        val course = TimelineItem(
            id = 1, type = TimelineType.COURSE, title = "高数",
            description = null, startAt = courseEpochOld, endAt = epoch(semester, 10, 40),
            isDone = false, doneAt = null,
            location = null, instructor = null, notes = null,
            seriesId = 1, periodIndex = 3, periodEndIndex = 3,
            weekdayCode = 1, weekIndexInSemester = 1, colorOverride = null,
            sourceType = TimelineSource.MANUAL, sourceExternalId = null,
            kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
        )
        val repo = FakeTimelineRepository().apply { preload(listOf(course)) }
        val newTimetable = DefaultTimetable.PERIODS.map {
            if (it.index == 3) it.copy(startHHmm = "09:50", endHHmm = "10:35") else it
        }
        val useCase = RecalculateCoursesAfterTimetableChangeUseCase(
            repo = repo, zone = zone,
            semesterProvider = { semester },
            nowProvider = { 0L },
        )
        useCase(newTimetable, mutableListOf<Long>())

        val updated = repo.findById(1)!!
        assertEquals(epoch(semester, 9, 50), updated.startAt)
        assertEquals(epoch(semester, 10, 35), updated.endAt)
    }

    @Test fun `past course rows are not touched`() = runTest {
        val pastEpoch = epoch(LocalDate.of(2026, 9, 1), 9, 55) // before semester
        val course = TimelineItem(
            id = 1, type = TimelineType.COURSE, title = "高数",
            description = null, startAt = pastEpoch, endAt = epoch(LocalDate.of(2026, 9, 1), 10, 40),
            isDone = false, doneAt = null,
            location = null, instructor = null, notes = null,
            seriesId = 1, periodIndex = 3, periodEndIndex = 3,
            weekdayCode = 1, weekIndexInSemester = 1, colorOverride = null,
            sourceType = TimelineSource.MANUAL, sourceExternalId = null,
            kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
        )
        val repo = FakeTimelineRepository().apply { preload(listOf(course)) }
        val newTimetable = DefaultTimetable.PERIODS.map {
            if (it.index == 3) it.copy(startHHmm = "09:50", endHHmm = "10:35") else it
        }
        val futureNow = epoch(LocalDate.of(2026, 10, 1), 0, 0) // future relative to course
        val touched = mutableListOf<Long>()
        val useCase = RecalculateCoursesAfterTimetableChangeUseCase(
            repo = repo, zone = zone,
            semesterProvider = { semester },
            nowProvider = { futureNow },
        )
        useCase(newTimetable, touched)
        assertEquals(0, touched.size)
        val unchanged = repo.findById(1)!!
        assertEquals(pastEpoch, unchanged.startAt)
    }

    @Test fun `unchanged rows are not re-written and excluded from outRescheduleIds`() = runTest {
        val courseEpoch = epoch(semester, 9, 55) // period 3 in default
        val course = TimelineItem(
            id = 1, type = TimelineType.COURSE, title = "高数",
            description = null, startAt = courseEpoch, endAt = epoch(semester, 10, 40),
            isDone = false, doneAt = null,
            location = null, instructor = null, notes = null,
            seriesId = 1, periodIndex = 3, periodEndIndex = 3,
            weekdayCode = 1, weekIndexInSemester = 1, colorOverride = null,
            sourceType = TimelineSource.MANUAL, sourceExternalId = null,
            kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
        )
        val repo = FakeTimelineRepository().apply { preload(listOf(course)) }
        val touched = mutableListOf<Long>()
        // Identical timetable — should be a no-op
        val useCase = RecalculateCoursesAfterTimetableChangeUseCase(
            repo = repo, zone = zone,
            semesterProvider = { semester },
            nowProvider = { 0L },
        )
        useCase(DefaultTimetable.PERIODS, touched)
        assertEquals(0, touched.size)
    }
}
