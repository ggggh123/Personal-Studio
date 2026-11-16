package com.example.personal_studio.feature.profile

import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.profile.ui.describeTime
import com.example.personal_studio.feature.profile.ui.shortTerm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CampusLogicTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 11, 16)
    private fun at(h: Int, d: LocalDate = day) = d.atTime(LocalTime.of(h, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun row(id: Long, title: String, start: Long, end: Long? = null, done: Boolean = false) = TimelineItemEntity(
        id = id, type = TimelineType.TASK, title = title, description = null, startAt = start, endAt = end,
        isDone = done, doneAt = null, location = null, instructor = null, notes = null, credits = null,
        seriesId = null, periodIndex = null, periodEndIndex = null, weekdayCode = null, weekIndexInSemester = null,
        colorOverride = null, sourceType = TimelineSource.IMPORTED_LEXUE, sourceExternalId = null,
        kbEntryIdsJson = "[]", createdAt = 0, updatedAt = 0,
    )

    @Test fun `agenda counts open assignments and exams still ahead`() {
        val now = at(12)
        val ddls = listOf(row(1, "late", at(9)), row(2, "done", at(20), done = true), row(3, "b", at(22)), row(4, "a", at(18)))
        val exams = listOf(row(5, "past", at(8), at(10)), row(6, "now", at(11), at(13)), row(7, "next week", at(9, day.plusDays(7))))
        val a = schoolAgenda(ddls, exams, now)
        assertEquals(2, a.ddlOpen)
        assertEquals(Upcoming("a", at(18)), a.nextDdl)
        assertEquals(2, a.examsUpcoming)
        assertEquals("now", a.nextExam?.title)
    }

    @Test fun `empty agenda`() {
        val a = schoolAgenda(emptyList(), emptyList(), at(12))
        assertEquals(0, a.ddlOpen)
        assertNull(a.nextDdl)
        assertNull(a.nextExam)
    }

    @Test fun `account is masked`() {
        assertEquals("1120 **** 21", maskAccount("1120230421"))
        assertEquals("abc", maskAccount("abc"))
    }

    @Test fun `relative times`() {
        val now = at(9)
        assertEquals("今天 22:00", describeTime(at(22), now, zone))
        assertEquals("明天 08:00", describeTime(at(8, day.plusDays(1)), now, zone))
        assertEquals("后天 19:00", describeTime(at(19, day.plusDays(2)), now, zone))
        assertEquals("周五 10:00", describeTime(at(10, day.plusDays(4)), now, zone))
        assertEquals("12 月 1 日", describeTime(at(10, LocalDate.of(2026, 12, 1)), now, zone))
    }

    @Test fun `term names shorten`() {
        assertEquals("2025 秋", shortTerm("2025-2026-1"))
        assertEquals("2026 春", shortTerm("2025-2026-2"))
        assertEquals("2026 春", shortTerm("2025–2026 学年 第二学期"))
    }
}
