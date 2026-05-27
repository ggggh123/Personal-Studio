package com.example.personal_studio.domain.bitgrades

import org.junit.Assert.assertEquals
import org.junit.Test

class JsxsdDetailParserTest {
    // Mirrors the real jsxsd cjfx 成绩分析 page verified on device: a flat list of
    // `<td>标签：值</td>` cells (full-width colon). Labels confirmed:
    // 班级人数/专业人数/学习人数/平均分/最高分/本人成绩/本人成绩在班级中占/在专业中占/在所有学生中占.
    @Test fun `parses avg and rank percentiles from real cjfx cells`() {
        val html = """
            <table>
            <tr><td>班级人数：18 人</td><td>专业人数：32 人</td><td>学习人数：1178 人</td></tr>
            <tr><td>平均分：78.687</td><td>最高分：100</td><td>本人成绩：83</td></tr>
            <tr><td>本人成绩在班级中占：67%</td><td>本人成绩在专业中占：63%</td>
                <td>本人成绩在所有学生中占：46%</td></tr></table>
        """.trimIndent()
        val d = JsxsdDetailParser().parse(html)
        assertEquals(78.687, d.courseAvg!!, 0.001)
        assertEquals(100.0, d.courseMaxScore!!, 0.001)
        assertEquals(1178, d.courseStudyCount)
        assertEquals("67%", d.classRankText)
        assertEquals("63%", d.majorRankText)
    }
}
