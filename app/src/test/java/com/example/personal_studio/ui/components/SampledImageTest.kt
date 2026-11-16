package com.example.personal_studio.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SampledImageTest {
    @Test fun `samples down while the shorter side still covers the target`() {
        assertEquals(8, sampleSizeFor(4000, 3000, 300))   // 3000 / 8 = 375 ≥ 300; /16 = 187 < 300
        assertEquals(1, sampleSizeFor(800, 600, 600))
        assertEquals(2, sampleSizeFor(4000, 3000, 1000))
    }

    @Test fun `bad sizes never sample`() {
        assertEquals(1, sampleSizeFor(0, 3000, 300))
        assertEquals(1, sampleSizeFor(4000, 3000, 0))
        assertEquals(1, sampleSizeFor(200, 100, 300))     // never upscales
    }
}
