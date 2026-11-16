package com.example.personal_studio.feature.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoScrollTest {

    @Test fun `sending a message jumps to the newest turn`() {
        assertTrue(sentNewMessage(seen = 3, latest = 7))
    }

    @Test fun `loading, reopening or rotating does not jump`() {
        // 首次加载:还没记录过
        assertFalse(sentNewMessage(seen = null, latest = 7))
        // 旋转屏幕:记录值恢复,最新一条没变
        assertFalse(sentNewMessage(seen = 7, latest = 7))
        // 还没有用户消息
        assertFalse(sentNewMessage(seen = 7, latest = null))
    }
}
