package com.example.personal_studio.domain.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTitlesTest {
    @Test fun `placeholders are replaced, real titles are kept`() {
        assertTrue(ChatTitles.isPlaceholder(ChatTitles.NEW))
        assertTrue(ChatTitles.isPlaceholder("session #007"))
        assertTrue(ChatTitles.isPlaceholder("  "))
        assertFalse(ChatTitles.isPlaceholder("傅里叶变换的收敛条件"))
    }
}
