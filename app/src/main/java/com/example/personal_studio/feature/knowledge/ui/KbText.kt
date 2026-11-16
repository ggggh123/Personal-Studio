package com.example.personal_studio.feature.knowledge.ui

import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.feature.chat.ui.ChatListGrouping
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wording for knowledge-base rows and the entry page; pure so it can be unit tested. */
object KbText {
    /** "数学 · 3 天前"; entries without a category read "未分类". */
    fun meta(entry: KbEntry, now: Long): String =
        "${entry.categoryName?.takeIf { it.isNotBlank() } ?: "未分类"} · ${ChatListGrouping.rowTime(entry.createdAt, now)}"

    /** One readable line for the row: the question for a mistake, otherwise the summary. */
    fun snippet(entry: KbEntry): String {
        val source = entry.standardizedQuestion?.takeIf { it.isNotBlank() } ?: entry.summaryMarkdown
        return ChatListGrouping.plainSnippet(source)
    }

    /** "笔记 · 2026 年 11 月 3 日存入" for the entry page caption. */
    fun detailCaption(entry: KbEntry): String {
        val kind = if (entry.isMistake) "错题" else "笔记"
        val date = SimpleDateFormat("yyyy 年 M 月 d 日", Locale.US).format(Date(entry.createdAt))
        return "${entry.categoryName?.takeIf { it.isNotBlank() } ?: "未分类"} · $kind · ${date}存入"
    }
}
