package com.example.personal_studio.domain.bitgrades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsxsdGradeParserTest {

    private val parser = JsxsdGradeParser()

    // Mirrors the real jsxsd cjcx_list table verified on device (16 columns:
    // 序号/开课学期/课程编号/课程名称/成绩/成绩标识/学分/总学时/考试性质/考核方式/
    // 课程属性/课程性质/课程归属/课程种类/是否第一次考试/操作栏). Score cells wrap the
    // value in an <a>; the 绩点 column does NOT exist (computed from 成绩).
    private val html = """
        <html><body>
        <table id="dataList" width="100%" class="Nsb_r_list Nsb_table">
          <tr>
            <th class="Nsb_r_list_thb">序号</th><th class="Nsb_r_list_thb">开课学期</th>
            <th class="Nsb_r_list_thb">课程编号</th><th class="Nsb_r_list_thb">课程名称</th>
            <th class="Nsb_r_list_thb">成绩</th><th class="Nsb_r_list_thb">成绩标识</th>
            <th class="Nsb_r_list_thb">学分</th><th class="Nsb_r_list_thb">总学时</th>
            <th class="Nsb_r_list_thb">考试性质</th><th class="Nsb_r_list_thb">考核方式</th>
            <th class="Nsb_r_list_thb">课程属性</th><th class="Nsb_r_list_thb">课程性质</th>
            <th>课程归属</th><th>课程种类</th><th>是否第一次考试</th><th>操作栏</th>
          </tr>
          <tr>
            <td>1</td><td>2024-2025-1</td><td>100028016</td><td align="left">思想道德修养</td>
            <td><a href="javascript:JsMod('/jsxsd/kscj/pscj_list.do?zcj=优秀')">优秀</a></td><td></td>
            <td>3.0</td><td>48</td><td>正常</td><td>考试</td><td></td><td>必修</td>
            <td></td><td></td><td>是</td>
            <td><a onclick="JsMod('/jsxsd/kscj/cjfx?xnxq01id=2024-2025-1&kch=100028016&cjfs=C')">成绩分析</a></td>
          </tr>
          <tr>
            <td>2</td><td>2024-2025-1</td><td>100171018</td><td align="left">数学分析I</td>
            <td><a href="x">80</a></td><td></td><td>5.0</td><td>80</td><td>正常</td><td>考试</td>
            <td></td><td>必修</td><td></td><td></td><td>是</td><td><a>成绩分析</a></td>
          </tr>
          <tr>
            <td>3</td><td>2023-2024-2</td><td>100070006</td><td align="left">物理实验</td>
            <td><a href="x">不及格</a></td><td></td><td>2.0</td><td>32</td><td>正常</td><td>考查</td>
            <td></td><td>必修</td><td></td><td></td><td>是</td><td><a>成绩分析</a></td>
          </tr>
        </table></body></html>
    """.trimIndent()

    @Test fun `parses real jsxsd columns and converts score to grade point`() {
        val rows = parser.parse(html, fetchedAt = 9L)
        assertEquals(3, rows.size)
        val byName = rows.associateBy { it.courseName }

        val moral = byName["思想道德修养"]!!
        assertEquals("2024-2025-1", moral.termCode)
        assertEquals("100028016", moral.courseCode)
        assertEquals(3.0, moral.credit, 0.001)
        assertEquals("优秀", moral.score)
        assertEquals(4.0, moral.gradePoint!!, 0.001) // 优秀 → 4.0
        assertEquals("必修", moral.category)
        assertEquals("正常", moral.attemptType)
        assertTrue(moral.isPass)
        assertEquals(9L, moral.fetchedAt)

        val math = byName["数学分析I"]!!
        // 80 → 4 − 3×(20²)/1600 = 4 − 0.75 = 3.25
        assertEquals(3.25, math.gradePoint!!, 0.001)
        assertEquals(5.0, math.credit, 0.001)
        assertTrue(math.isPass)

        val phys = byName["物理实验"]!!
        assertEquals(0.0, phys.gradePoint!!, 0.001) // 不及格 → 0
        assertFalse(phys.isPass)
        assertEquals("2023-2024-2", phys.termCode)
    }

    @Test fun `no dataList table yields empty`() {
        assertEquals(0, parser.parse("<html><body>nothing</body></html>", 1L).size)
    }

    @Test fun `review gate detected only when no dataList`() {
        assertTrue(parser.isReviewGated("<html><body>请先完成评教后再查询成绩</body></html>"))
        assertFalse(parser.isReviewGated(html)) // has dataList → not gated even if it said 评教
    }

    @Test fun `pass-only grade (通过) does not count toward gpa`() {
        val rows = parser.parse(
            html.replace("<td><a href=\"x\">80</a></td>", "<td><a href=\"x\">通过</a></td>"), 1L,
        )
        assertNull(rows.first { it.courseName == "数学分析I" }.gradePoint) // 通过 → null (P/NP)
    }
}
