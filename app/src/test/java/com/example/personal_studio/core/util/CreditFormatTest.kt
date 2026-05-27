package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditFormatTest {
    @Test fun `0_25 keeps two decimals`() {
        assertEquals("0.25", CreditFormat.format(0.25)) // 形势与政策一类课
    }

    @Test fun `single-decimal values keep one decimal`() {
        assertEquals("1.5", CreditFormat.format(1.5))
        assertEquals("0.5", CreditFormat.format(0.5))
    }

    @Test fun `integer values render with one trailing zero`() {
        assertEquals("3.0", CreditFormat.format(3.0))
        assertEquals("4.0", CreditFormat.format(4.0))
        assertEquals("100.0", CreditFormat.format(100.0))
    }

    @Test fun `aggregated sums with 0_25 also keep precision`() {
        // 例如某学期含 4 门 0.25 学分课，term 总计应为 1.0 — 不该报"1"或截断
        assertEquals("29.25", CreditFormat.format(29.25))
        assertEquals("1.0", CreditFormat.format(1.0))
    }
}
