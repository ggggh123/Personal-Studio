package com.example.personal_studio.core.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test fun `gpa bounds round to half steps within 0_maxPoint`() {
        // 值 3.1..3.9 → 下界 floor 到 3.0、上界 ceil 到 4.0
        assertEquals(3.0 to 4.0, ChartMath.gpaBounds(listOf(3.1, 3.9), 4.0))
    }
    @Test fun `empty returns full scale`() {
        assertEquals(0.0 to 4.0, ChartMath.gpaBounds(emptyList(), 4.0))
    }
    @Test fun `single value still gives non-zero span`() {
        val (lo, hi) = ChartMath.gpaBounds(listOf(3.5), 4.0)
        assertEquals(true, hi > lo)
    }
}
