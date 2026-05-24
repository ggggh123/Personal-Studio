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
}
