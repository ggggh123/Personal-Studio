package com.example.personal_studio.feature.profile.ui

import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.feature.bitimport.ui.importSlotText
import com.example.personal_studio.feature.timeline.ui.courseDraftSummary
import com.example.personal_studio.feature.timeline.ui.withDate
import com.example.personal_studio.feature.timeline.ui.withTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SchoolTextTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun `remaining time picks the largest whole unit`() {
        assertEquals("还有 1 分钟", SchoolText.remaining(20_000))
        assertEquals("还有 25 分钟", SchoolText.remaining(25 * 60_000L))
        assertEquals("还有 5 小时", SchoolText.remaining(5 * 3_600_000L + 59 * 60_000L))
        assertEquals("还有 3 天", SchoolText.remaining(3 * 86_400_000L + 1))
        assertEquals("已开始", SchoolText.remaining(0))
    }

    @Test fun `exam span keeps the end time on the same day`() {
        assertEquals("11 月 16 日 周一 19:00–21:00", SchoolText.span(at(2026, 11, 16, 19), at(2026, 11, 16, 21), zone))
        assertEquals("11 月 16 日 周一 19:00", SchoolText.span(at(2026, 11, 16, 19), null, zone))
        assertEquals("11 月 16 日 周一 23:00 – 11 月 17 日 周二 01:00", SchoolText.span(at(2026, 11, 16, 23), at(2026, 11, 17, 1), zone))
    }

    @Test fun `deadlines group by day`() {
        val now = at(2026, 11, 16, 8)
        assertEquals("今天", SchoolText.dueGroup(at(2026, 11, 16, 23, 59), now, zone))
        assertEquals("明天", SchoolText.dueGroup(at(2026, 11, 17, 0, 30), now, zone))
        assertEquals("7 天内", SchoolText.dueGroup(at(2026, 11, 22, 12), now, zone))
        assertEquals("更晚", SchoolText.dueGroup(at(2026, 11, 23, 12), now, zone))
    }


    @Test fun `new course summary counts the classes`() {
        assertEquals("周一、周三 第 1–2 节 · 第 1–16 周 · 共 32 次课", courseDraftSummary(setOf(3, 1), 1, 2, 1, 16))
        assertEquals("周五 第 3 节 · 第 4 周 · 共 1 次课", courseDraftSummary(setOf(5), 3, 3, 4, 4))
        assertNull(courseDraftSummary(emptySet(), 1, 2, 1, 16))
    }

    @Test fun `imported slot names weekday and periods`() {
        fun item(day: Int?, a: Int?, b: Int?) = TimelineItemEntity(
            type = com.example.personal_studio.domain.model.TimelineType.COURSE, title = "T", startAt = 0,
            weekdayCode = day, periodIndex = a, periodEndIndex = b, createdAt = 0, updatedAt = 0,
        )
        assertEquals("周三 第 3–5 节", importSlotText(item(3, 3, 5)))
        assertEquals("周日 第 1 节", importSlotText(item(7, 1, 1)))
        assertEquals("", importSlotText(item(null, null, null)))
    }

    @Test fun `picked date keeps the time, picked time keeps the date`() {
        val d = LocalDate.of(2026, 11, 20)
        assertEquals(at(2026, 11, 20, 23, 59), withDate(null, d, LocalTime.of(23, 59), zone))
        assertEquals(at(2026, 11, 20, 14, 30), withDate(at(2026, 11, 18, 14, 30), d, LocalTime.of(9, 0), zone))
        assertEquals(at(2026, 11, 18, 8, 5), withTime(at(2026, 11, 18, 14, 30), 8, 5, zone))
    }
}
