package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.CreditFormat
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import javax.inject.Inject
import java.util.Locale

/** GradeBook → 紧凑脱敏文本（无姓名/学号）。AI 报告 prompt 与聊天 seed 共用。
 *  额外把政治理论类课程名替换为「思政课 N」——国内 LLM 网关常对这类词触发敏感词过滤
 *  (实测 HTTP 500 local:sensitive_words)。学分/成绩/绩点照常保留,趋势分析不受影响。 */
class BuildGradeSummaryUseCase @Inject constructor() {
    fun invoke(book: GradeBook): String = buildString {
        var ideoIdx = 0
        val anonMap = HashMap<String, String>()
        fun displayName(name: String): String =
            if (!isPoliticallySensitive(name)) name
            else anonMap.getOrPut(name) { "思政课${++ideoIdx}" }

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
                append("- ${displayName(c.courseName)} 学分${CreditFormat.format(c.credit)} 成绩${c.score}")
                c.gradePoint?.let { append(" 绩点${fmt(it)}") }
                if (!c.isPass) append(" [挂科]")
                if (c.attemptType != "正常") append(" [${c.attemptType}]")
                appendLine()
            }
        }
        if (anonMap.isNotEmpty()) {
            appendLine()
            appendLine("注: 已将 ${anonMap.size} 门政治理论类课程名脱敏为「思政课 N」(规避内容过滤),分析时按普通课程对待即可。")
        }
    }

    private fun isPoliticallySensitive(name: String): Boolean = SENSITIVE_KEYWORDS.any { it in name }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)

    companion object {
        /** 政治理论课/思政课名常见关键词。命中其一即视作敏感,整门课换匿名占位。 */
        private val SENSITIVE_KEYWORDS = listOf(
            "马克思", "毛泽东", "邓小平", "习近平",
            "中国共产党", "中共", "共产党", "党史",
            "中国特色社会主义", "社会主义",
            "中国近现代史", "近现代史",
            "形势与政策", "思想道德",
            "二十大", "三个代表",
        )
    }
}
