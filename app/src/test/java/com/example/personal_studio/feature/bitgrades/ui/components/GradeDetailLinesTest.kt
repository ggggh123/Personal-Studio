package com.example.personal_studio.feature.bitgrades.ui.components

import com.example.personal_studio.domain.bitgrades.model.GradeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeDetailLinesTest {

    @Test fun `full item produces three grouped lines`() {
        val c = GradeItem(
            "高等数学", "C1", 4.0, "92", 4.0, null, "必修", "正常", true,
            courseAvg = 78.7, classRankText = "67%", majorRankText = "63%",
            courseMaxScore = 100.0, courseStudyCount = 1178,
            classSize = 18, majorSize = 32, schoolRankText = "46%",
        )
        val lines = gradeDetailLines(c)
        assertEquals(3, lines.size)
        assertEquals("必修  ·  绩点 4.0", lines[0])
        assertEquals("平均 78.7  ·  最高 100  ·  修学 1178人", lines[1])
        assertEquals("班级 67%(18人)  ·  专业 63%(32人)  ·  全校 46%", lines[2])
    }

    @Test fun `empty item yields no lines`() {
        val c = GradeItem("体育", "C2", 1.0, "通过", null, null, null, "正常", true)
        assertEquals(emptyList<String>(), gradeDetailLines(c))
    }

    @Test fun `only category and gradePoint yields one line`() {
        val c = GradeItem("思修", "C3", 2.0, "85", 3.7, null, "必修", "正常", true)
        val lines = gradeDetailLines(c)
        assertEquals(1, lines.size)
        assertEquals("必修  ·  绩点 3.7", lines[0])
    }

    @Test fun `rank without sizes omits parentheses`() {
        // gradePoint=null 让课程属性行也为空，单独验证排名行无人数时不带括注。
        val c = GradeItem(
            "x", "C4", 3.0, "80", null, null, null, "正常", true,
            classRankText = "50%",
        )
        assertEquals(listOf("班级 50%"), gradeDetailLines(c))
    }

    @Test fun `non-integer max score keeps one decimal`() {
        val c = GradeItem(
            "x", "C5", 3.0, "88", null, null, null, "正常", true,
            courseMaxScore = 99.5,
        )
        assertEquals(listOf("最高 99.5"), gradeDetailLines(c))
    }
}
