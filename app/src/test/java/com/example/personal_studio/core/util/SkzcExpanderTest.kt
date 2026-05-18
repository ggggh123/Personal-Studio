package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkzcExpanderTest {

    @Test fun `all ones expands to 1 through N`() {
        assertEquals(listOf(1, 2, 3, 4, 5), SkzcExpander.expand("11111"))
    }

    @Test fun `interleaved ones return the correct indices`() {
        // Single-week pattern (3rd, 5th, 7th weeks)
        assertEquals(listOf(3, 5, 7), SkzcExpander.expand("0010101"))
    }

    @Test fun `typical 16-week first-5-weeks course`() {
        assertEquals(listOf(1, 2, 3, 4, 5), SkzcExpander.expand("1111100000000000"))
    }

    @Test fun `all zeros yields empty list`() {
        assertEquals(emptyList<Int>(), SkzcExpander.expand("0000"))
    }

    @Test fun `empty string yields empty list`() {
        assertEquals(emptyList<Int>(), SkzcExpander.expand(""))
    }

    @Test fun `non-binary character throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkzcExpander.expand("110210")
        }
    }

    @Test fun `length over 30 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkzcExpander.expand("1".repeat(31))
        }
    }
}
