package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.bitgrades.model.TermRank
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGradeSummaryUseCaseTest {
    @Test fun `summary contains gpa terms courses and flags, no PII`() {
        val book = GradeBook(
            terms = listOf(TermGrades("2024-2025-2", "24春",
                courses = listOf(
                    GradeItem("高数", "M1", 5.0, "92", 4.0, "A", "必修", "正常", true),
                    GradeItem("物理", "P1", 4.0, "55", 0.0, "F", "必修", "正常", false),
                ),
                weightedGpa = 2.22, avgScore = 75.5, rank = TermRank(5, 32, 18, 120))),
            overallGpa = 2.22, totalCredits = 9.0, overallAvgScore = 75.5,
            overallRank = TermRank(null, null, 18, 120),
        )
        val s = BuildGradeSummaryUseCase().invoke(book)
        assertTrue("高数" in s && "物理" in s)
        assertTrue("挂科" in s)            // 不及格标记
        assertTrue("专业" in s)            // 排名
        assertTrue("GPA" in s)
    }
}
