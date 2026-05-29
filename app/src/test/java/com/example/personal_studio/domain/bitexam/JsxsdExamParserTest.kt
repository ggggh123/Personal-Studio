package com.example.personal_studio.domain.bitexam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class JsxsdExamParserTest {
    private val parser = JsxsdExamParser()

    private fun plus8(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    private fun page(vararg rows: String): String {
        val header = "<tr><th>序号</th><th>校区</th><th>课程编号</th><th>课程名称</th>" +
            "<th>任课教师</th><th>考试时间</th><th>考点</th><th>座位号</th></tr>"
        return """<table id="dataList">$header${rows.joinToString("")}</table>"""
    }
    private fun row(code: String, name: String, teacher: String, time: String, place: String, seat: String) =
        "<tr><td>1</td><td>中关村</td><td>$code</td><td>$name</td>" +
            "<td>$teacher</td><td>$time</td><td>$place</td><td>$seat</td></tr>"

    @Test fun `parses a full exam row with start-end range`() {
        val html = page(row("A101", "高等数学", "张老师", "2026-01-05 08:00~10:00", "中教401", "23"))
        val r = parser.parse(html, term = "2025-2026-1")
        assertEquals(1, r.size)
        val e = r.single()
        assertEquals("高等数学", e.course)
        assertEquals(plus8(2026, 1, 5, 8, 0), e.startAt)
        assertEquals(plus8(2026, 1, 5, 10, 0), e.endAt)
        assertEquals("中教401", e.location)
        assertEquals("23", e.seat)
        assertEquals("张老师", e.invigilator)
        assertEquals("2025-2026-1|A101|${plus8(2026,1,5,8,0)}", e.uid)
    }

    @Test fun `accepts hyphen and fullwidth tilde separators`() {
        val a = parser.parse(page(row("c","x","t","2026-01-05 08:00-10:00","r","1")), "t").single()
        assertEquals(plus8(2026,1,5,10,0), a.endAt)
        val b = parser.parse(page(row("c","x","t","2026-01-05 08:00～10:00","r","1")), "t").single()
        assertEquals(plus8(2026,1,5,10,0), b.endAt)
    }

    @Test fun `date only with no time degrades to midnight and null end`() {
        val e = parser.parse(page(row("c","x","t","2026-01-05","r","1")), "t").single()
        assertEquals(plus8(2026,1,5,0,0), e.startAt)
        assertNull(e.endAt)
    }

    @Test fun `maps columns by header regardless of order`() {
        val header = "<tr><th>课程名称</th><th>考试时间</th><th>考点</th><th>座位号</th></tr>"
        val body = "<tr><td>线性代数</td><td>2026-01-06 14:00~16:00</td><td>理406</td><td>7</td></tr>"
        val e = parser.parse("""<table id="dataList">$header$body</table>""", "t").single()
        assertEquals("线性代数", e.course)
        assertEquals("理406", e.location)
        assertEquals("7", e.seat)
    }

    @Test fun `multiple rows`() {
        val html = page(
            row("a","课1","t","2026-01-05 08:00~10:00","r1","1"),
            row("b","课2","t","2026-01-06 14:00~16:00","r2","2"),
        )
        assertEquals(listOf("课1", "课2"), parser.parse(html, "t").map { it.course })
    }

    @Test fun `empty result yields empty list`() {
        assertTrue(parser.parse("""<table id="dataList"><tr><td>未查询到数据</td></tr></table>""", "t").isEmpty())
        assertTrue(parser.parse("<html>no table</html>", "t").isEmpty())
    }

    @Test fun `extractCurrentTerm reads selected xnxqid option`() {
        val html = """<select id="xnxqid" name="xnxqid">
            <option value="2024-2025-2">2024-2025-2</option>
            <option value="2025-2026-1" selected>2025-2026-1</option></select>"""
        assertEquals("2025-2026-1", JsxsdExamParser.extractCurrentTerm(html))
    }
}
