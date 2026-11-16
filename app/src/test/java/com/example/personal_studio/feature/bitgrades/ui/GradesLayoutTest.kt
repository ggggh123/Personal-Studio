package com.example.personal_studio.feature.bitgrades.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GradesLayoutTest {
    @Test fun `term bars keep a stub and cap at 4_0`() {
        assertEquals(130f, barHeight(4.0), 0.01f)
        assertEquals(75f, barHeight(3.5), 0.01f)
        assertEquals(12f, barHeight(2.0), 0.01f)
    }

    @Test fun `term labels`() {
        assertEquals("25 秋", shortTermLabel("2025-2026-1"))
        assertEquals("26 春", shortTermLabel("2025-2026-2"))
        assertEquals("27 春", shortTermLabel("2026–2027 学年 第二学期"))
    }

    @Test fun `term codes read as words`() {
        assertEquals("2025–2026 学年 第一学期", fullTermName("2025-2026-1"))
        assertEquals("2025–2026 学年 第二学期", fullTermName("2025–2026 学年 第二学期"))
    }

    @Test fun `credits drop a trailing zero`() {
        assertEquals("3", fmtCredits(3.0))
        assertEquals("3.5", fmtCredits(3.5))
    }
}
