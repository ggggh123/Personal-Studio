package com.example.personal_studio.core.charts

import kotlin.math.ceil
import kotlin.math.floor

object ChartMath {
    /** 给 GPA 类序列算"好看"的 y 轴上下界（0.5 取整，夹在 [0, maxPoint]）。 */
    fun gpaBounds(values: List<Double>, maxPoint: Double = 4.0): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to maxPoint
        val rawLo = values.min()
        val rawHi = values.max()
        var lo = (floor(rawLo * 2) / 2).coerceAtLeast(0.0)
        var hi = (ceil(rawHi * 2) / 2).coerceAtMost(maxPoint)
        if (hi <= lo) { lo = (lo - 0.5).coerceAtLeast(0.0); hi = (lo + 1.0).coerceAtMost(maxPoint) }
        return lo to hi
    }
}
