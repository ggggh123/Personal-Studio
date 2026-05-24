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
}
