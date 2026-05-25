package com.example.personal_studio.core.util

/**
 * BIT 成绩 → 绩点 换算。正方教务的成绩列表(cjcx_list)只给"成绩"不给"绩点"，
 * 故按北理工官方算法本地换算（与 BIT101-iOS ScoreModels 一致）：
 *
 *   等级制：优秀=4.0 良好=3.6 中等=2.8 及格=1.7 不及格=0
 *   百分制：<60 → 0；否则 4 − 3×(100−分)²/1600
 *
 * 二级制(通过/合格/免修等)不计入 GPA → 返回 null（与 [GpaCalculator] 的 null 跳过一致）。
 */
object BitGpaConverter {

    private val LEVEL = mapOf(
        "优秀" to 4.0, "优" to 4.0,
        "良好" to 3.6, "良" to 3.6,
        "中等" to 2.8, "中" to 2.8,
        "及格" to 1.7,
        "不及格" to 0.0,
    )

    /** 不计入 GPA 的二级制成绩（按 null 处理）。 */
    private val NON_GPA = listOf("通过", "合格", "免修", "缓考", "免考")

    /** 计为 0 绩点的失败类成绩。 */
    private val FAIL = listOf("不及格", "不合格", "不通过", "缺考", "作弊")

    /** 返回绩点；二级制/无法识别为"通过"类的返回 null（不计入 GPA）。 */
    fun toGradePoint(rawScore: String): Double? {
        val s = rawScore.trim()
        if (s.isEmpty()) return null
        LEVEL[s]?.let { return it }
        s.toDoubleOrNull()?.let { num -> return if (num < 60.0) 0.0 else 4.0 - 3.0 * (100 - num) * (100 - num) / 1600.0 }
        if (FAIL.any { it in s }) return 0.0
        if (NON_GPA.any { it in s }) return null
        // 其它等级词（如"良"已在 LEVEL）——保守起见不计入 GPA
        return null
    }

    /** 等级→代表分（用于加权均分）：优秀95/良好85/中等75/及格65/不及格40；
     *  百分制原样；二级制(通过/合格/免修)与缺考/作弊返回 null(不计入均分)。 */
    fun toScore(rawScore: String): Double? {
        val s = rawScore.trim()
        if (s.isEmpty()) return null
        when (s) {
            "优秀", "优" -> return 95.0
            "良好", "良" -> return 85.0
            "中等", "中" -> return 75.0
            "及格" -> return 65.0
            "不及格" -> return 40.0
        }
        s.toDoubleOrNull()?.let { return it }
        return null
    }
}
