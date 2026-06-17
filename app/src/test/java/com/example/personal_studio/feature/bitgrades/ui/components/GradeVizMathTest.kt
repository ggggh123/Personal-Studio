package com.example.personal_studio.feature.bitgrades.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeVizMathTest {

    @Test fun `parseTopPercent extracts top percent`() {
        assertEquals(20, parseTopPercent("前20%"))
        assertEquals(5, parseTopPercent("前5%"))
        assertEquals(20, parseTopPercent("20%"))
        assertEquals(100, parseTopPercent("前100%"))
    }

    @Test fun `parseTopPercent returns null for non-percent`() {
        assertNull(parseTopPercent(""))
        assertNull(parseTopPercent("优"))
        assertNull(parseTopPercent(null))
        assertNull(parseTopPercent("前0%"))
    }

    @Test fun `scoreToFloat parses numeric scores only`() {
        assertEquals(92f, scoreToFloat("92"))
        assertEquals(92.5f, scoreToFloat(" 92.5 "))
        assertNull(scoreToFloat("A"))
        assertNull(scoreToFloat("优"))
    }

    @Test fun `fmtScore drops trailing zero`() {
        assertEquals("98", fmtScore(98.0))
        assertEquals("92.5", fmtScore(92.5))
    }

    @Test fun `scoreAxisLo floors to ten and clamps at zero`() {
        assertEquals(70f, scoreAxisLo(78.0, 92f), 0.001f)
        assertEquals(40f, scoreAxisLo(55.0, 45f), 0.001f)
        assertEquals(0f, scoreAxisLo(3.0, 5f), 0.001f)
    }
}
