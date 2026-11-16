package com.example.personal_studio.feature.timeline.today

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Quick task deadlines, detail wording and the week grid's time mapping. */
class TodayDetailTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 11, 16)

    private fun at(h: Int, m: Int = 0, d: LocalDate = monday) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    @Test fun `quick deadlines on a weekday`() {
        val dues = quickDues(at(10), zone)
        assertEquals(listOf("今晚 23:59", "明天 12:00", "本周日 23:59"), dues.map { it.label })
        assertEquals(listOf(at(23, 59), at(12, d = monday.plusDays(1)), at(23, 59, d = monday.plusDays(6))), dues.map { it.epoch })
        assertEquals(0, defaultDueIndex(at(10), zone))
        assertEquals(1, defaultDueIndex(at(21, 30), zone))
    }

    @Test fun `on sunday the third deadline is next sunday`() {
        val sunday = monday.minusDays(1)
        val dues = quickDues(at(9, d = sunday), zone)
        assertEquals("下周日 23:59", dues[2].label)
        assertEquals(at(23, 59, d = sunday.plusDays(7)), dues[2].epoch)
    }

    @Test fun `task detail marks passed reminders and the lexue note`() {
        val due = at(18)
        val task = item(1, TimelineType.TASK, due, source = TimelineSource.IMPORTED_LEXUE, courseName = "数据结构")
        val spec = detailSpec(task, now = at(16, 30), zone = zone)
        assertEquals("乐学作业", spec.kind)
        assertEquals("11 月 16 日 周一 18:00 截止", spec.subtitle)
        assertEquals(
            listOf("截止前 1 天" to true, "截止前 2 小时" to true, "截止前 30 分钟" to false, "到期提醒" to false),
            spec.reminders,
        )
        assertEquals(listOf("来源" to "乐学 · 数据结构", "状态" to "未完成"), spec.rows)
        assertTrue(spec.note!!.contains("不会同步到乐学"))
    }

    @Test fun `done task switches reminders off`() {
        val spec = detailSpec(item(1, TimelineType.TASK, at(18), done = true), now = at(10), zone = zone)
        assertTrue(spec.remindersOff)
        assertEquals("已完成", spec.rows.toMap()["状态"])
        assertNull(spec.note)
    }

    @Test fun `exam detail shows seat and source`() {
        val exam = item(2, TimelineType.EXAM, at(19), at(21), location = "理教 105", notes = "座位: 32", source = TimelineSource.IMPORTED_EXAM)
        val spec = detailSpec(exam, now = at(10), zone = zone)
        assertEquals("考试", spec.kind)
        assertEquals("11 月 16 日 周一 · 19:00–21:00", spec.subtitle)
        assertEquals(listOf("地点" to "理教 105", "座位号" to "32", "来源" to "教务考试安排"), spec.rows)
        assertEquals(listOf("考前 1 天", "考前 2 小时", "考前 30 分钟"), spec.reminders.map { it.first })
    }

    @Test fun `course detail`() {
        val course = item(3, TimelineType.COURSE, at(8), at(9, 35), location = "理教 305", instructor = "陈老师", period = 1 to 2, source = TimelineSource.IMPORTED_PORTAL)
        val spec = detailSpec(course, now = at(7), zone = zone)
        assertEquals("11 月 16 日 周一 · 第 1–2 节 · 08:00–09:35", spec.subtitle)
        assertEquals(listOf("上课前 10 分钟" to false), spec.reminders)
        assertTrue(spec.note!!.startsWith("来自教务课表导入"))
    }

    @Test fun `week grid maps times onto period rows`() {
        val grid = PeriodGrid(DefaultTimetable.PERIODS)
        assertEquals(13, grid.count)
        assertEquals(0f, grid.rowsOf(8 * 60)!!, 0.001f)
        assertEquals(0.5f, grid.rowsOf(8 * 60 + 22)!!, 0.03f)
        // Break between period 2 (ends 09:35) and 3 (starts 09:55) collapses onto the line.
        assertEquals(2f, grid.rowsOf(9 * 60 + 45)!!, 0.001f)
        assertNull(grid.rowsOf(7 * 60))
        assertNull(grid.rowsOf(22 * 60))
    }
}
