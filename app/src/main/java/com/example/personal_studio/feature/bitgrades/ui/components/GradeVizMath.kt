package com.example.personal_studio.feature.bitgrades.ui.components

import java.util.Locale
import kotlin.math.floor

/** 从 "前20%"/"前5%"/"20%" 抽出顶部百分数(整数 1..100);无有效数字返回 null。 */
fun parseTopPercent(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    val raw = Regex("""(\d+(?:\.\d+)?)\s*%""").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    return raw.toInt().takeIf { it in 1..100 }
}

/** 分数字符串转 Float;等级制/「优」等非数字返回 null。 */
fun scoreToFloat(score: String): Float? = score.trim().toFloatOrNull()

/** 整数分去小数(98.0→"98"),否则保 1 位。 */
fun fmtScore(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

/** 成绩对比条轴下界:min(平均,你) 向下取整到 10,且 ≥ 0。 */
fun scoreAxisLo(avg: Double, you: Float): Float =
    (floor(minOf(avg.toFloat(), you) / 10f) * 10f).coerceAtLeast(0f)
