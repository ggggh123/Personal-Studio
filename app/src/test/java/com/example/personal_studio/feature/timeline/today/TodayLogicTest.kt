package com.example.personal_studio.feature.timeline.today

import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.ui.theme.daylight.SunDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TodayLogicTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 11, 16) // a Monday
    private val sun = SunDay(day, sunrise = 6.9, sunset = 16.9, solarNoon = 11.9, maxElevation = 30.0)

    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    // ── Periods and taglines ────────────────────────────────────────────────

    @Test fun `periods follow the sun`() {
        assertEquals(DayPeriod.LATE_NIGHT, periodOf(2.0, sun))
        assertEquals(DayPeriod.BEFORE_DAWN, periodOf(6.0, sun))
        assertEquals(DayPeriod.DAWN, periodOf(7.2, sun))
        assertEquals(DayPeriod.MORNING, periodOf(9.0, sun))
        assertEquals(DayPeriod.NOON, periodOf(12.0, sun))
        assertEquals(DayPeriod.AFTERNOON, periodOf(14.5, sun))
        assertEquals(DayPeriod.SUNSET, periodOf(16.5, sun))
        assertEquals(DayPeriod.EVENING, periodOf(19.0, sun))
        assertEquals(DayPeriod.LATE_NIGHT, periodOf(23.0, sun))
    }

    @Test fun `exam tomorrow wins at night`() {
        val facts = TaglineFacts(examTomorrow = true, tasksDueTonight = 2)
        assertEquals("明天有考试，今晚早点睡", Taglines.pick(DayPeriod.EVENING, 20.0, facts, 1))
        // In the morning the period line shows instead.
        assertTrue(Taglines.pick(DayPeriod.MORNING, 9.0, facts, 1) in Taglines.PERIOD.getValue(DayPeriod.MORNING))
    }

    @Test fun `tasks due tonight after five`() {
        val facts = TaglineFacts(tasksDueTonight = 2)
        assertEquals("今晚还有 2 项截止", Taglines.pick(DayPeriod.SUNSET, 17.2, facts, 1))
        assertTrue(Taglines.pick(DayPeriod.SUNSET, 16.5, facts, 1) in Taglines.PERIOD.getValue(DayPeriod.SUNSET))
    }

    @Test fun `classes over in the afternoon`() {
        val facts = TaglineFacts(coursesToday = 3, coursesAllOver = true)
        assertEquals("今天的课都上完了", Taglines.pick(DayPeriod.AFTERNOON, 15.0, facts, 1))
        assertTrue(Taglines.pick(DayPeriod.MORNING, 10.0, facts, 1) in Taglines.PERIOD.getValue(DayPeriod.MORNING))
    }

    @Test fun `period line rotates by day of year`() {
        val lines = Taglines.PERIOD.getValue(DayPeriod.NOON)
        assertEquals(lines[0], Taglines.pick(DayPeriod.NOON, 12.0, TaglineFacts(), 3))
        assertEquals(lines[1], Taglines.pick(DayPeriod.NOON, 12.0, TaglineFacts(), 4))
    }

    @Test fun `facts read today and tomorrow`() {
        val now = at(20)
        val today = listOf(
            item(1, TimelineType.COURSE, at(8), at(9, 35)),
            item(2, TimelineType.TASK, at(23, 59)),
            item(3, TimelineType.TASK, at(22), done = true),
            item(4, TimelineType.TASK, at(12)), // already past
        )
        val tomorrow = listOf(item(5, TimelineType.EXAM, at(9, d = day.plusDays(1)), at(11, d = day.plusDays(1))))
        val facts = Taglines.facts(today, tomorrow, now, 20.0)
        assertEquals(TaglineFacts(examTomorrow = true, tasksDueTonight = 1, coursesToday = 1, coursesAllOver = true), facts)
    }

    @Test fun `after midnight tomorrow's exam means later today`() {
        val now = at(1)
        val today = listOf(item(1, TimelineType.EXAM, at(9), at(11)))
        assertTrue(Taglines.facts(today, emptyList(), now, 1.0).examTomorrow)
    }

    // ── Next item ───────────────────────────────────────────────────────────

    @Test fun `course in progress shows remaining time and progress`() {
        val course = item(1, TimelineType.COURSE, at(8), at(9, 35), location = "理教 305", instructor = "陈老师")
        val next = nextUp(listOf(course), emptyList(), at(8, 30))
        next as NextUp.InProgress
        assertEquals(30f / 95f, next.progress, 0.001f)
        val text = nextCardText(next, zone)
        assertEquals("进行中 · 还剩 1 小时 5 分", text.label)
        assertEquals("08:00–09:35 · 理教 305 · 陈老师", text.meta)
        assertEquals(1L, text.itemId)
    }

    @Test fun `next course soon is relative, later is a time`() {
        val course = item(1, TimelineType.COURSE, at(14), at(15, 35))
        assertEquals("下一项 · 2 小时后", nextCardText(nextUp(listOf(course), emptyList(), at(12)), zone).label)
        assertEquals("下一项 · 14:00", nextCardText(nextUp(listOf(course), emptyList(), at(9)), zone).label)
    }

    @Test fun `task deadline labels`() {
        val task = item(1, TimelineType.TASK, at(23, 59), source = TimelineSource.IMPORTED_LEXUE, courseName = "数据结构")
        val soon = nextCardText(nextUp(listOf(task), emptyList(), at(20)), zone)
        assertEquals("截止 · 还有 3 小时 59 分", soon.label)
        assertEquals("乐学 · 数据结构", soon.meta)
        assertEquals("今天 23:59 截止", nextCardText(nextUp(listOf(task), emptyList(), at(9)), zone).label)
    }

    @Test fun `done tasks are skipped`() {
        val done = item(1, TimelineType.TASK, at(10), done = true)
        val later = item(2, TimelineType.TASK, at(18))
        assertEquals(2L, (nextUp(listOf(done, later), emptyList(), at(9)) as NextUp.Upcoming).item.id)
    }

    @Test fun `day over points to tomorrow`() {
        val tomorrow = item(9, TimelineType.COURSE, at(8, d = day.plusDays(1)), at(9, 35, d = day.plusDays(1)), location = "理教 305")
        val text = nextCardText(nextUp(emptyList(), listOf(tomorrow), at(21)), zone)
        assertEquals("今天的安排都结束了", text.label)
        assertEquals("明天 08:00 · 理教 305", text.meta)
        assertEquals(9L, text.itemId)
        val empty = nextCardText(nextUp(emptyList(), emptyList(), at(21)), zone)
        assertEquals("明天没有安排", empty.title)
        assertNull(empty.itemId)
    }

    // ── Day rows ────────────────────────────────────────────────────────────

    @Test fun `now marker sits before the first item ahead`() {
        val items = listOf(
            item(1, TimelineType.COURSE, at(8), at(9, 35)),
            item(2, TimelineType.COURSE, at(10), at(11, 30)),
            item(3, TimelineType.TASK, at(18)),
        )
        val rows = buildDayRows(items, day, day, at(10, 30)).rows
        assertEquals(listOf("item-1", "item-2", "now", "item-3"), rows.map { it.key })
        val statuses = rows.filterIsInstance<DayRow.Entry>().map { it.status }
        assertEquals(listOf(RowStatus.PAST, RowStatus.IN_PROGRESS, RowStatus.UPCOMING), statuses)
    }

    @Test fun `now marker goes last when everything is over`() {
        val items = listOf(item(1, TimelineType.COURSE, at(8), at(9, 35)))
        assertEquals(listOf("item-1", "now"), buildDayRows(items, day, day, at(22)).rows.map { it.key })
    }

    @Test fun `other days have no now marker`() {
        val items = listOf(item(1, TimelineType.COURSE, at(8, d = day.plusDays(1)), at(9, d = day.plusDays(1))))
        assertEquals(listOf("item-1"), buildDayRows(items, day.plusDays(1), day, at(10)).rows.map { it.key })
    }

    @Test fun `task statuses and the done group`() {
        val items = listOf(
            item(1, TimelineType.TASK, at(9)),            // overdue
            item(2, TimelineType.TASK, at(12)),           // due within 3 h
            item(3, TimelineType.TASK, at(20)),           // later
            item(4, TimelineType.TASK, at(11), done = true),
        )
        val result = buildDayRows(items, day, day, at(10))
        val entries = result.rows.filterIsInstance<DayRow.Entry>()
        assertEquals(listOf(1L, 2L, 3L), entries.map { it.item.id })
        assertEquals(listOf(RowStatus.OVERDUE, RowStatus.DUE_SOON, RowStatus.UPCOMING), entries.map { it.status })
        assertEquals(listOf(4L), result.done.map { it.id })
    }

    @Test fun `a task being checked stays in place until its animation ends`() {
        val items = listOf(item(1, TimelineType.TASK, at(20), done = true))
        val held = buildDayRows(items, day, day, at(10), holding = setOf(1L))
        assertEquals(listOf(1L), held.rows.filterIsInstance<DayRow.Entry>().map { it.item.id })
        assertTrue(held.done.isEmpty())
        val released = buildDayRows(items, day, day, at(10))
        assertEquals(listOf(1L), released.done.map { it.id })
    }

    @Test fun `past days mark open tasks overdue`() {
        val yesterday = day.minusDays(1)
        val items = listOf(item(1, TimelineType.TASK, at(20, d = yesterday)), item(2, TimelineType.COURSE, at(8, d = yesterday), at(9, d = yesterday)))
        val statuses = buildDayRows(items, yesterday, day, at(10)).rows.filterIsInstance<DayRow.Entry>().associate { it.item.id to it.status }
        assertEquals(RowStatus.PAST, statuses[2L])
        assertEquals(RowStatus.OVERDUE, statuses[1L])
    }

    // ── Text ────────────────────────────────────────────────────────────────

    @Test fun `durations`() {
        assertEquals("1 分钟", TodayText.duration(10_000))
        assertEquals("45 分钟", TodayText.duration(45 * 60_000L))
        assertEquals("2 小时", TodayText.duration(120 * 60_000L))
        assertEquals("1 小时 20 分", TodayText.duration(80 * 60_000L))
        assertEquals("3 天", TodayText.duration(3 * 24 * 60 * 60_000L))
        assertEquals("23:30", TodayText.hhmm(23.5))
        assertEquals("00:00", TodayText.hhmm(24.0))
    }

    @Test fun `ago`() {
        val now = at(12)
        assertEquals("刚刚", TodayText.ago(now - 20_000, now))
        assertEquals("12 分钟前", TodayText.ago(now - 12 * 60_000L, now))
        assertEquals("2 小时前", TodayText.ago(now - 130 * 60_000L, now))
        assertEquals("3 天前", TodayText.ago(now - 3 * 24 * 60 * 60_000L, now))
    }

    @Test fun `seat comes from the exam notes`() {
        assertEquals("32", TodayText.seat(item(1, TimelineType.EXAM, at(9), at(11), notes = "座位: 32")))
        assertNull(TodayText.seat(item(2, TimelineType.EXAM, at(9), at(11))))
    }

    @Test fun `row meta`() {
        val course = item(1, TimelineType.COURSE, at(8), at(9, 35), location = "理教 305", period = 1 to 2)
        assertEquals("理教 305 · 第 1–2 节", TodayText.rowMeta(course))
        val exam = item(2, TimelineType.EXAM, at(19), at(21), location = "理教 105", notes = "座位: 32")
        assertEquals("理教 105 · 座位 32", TodayText.rowMeta(exam))
        assertEquals("手动添加", TodayText.rowMeta(item(3, TimelineType.TASK, at(18))))
    }

    @Test fun `teaching week`() {
        val start = LocalDate.of(2026, 9, 7)
        assertEquals(1, semesterWeek(start, start))
        assertEquals(11, semesterWeek(start, day))
        assertNull(semesterWeek(start, start.minusDays(1)))
        assertNull(semesterWeek(null, day))
    }
}

internal fun item(
    id: Long,
    type: TimelineType,
    start: Long,
    end: Long? = null,
    done: Boolean = false,
    location: String? = null,
    instructor: String? = null,
    notes: String? = null,
    source: TimelineSource = TimelineSource.MANUAL,
    courseName: String? = null,
    period: Pair<Int, Int>? = null,
) = TimelineItem(
    id = id, type = type, title = "item$id", description = null,
    startAt = start, endAt = end, isDone = done, doneAt = null,
    location = location, instructor = instructor, notes = notes,
    seriesId = null, periodIndex = period?.first, periodEndIndex = period?.second,
    weekdayCode = null, weekIndexInSemester = null, colorOverride = null,
    sourceType = source, sourceExternalId = null,
    kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0, courseName = courseName,
)
