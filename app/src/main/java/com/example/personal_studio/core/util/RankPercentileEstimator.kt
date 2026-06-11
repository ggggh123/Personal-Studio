package com.example.personal_studio.core.util

import kotlin.math.sqrt

/** 专业整体排名百分位估计结果。percent 均为「前 X%」语义,已夹 [1,99]。 */
data class RankPercentileEstimate(
    val pointPercent: Double,
    val loPercent: Double,
    val hiPercent: Double,
    val basisCount: Int,
)

/**
 * 用各课「专业中占 p%」(前 p%,小=好)逐课位次,z 空间学分加权聚合出整体专业百分位。
 * 点估计取 ρ=1 一致能力模型(保守);区间合成「各课分歧标准误」+「相关性敏感度(ρ_min)」。
 * 见 spec docs/superpowers/specs/2026-06-11-rank-percentile-estimator-design.md。
 */
object RankPercentileEstimator {
    private const val RHO_MIN = 0.5    // 相关性结构带下界
    private const val SE_K = 1.0       // 标准误倍数(≈68% 带)
    private const val EPS = 0.005      // 无专业人数时的连续性夹值
    private const val MIN_COURSES = 2  // 少于此不出估计

    /** courses: 各课 (credit, majorPercentile p∈[0,100], majorSize?)。不足/无效返回 null。 */
    fun estimate(courses: List<Triple<Double, Double, Int?>>): RankPercentileEstimate? {
        val valid = courses.filter { it.first > 0.0 && it.second in 0.0..100.0 }
        if (valid.size < MIN_COURSES) return null

        val w = valid.map { it.first }
        val z = valid.map { (_, p, n) ->
            val b = 1.0 - p / 100.0                       // 击败比例
            val lo = if (n != null && n >= 2) 1.0 / (2.0 * n) else EPS
            PeerGpaEstimator.invNormalCdf(b.coerceIn(lo, 1.0 - lo))
        }
        val sumW = w.sum()
        val sumW2 = w.sumOf { it * it }
        val sumWZ = w.indices.sumOf { w[it] * z[it] }
        val zStar = sumWZ / sumW                          // ρ=1 点估计

        val s2 = w.indices.sumOf { w[it] * (z[it] - zStar) * (z[it] - zStar) } / sumW
        val nEff = sumW * sumW / sumW2
        val se = sqrt(s2 / nEff)                          // 各课分歧标准误

        val denomRho = sqrt((1.0 - RHO_MIN) * sumW2 + RHO_MIN * sumW * sumW)
        val zRho = sumWZ / denomRho                       // 相关性敏感度(更极端一侧)

        val sign = if (zStar >= 0.0) 1.0 else -1.0
        val zLess = zStar - sign * SE_K * se              // 往 50% 收
        val zMore = zRho + sign * SE_K * se               // 往两端推

        fun pct(zz: Double) = (100.0 * (1.0 - PeerGpaEstimator.normalCdf(zz))).coerceIn(1.0, 99.0)
        val pPoint = pct(zStar)
        val pA = pct(zLess)
        val pB = pct(zMore)
        return RankPercentileEstimate(
            pointPercent = pPoint,
            loPercent = minOf(pA, pB),
            hiPercent = maxOf(pA, pB),
            basisCount = valid.size,
        )
    }
}
