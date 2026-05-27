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

    /** 目标反推结果。[value] = 剩余课程平均需达到的绩点；[achievable] = 是否 ≤ maxPoint 且有剩余学分。 */
    data class RequiredAvg(val value: Double, val achievable: Boolean)

    /**
     * 反推：要把总 GPA 提到 [targetGpa]，剩余 [remainingCredits] 学分平均需多少绩点。
     * required = (target×(done+remaining) − weightedSumDone) / remaining
     */
    fun requiredAverageForTarget(
        doneCredits: Double,
        weightedSumDone: Double,
        remainingCredits: Double,
        targetGpa: Double,
        maxPoint: Double = 4.0,
    ): RequiredAvg {
        if (remainingCredits <= 0.0) return RequiredAvg(0.0, achievable = false)
        val required = (targetGpa * (doneCredits + remainingCredits) - weightedSumDone) / remainingCredits
        return RequiredAvg(required, achievable = required <= maxPoint && required >= 0.0)
    }

    /** 预测：给未出分课程填假设绩点，算预计新总 GPA（学分加权合并当前与预测）。 */
    fun project(
        currentItems: List<Pair<Double, Double?>>,
        predicted: List<Pair<Double, Double>>,
    ): Double = weightedGpa(currentItems + predicted.map { it.first to it.second })
}
