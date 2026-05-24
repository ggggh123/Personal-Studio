package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.network.bit.dto.GradeRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGradeUseCaseTest {
    private val mapper = MapGradeUseCase()

    @Test fun `maps a normal course`() {
        val e = mapper.invoke(GradeRowDto(
            termCode = "2024-2025-2", termName = "24春", courseName = "高数",
            courseCode = "M1", credit = 5.0, score = "92", gradePoint = 4.0,
            gradeLetter = "A", category = "必修", attemptType = "正常",
        ), fetchedAt = 7L)!!
        assertEquals("高数", e.courseName)
        assertEquals(5.0, e.credit, 0.001)
        assertTrue(e.isPass)
        assertEquals(7L, e.fetchedAt)
    }

    @Test fun `null courseName or termCode is dropped`() {
        assertNull(mapper.invoke(GradeRowDto(termCode = "x", courseName = null), 1L))
        assertNull(mapper.invoke(GradeRowDto(termCode = null, courseName = "y"), 1L))
    }

    @Test fun `failing gradePoint marks not pass`() {
        val e = mapper.invoke(GradeRowDto(
            termCode = "t", courseName = "物理", gradePoint = 0.0, score = "55",
        ), 1L)!!
        assertFalse(e.isPass)
        assertEquals("正常", e.attemptType) // 默认值
    }

    @Test fun `pass inferred from 等级 word when no gradePoint`() {
        val e = mapper.invoke(GradeRowDto(
            termCode = "t", courseName = "体育", gradePoint = null, score = "通过",
        ), 1L)!!
        assertTrue(e.isPass)
    }

    @Test fun `不及格 等级 without gradePoint is not pass`() {
        // 回归：'不及格' 含子串 '及格'，否定词必须先于通过词判定
        val fail = mapper.invoke(GradeRowDto(
            termCode = "t", courseName = "高数", gradePoint = null, score = "不及格",
        ), 1L)!!
        assertFalse(fail.isPass)
        val fail2 = mapper.invoke(GradeRowDto(
            termCode = "t", courseName = "高数", gradePoint = null, gradeLetter = "不通过", score = "",
        ), 1L)!!
        assertFalse(fail2.isPass)
    }
}
