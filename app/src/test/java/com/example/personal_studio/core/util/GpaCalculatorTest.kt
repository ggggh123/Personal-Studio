package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GpaCalculatorTest {
    @Test fun `weighted gpa ignores null-point courses`() {
        // (5学分×4.0 + 3×3.0) / (5+3) = 29/8 = 3.625；P/NP(null)不计
        val gpa = GpaCalculator.weightedGpa(listOf(5.0 to 4.0, 3.0 to 3.0, 2.0 to null))
        assertEquals(3.625, gpa, 0.0001)
    }
    @Test fun `empty or all-null yields zero`() {
        assertEquals(0.0, GpaCalculator.weightedGpa(emptyList()), 0.0001)
        assertEquals(0.0, GpaCalculator.weightedGpa(listOf(3.0 to null)), 0.0001)
    }

    // ---- What-if (M2) ----

    @Test fun `requiredAverageForTarget computes needed average for remaining credits`() {
        // 已修 90 学分、加权和 = 90×3.5=315；想把总 GPA 提到 3.7，还剩 30 学分。
        // 需要平均 = (3.7×120 − 315) / 30 = (444 − 315)/30 = 129/30 = 4.3 → 超过 4.0 上限 → 不可达
        val r = GpaCalculator.requiredAverageForTarget(
            doneCredits = 90.0, weightedSumDone = 315.0, remainingCredits = 30.0, targetGpa = 3.7,
        )
        assertEquals(4.3, r.value, 0.001)
        assertEquals(false, r.achievable)
    }

    @Test fun `requiredAverageForTarget achievable when within max`() {
        // 已修 60 学分 GPA 3.0 (和=180)，目标 3.2，剩 40 学分 →
        // 需要 = (3.2×100 − 180)/40 = (320−180)/40 = 140/40 = 3.5 ≤ 4.0 → 可达
        val r = GpaCalculator.requiredAverageForTarget(60.0, 180.0, 40.0, 3.2)
        assertEquals(3.5, r.value, 0.001)
        assertEquals(true, r.achievable)
    }

    @Test fun `requiredAverageForTarget with zero remaining is not achievable`() {
        val r = GpaCalculator.requiredAverageForTarget(60.0, 180.0, 0.0, 3.2)
        assertEquals(false, r.achievable) // 没有剩余学分可调整
    }

    @Test fun `project blends current and predicted into new gpa`() {
        // 当前 (4学分,3.0)=12；预测加 (2学分,4.0)=8 → (12+8)/(4+2)=20/6=3.333
        val gpa = GpaCalculator.project(
            currentItems = listOf(4.0 to 3.0),
            predicted = listOf(2.0 to 4.0),
        )
        assertEquals(3.3333, gpa, 0.001)
    }
}
