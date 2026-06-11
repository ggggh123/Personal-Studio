package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerGpaEstimatorTest {
    @Test fun `invNormalCdf basic landmarks`() {
        assertEquals(0.0, PeerGpaEstimator.invNormalCdf(0.5), 1e-6)
        assertEquals(1.960, PeerGpaEstimator.invNormalCdf(0.975), 5e-3)
        assertEquals(-1.960, PeerGpaEstimator.invNormalCdf(0.025), 5e-3)
        assertEquals(2.326, PeerGpaEstimator.invNormalCdf(0.99), 5e-3)
    }
    @Test fun `expectedMaxZ grows slowly with n`() {
        val z10 = PeerGpaEstimator.expectedMaxZ(10)
        val z1000 = PeerGpaEstimator.expectedMaxZ(1000)
        assertTrue("$z10 should be < $z1000", z10 < z1000)
        assertEquals(1.282, z10, 0.05)   // Φ⁻¹(0.9) ≈ 1.282
        assertEquals(3.090, z1000, 0.05) // Φ⁻¹(0.999) ≈ 3.090
    }
    @Test fun `estimateSigma matches order-statistic intuition`() {
        // N=1000 → expectedMaxZ ≈ 3.09 → σ ≈ (95-73.5)/3.09 ≈ 6.96
        val s = PeerGpaEstimator.estimateSigma(73.5, 95.0, 1000)
        assertEquals(6.96, s, 0.1)
    }
    @Test fun `estimateSigma returns 0 when params missing or invalid`() {
        assertEquals(0.0, PeerGpaEstimator.estimateSigma(80.0, null, 100), 0.0)
        assertEquals(0.0, PeerGpaEstimator.estimateSigma(80.0, 95.0, null), 0.0)
        assertEquals(0.0, PeerGpaEstimator.estimateSigma(80.0, 70.0, 100), 0.0) // max<=mean
    }
    @Test fun `correctedGradePoint applies Jensen lowering`() {
        // mean=78, σ=10 → naive f(78)=4-3*484/1600=3.0925; corrected −1.875e-3*100=−0.1875 → ~2.905
        val c = PeerGpaEstimator.correctedGradePoint(78.0, 10.0)
        assertEquals(2.905, c, 0.01)
        // sigma=0 → no correction (degrades to B/A semantics)
        assertEquals(BitGpaConverter.toGradePoint(78.0), PeerGpaEstimator.correctedGradePoint(78.0, 0.0), 1e-9)
    }

    @Test fun `normalCdf matches known values`() {
        assertEquals(0.5, PeerGpaEstimator.normalCdf(0.0), 1e-6)
        assertEquals(0.975, PeerGpaEstimator.normalCdf(1.96), 2e-3)
        assertEquals(0.8413, PeerGpaEstimator.normalCdf(1.0), 2e-3)
    }

    @Test fun `normalCdf is symmetric`() {
        assertEquals(1.0 - PeerGpaEstimator.normalCdf(1.3), PeerGpaEstimator.normalCdf(-1.3), 1e-9)
    }

    @Test fun `normalCdf inverts invNormalCdf`() {
        assertEquals(0.8, PeerGpaEstimator.normalCdf(PeerGpaEstimator.invNormalCdf(0.8)), 2e-3)
    }
}
