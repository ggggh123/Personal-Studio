package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankPercentileEstimatorTest {

    // (credit, majorPercentile p, majorSize)
    private fun c(p: Double, credit: Double = 3.0, n: Int? = 30) = Triple(credit, p, n)

    @Test fun `all median maps to about 50 percent`() {
        val r = RankPercentileEstimator.estimate(listOf(c(50.0), c(50.0), c(50.0)))!!
        assertEquals(50.0, r.pointPercent, 1.0)
        assertEquals(50.0, r.loPercent, 1.0)
        assertEquals(50.0, r.hiPercent, 1.0)
    }

    @Test fun `strong consistent yields small point within interval`() {
        val r = RankPercentileEstimator.estimate(listOf(c(10.0), c(12.0), c(15.0)))!!
        assertTrue("point ${r.pointPercent} should be <15", r.pointPercent < 15.0)
        assertTrue("point ${r.pointPercent} should be >5", r.pointPercent > 5.0)
        assertTrue(r.loPercent <= r.pointPercent)
        assertTrue(r.pointPercent <= r.hiPercent)
        assertEquals(3, r.basisCount)
    }

    @Test fun `smaller percentiles give a smaller point estimate`() {
        val strong = RankPercentileEstimator.estimate(listOf(c(5.0), c(8.0), c(6.0)))!!
        val weak = RankPercentileEstimator.estimate(listOf(c(40.0), c(45.0), c(42.0)))!!
        assertTrue(strong.pointPercent < weak.pointPercent)
    }

    @Test fun `fewer than two courses yields null`() {
        assertNull(RankPercentileEstimator.estimate(emptyList()))
        assertNull(RankPercentileEstimator.estimate(listOf(c(20.0))))
    }

    @Test fun `interval invariants and bounds hold`() {
        val r = RankPercentileEstimator.estimate(listOf(c(2.0), c(30.0), c(70.0), c(95.0)))!!
        assertTrue(r.loPercent in 1.0..99.0)
        assertTrue(r.hiPercent in 1.0..99.0)
        assertTrue(r.pointPercent in 1.0..99.0)
        assertTrue(r.loPercent <= r.pointPercent && r.pointPercent <= r.hiPercent)
    }

    @Test fun `perfect score does not blow up`() {
        val r = RankPercentileEstimator.estimate(listOf(c(0.0), c(20.0)))!!
        assertTrue(r.pointPercent.isFinite())
        assertTrue(r.pointPercent in 1.0..99.0)
    }

    @Test fun `higher credit on the stronger course pulls point lower`() {
        val heavyStrong = RankPercentileEstimator.estimate(
            listOf(c(p = 5.0, credit = 6.0), c(p = 60.0, credit = 1.0)),
        )!!
        val heavyWeak = RankPercentileEstimator.estimate(
            listOf(c(p = 5.0, credit = 1.0), c(p = 60.0, credit = 6.0)),
        )!!
        assertTrue(heavyStrong.pointPercent < heavyWeak.pointPercent)
    }
}
