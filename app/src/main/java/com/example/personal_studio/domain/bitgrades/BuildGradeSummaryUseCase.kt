package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.CreditFormat
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import javax.inject.Inject
import java.util.Locale

/** GradeBook → 紧凑脱敏文本（无姓名/学号）。AI 报告 prompt 与聊天 seed 共用。 */
class BuildGradeSummaryUseCase @Inject constructor() {
    fun invoke(book: GradeBook): String = buildString {
        appendLine("总GPA=${fmt(book.overallGpa)} 总学分=${CreditFormat.format(book.totalCredits)}")
        book.overallRank?.majorPercentile?.let { appendLine("专业排名约 前${it}%") }
        book.terms.forEach { t ->
            val rankStr = t.rank?.let { r ->
                buildString {
                    r.classRank?.let { append(" 班级$it/${r.classTotal}") }
                    r.majorRank?.let { append(" 专业$it/${r.majorTotal}") }
                }
            }.orEmpty()
            appendLine("【${t.termName}】GPA=${fmt(t.weightedGpa)}$rankStr")
            t.courses.forEach { c ->
                append("- ${c.courseName} 学分${CreditFormat.format(c.credit)} 成绩${c.score}")
                c.gradePoint?.let { append(" 绩点${fmt(it)}") }
                if (!c.isPass) append(" [挂科]")
                if (c.attemptType != "正常") append(" [${c.attemptType}]")
                appendLine()
            }
        }
    }
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)
}
