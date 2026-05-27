package com.example.personal_studio.core.util

import com.example.personal_studio.domain.bitgrades.model.GradeItem

data class GradeBucket(val label: String, val count: Int)

/**
 * 成绩分布分桶。若大多数成绩是数字 → A/B/C/D/F 分段（A≥90,B80-89,C70-79,D60-69,F<60）；
 * 否则按原始等级字符串分桶（优/良/通过…）。
 */
object GradeBucketer {
    private val BAND_ORDER = listOf("A", "B", "C", "D", "F")

    fun bucket(items: List<GradeItem>): List<GradeBucket> {
        if (items.isEmpty()) return emptyList()
        val numeric = items.mapNotNull { it.score.toDoubleOrNull() }
        val numericRatio = numeric.size.toDouble() / items.size
        return if (numericRatio >= 0.5) bucketNumeric(items) else bucketLabel(items)
    }

    private fun band(score: Double): String = when {
        score >= 90 -> "A"
        score >= 80 -> "B"
        score >= 70 -> "C"
        score >= 60 -> "D"
        else -> "F"
    }

    private fun bucketNumeric(items: List<GradeItem>): List<GradeBucket> {
        val counts = items.mapNotNull { it.score.toDoubleOrNull() }
            .groupingBy { band(it) }.eachCount()
        return BAND_ORDER.mapNotNull { b -> counts[b]?.let { GradeBucket(b, it) } }
    }

    private fun bucketLabel(items: List<GradeItem>): List<GradeBucket> =
        items.groupingBy { it.score.ifBlank { "—" } }.eachCount()
            .map { GradeBucket(it.key, it.value) }
            .sortedByDescending { it.count }
}
