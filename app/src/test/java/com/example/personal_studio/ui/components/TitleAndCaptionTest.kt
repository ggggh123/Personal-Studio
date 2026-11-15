package com.example.personal_studio.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleAndCaptionTest {

    @Test
    fun `first subtitle line is the title and comment marks are dropped`() {
        assertEquals("课程管理" to "", titleAndCaption("courses", "# 课程管理"))
    }

    @Test
    fun `further lines join into the caption`() {
        assertEquals(
            "期中复习" to "deepseek-chat",
            titleAndCaption("chat", "期中复习\ndeepseek-chat"),
        )
        assertEquals(
            "讲义" to "3 页 · 11-15 10:00",
            titleAndCaption("scans/讲义", "讲义\n3 页 · 11-15 10:00"),
        )
    }

    @Test
    fun `route is the title when there is no subtitle`() {
        assertEquals("线性代数笔记" to "", titleAndCaption("线性代数笔记", null))
        assertEquals("edit" to "", titleAndCaption("edit", "  \n# "))
    }
}
