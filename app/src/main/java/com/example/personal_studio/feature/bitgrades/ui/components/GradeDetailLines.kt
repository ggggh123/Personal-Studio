package com.example.personal_studio.feature.bitgrades.ui.components

import com.example.personal_studio.domain.bitgrades.model.GradeItem
import java.util.Locale

/**
 * 课程展开后的详情，分 3 组：课程属性 / 成绩对比 / 排名。
 * 每行内 null 项省略；整行全 null 则不产出该行；全部为空返回空列表（UI 显示「无详情」）。
 * 抽成纯函数便于单测（不依赖 Compose）。
 */
fun gradeDetailLines(c: GradeItem): List<String> {
    val attrs = buildList {
        c.category?.takeIf { it.isNotBlank() }?.let { add(it) }
        c.gradePoint?.let { add("绩点 " + String.format(Locale.US, "%.1f", it)) }
    }
    val scores = buildList {
        c.courseAvg?.let { add("平均 " + String.format(Locale.US, "%.1f", it)) }
        c.courseMaxScore?.let { add("最高 " + fmtScore(it)) }
        c.courseStudyCount?.let { add("修学 ${it}人") }
    }
    val ranks = buildList {
        c.classRankText?.let { add("班级 $it" + (c.classSize?.let { n -> "(${n}人)" } ?: "")) }
        c.majorRankText?.let { add("专业 $it" + (c.majorSize?.let { n -> "(${n}人)" } ?: "")) }
        c.schoolRankText?.let { add("全校 $it") }
    }
    return listOf(attrs, scores, ranks)
        .filter { it.isNotEmpty() }
        .map { it.joinToString("  ·  ") }
}

/** 整数分去掉小数（最高 100 而非 100.0），否则保 1 位。 */
private fun fmtScore(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
