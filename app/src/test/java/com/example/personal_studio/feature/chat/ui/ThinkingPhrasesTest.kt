package com.example.personal_studio.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingPhrasesTest {
    @Test fun `has 24 phrases`() {
        assertEquals(24, THINKING_PHRASES.size)
    }

    @Test fun `phrase advances every 3 seconds`() {
        val off = 0
        assertEquals(THINKING_PHRASES[0], thinkingPhraseAt(0, off))
        assertEquals(THINKING_PHRASES[0], thinkingPhraseAt(2, off))
        assertEquals(THINKING_PHRASES[1], thinkingPhraseAt(3, off))
        assertEquals(THINKING_PHRASES[1], thinkingPhraseAt(5, off))
        assertEquals(THINKING_PHRASES[2], thinkingPhraseAt(6, off))
    }

    @Test fun `offset shifts start and wraps modulo size`() {
        assertEquals(THINKING_PHRASES[5], thinkingPhraseAt(0, 5))
        // offset == size wraps back to 0
        assertEquals(THINKING_PHRASES[0], thinkingPhraseAt(0, THINKING_PHRASES.size))
        // last index then advance wraps to first
        assertEquals(THINKING_PHRASES[0], thinkingPhraseAt(3, THINKING_PHRASES.size - 1))
    }
}
