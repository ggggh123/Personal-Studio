package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.bitgrades.model.TermRank
import org.junit.Assert.assertFalse
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

    @Test fun `anonymizes politically-sensitive course names with 思政课 N`() {
        // Real-world trigger: LLM gateway returned HTTP 500 local:sensitive_words
        // on prompts that named 马克思主义/中共党史/etc. We replace those names with
        // "思政课 N" placeholders but keep credit/score/gpa so the AI can still analyze.
        val book = GradeBook(
            terms = listOf(TermGrades("2024-2025-1", "24秋",
                courses = listOf(
                    GradeItem("马克思主义基本原理", "P1", 3.0, "85", 3.5, null, "必修", "正常", true),
                    GradeItem("中国近现代史纲要", "P2", 2.0, "88", 3.7, null, "必修", "正常", true),
                    GradeItem("形势与政策(1)", "P3", 0.25, "90", 3.85, null, "必修", "正常", true),
                    GradeItem("高等数学A", "M1", 5.0, "92", 4.0, null, "必修", "正常", true),
                ),
                weightedGpa = 3.8, avgScore = 88.0, rank = null)),
            overallGpa = 3.8, totalCredits = 10.25, overallAvgScore = 88.0,
            overallRank = null,
        )
        val s = BuildGradeSummaryUseCase().invoke(book)
        // sensitive names scrubbed
        assertFalse("敏感词应被脱敏: 马克思", "马克思" in s)
        assertFalse("敏感词应被脱敏: 中国近现代史", "中国近现代史" in s)
        assertFalse("敏感词应被脱敏: 形势与政策", "形势与政策" in s)
        // placeholders present
        assertTrue("应当包含'思政课1'占位", "思政课1" in s)
        assertTrue("应当包含'思政课2'占位", "思政课2" in s)
        assertTrue("应当包含'思政课3'占位", "思政课3" in s)
        // numeric data and non-sensitive name preserved
        assertTrue("非敏感课程名应保留", "高等数学A" in s)
        assertTrue("学分仍展示", "学分0.25" in s)   // 形势与政策 课的 0.25 学分
        assertTrue("末尾注释告知脱敏数量", "已将 3 门" in s)
    }
}
