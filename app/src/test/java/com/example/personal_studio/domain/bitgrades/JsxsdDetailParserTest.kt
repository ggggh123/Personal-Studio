package com.example.personal_studio.domain.bitgrades

import org.junit.Assert.assertEquals
import org.junit.Test

class JsxsdDetailParserTest {
    @Test fun `parses avg and rank percentiles from td label-value cells`() {
        val html = """
            <table><tr><td>课程名称：高数</td><td>平均分：85.3</td></tr>
            <tr><td>最高分：99</td><td>本人成绩在班级中占：前 20%</td></tr>
            <tr><td>本人成绩在专业中占：前 35%</td></tr></table>
        """.trimIndent()
        val d = JsxsdDetailParser().parse(html)
        assertEquals(85.3, d.courseAvg!!, 0.001)
        assertEquals("前 20%", d.classRankText)
        assertEquals("前 35%", d.majorRankText)
    }
}
