package com.example.personal_studio.feature.knowledge.ui

import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.feature.scanner.library.scanDocMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class KbTextTest {
    private fun noon(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, 12, 0)
    }.timeInMillis

    private fun entry(category: String?, question: String?, summary: String = "摘要 **重点**", at: Long = noon(2026, 11, 13)) = KbEntry(
        id = 1, title = "T", categoryId = null, categoryName = category, source = KbSource.CHAT_SESSION,
        sourceChatMessageId = null, sourceChatSessionId = 1, sourceScanPageId = null, originalImagePath = null,
        standardizedQuestion = question, summaryMarkdown = summary, createdAt = at, updatedAt = at,
    )

    @Test fun `row meta names the category and when it was saved`() {
        val now = noon(2026, 11, 16)
        assertEquals("数学 · 3 天前", KbText.meta(entry("数学", null), now))
        assertEquals("未分类 · 3 天前", KbText.meta(entry(null, null), now))
        assertEquals("未分类 · 3 天前", KbText.meta(entry(" ", null), now))
    }

    @Test fun `mistakes preview the question, notes the summary`() {
        assertEquals("证明 [公式]", KbText.snippet(entry("数学", "证明 \$x=1\$")))
        assertEquals("摘要 重点", KbText.snippet(entry("数学", null)))
        assertEquals("摘要 重点", KbText.snippet(entry("数学", " ")))
    }

    @Test fun `entry caption says kind and date`() {
        assertEquals("数学 · 错题 · 2026 年 11 月 13 日存入", KbText.detailCaption(entry("数学", "q")))
        assertTrue(KbText.detailCaption(entry(null, null)).startsWith("未分类 · 笔记"))
    }

    @Test fun `scan meta follows the sort order`() {
        val now = noon(2026, 11, 16)
        val doc = ScanDocumentSummary(1, "讲义", createdAt = noon(2026, 11, 1), updatedAt = noon(2026, 11, 15), pageCount = 3, coverPageId = null, coverPath = null)
        assertEquals("3 页 · 11 月 1 日", scanDocMeta(doc, SortMode.TIME_DESC, now))
        assertEquals("3 页 · 昨天", scanDocMeta(doc, SortMode.RECENT_UPDATED, now))
    }
}
