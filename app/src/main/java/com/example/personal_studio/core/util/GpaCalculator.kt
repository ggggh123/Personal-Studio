package com.example.personal_studio.core.util

/** GPA 计算。M1 仅含加权 GPA；What-if 反推/预测在 M2 追加。 */
object GpaCalculator {
    /** 学分加权 GPA：Σ(credit×point)/Σ(credit)；point==null 的课（P/NP）不计入。 */
    fun weightedGpa(items: List<Pair<Double, Double?>>): Double {
        var sumCredit = 0.0
        var sumWeighted = 0.0
        for ((credit, point) in items) {
            if (point == null) continue
            sumCredit += credit
            sumWeighted += credit * point
        }
        return if (sumCredit == 0.0) 0.0 else sumWeighted / sumCredit
    }
}
