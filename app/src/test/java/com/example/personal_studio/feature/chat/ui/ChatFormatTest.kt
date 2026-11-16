package com.example.personal_studio.feature.chat.ui

import com.example.personal_studio.domain.model.ChatMessage
import com.example.personal_studio.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatFormatTest {
    private fun msg(ms: Long?, tokens: Int?) = ChatMessage(
        id = 1, sessionId = 1, role = MessageRole.AI, contentMarkdown = "x", attachedImagePaths = emptyList(),
        createdAt = 0, modelUsed = null, generationMs = ms, tokenCount = tokens,
    )

    @Test fun `answer footer shows time and tokens`() {
        assertEquals("2.3 秒 · 约 410 tokens", formatDoneLine(msg(2_300, 410)))
        assertEquals("15 秒 · 约 1200 tokens", formatDoneLine(msg(15_200, 1200)))
    }

    @Test fun `older answers without stats show nothing`() {
        assertNull(formatDoneLine(msg(null, 10)))
        assertNull(formatDoneLine(msg(1000, null)))
    }

    @Test fun `placeholder names read as 新对话`() {
        assertEquals("新对话", displayTitle("session #003"))
        assertEquals("新对话", displayTitle(null))
        assertEquals("极限", displayTitle("极限"))
    }

    @Test fun `snippets drop formulas and marks`() {
        val d = "$"
        assertEquals(
            "取 [公式]。当 [公式] 时 [公式] 成立",
            ChatListGrouping.plainSnippet("取 ${d}N$d。当 ${d}n > N$d 时\n$d$d|a_n - a| < \\varepsilon$d${d}成立"),
        )
        assertEquals("ε-N 定义 说的是", ChatListGrouping.plainSnippet("**ε-N 定义** 说的是"))
        assertEquals("标题 内容", ChatListGrouping.plainSnippet("## 标题\n> 内容"))
    }
}
