package com.example.personal_studio.core.util

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 估算"班级平均绩点"(peer benchmark GPA)。BIT GPA 公式 f(x)=4−3(100−x)²/1600 是凹函数,
 * 直接 f(平均分) 会因 Jensen 不等式系统性高估真实班级 E[f(score)]。这里用 cjfx 给到的
 * 最高分 + 学习人数 通过样本最大值的顺序统计量(E[max] ≈ μ + σ·Φ⁻¹(1−1/N))估 σ,
 * 再做二阶 Taylor 修正 E[f(X)] ≈ f(μ) + ½f''(μ)·σ² = f(μ) − 0.00188·σ²。
 *
 * 当 max 或 N 缺失时降级为"无修正"(等价于 Polish-B 的更弱版本——只用 mean)。
 */
object PeerGpaEstimator {

    /** 逆标准正态 CDF(Abramowitz-Stegun 26.2.23 有理逼近,精度 ~4.5e-4)。 */
    fun invNormalCdf(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "p must be in (0,1)" }
        if (p == 0.5) return 0.0
        val q = if (p < 0.5) p else 1.0 - p
        val t = sqrt(-2.0 * ln(q))
        val num = 2.515517 + 0.802853 * t + 0.010328 * t * t
        val den = 1.0 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t
        val z = t - num / den
        return if (p < 0.5) -z else z
    }

    /** 样本量 n 时,标准正态的最大值期望 ≈ Φ⁻¹(1 − 1/n)(对 n=2 用 1-1/(2n) 兜底)。 */
    fun expectedMaxZ(n: Int): Double {
        val nn = n.coerceAtLeast(2)
        return invNormalCdf(1.0 - 1.0 / nn)
    }

    /** 用 (μ, max, N) 估 σ。max≤μ 或参数缺失时返回 0(代表"我们不知道"——无修正)。 */
    fun estimateSigma(mean: Double, max: Double?, n: Int?): Double {
        if (max == null || n == null || n < 2 || max <= mean) return 0.0
        val z = expectedMaxZ(n)
        return if (z <= 0.0) 0.0 else (max - mean) / z
    }

    /** 二阶 Jensen 修正:E[f(X)] ≈ f(μ) − 0.00188·σ²。c = ½|f''| = 3/3200 = 9.375e-4
     *  但严格点 ½·(6/1600) = 3/1600 = 1.875e-3,这里用 1.875e-3 数值更准。
     *  夹回 [0, 4.0] 保证不越界。 */
    fun correctedGradePoint(mean: Double, sigma: Double): Double {
        val naive = BitGpaConverter.toGradePoint(mean)
        val corrected = naive - 1.875e-3 * sigma * sigma
        return corrected.coerceIn(0.0, 4.0)
    }
}
