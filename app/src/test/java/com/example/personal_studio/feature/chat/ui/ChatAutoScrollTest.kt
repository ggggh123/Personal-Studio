package com.example.personal_studio.feature.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoScrollTest {

    @Test fun `auto-scrolls only when at bottom and user is not scrolling`() {
        // 在底部且用户没在手滑 → 抢滚动钉底(流式/新消息场景)
        assertTrue(shouldAutoScroll(atBottom = true, userScrolling = false))
        // 用户正在手滑(即便末条露头/在底部) → 不抢,避免打断滑动跳到底
        assertFalse(shouldAutoScroll(atBottom = true, userScrolling = true))
        // 不在底部(在看历史) → 不抢
        assertFalse(shouldAutoScroll(atBottom = false, userScrolling = false))
        assertFalse(shouldAutoScroll(atBottom = false, userScrolling = true))
    }
}
