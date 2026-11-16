package com.example.personal_studio.feature.chat.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 会话概览的日期分组(今天 / 近 7 天 / 更早)与行内相对时间。纯函数,泛型于"取 updatedAt"以便单测。 */
object ChatListGrouping {
    data class Group<T>(val label: String, val items: List<T>)

    fun <T> group(items: List<T>, now: Long, updatedAt: (T) -> Long): List<Group<T>> {
        if (items.isEmpty()) return emptyList()
        val startToday = startOfDay(now)
        val sorted = items.sortedByDescending(updatedAt)
        val weekStart = startToday - 6 * 86_400_000L
        val today = sorted.filter { updatedAt(it) >= startToday }
        val week = sorted.filter { updatedAt(it) in weekStart until startToday }
        val earlier = sorted.filter { updatedAt(it) < weekStart }
        return buildList {
            if (today.isNotEmpty()) add(Group("今天", today))
            if (week.isNotEmpty()) add(Group("近 7 天", week))
            if (earlier.isNotEmpty()) add(Group("更早", earlier))
        }
    }

    /** 今天→HH:mm;昨天→"昨天";一周内→"N 天前";更早→"M 月 d 日"。 */
    fun rowTime(ts: Long, now: Long): String {
        val startToday = startOfDay(now)
        if (ts >= startToday) return SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
        val days = ((startToday - startOfDay(ts)) / 86_400_000L).toInt()
        return when (days) {
            1 -> "昨天"
            in 2..6 -> "$days 天前"
            else -> SimpleDateFormat("M 月 d 日", Locale.US).format(Date(ts))
        }
    }

    /** One readable line from a Markdown + LaTeX message: formulas become "[公式]", marks and list bullets are dropped. */
    fun plainSnippet(markdown: String): String = markdown
        .replace(Regex("""\$\$[\s\S]*?\$\$"""), " [公式] ")
        .replace(Regex("""\\\[[\s\S]*?\\\]"""), " [公式] ")
        .replace(Regex("""\$[^$\n]+\$"""), "[公式]")
        .replace(Regex("""\\\(.+?\\\)"""), "[公式]")
        .replace(Regex("""(\*\*|__|`+|^#+\s*|^>\s*)""", RegexOption.MULTILINE), "")
        .replace(Regex("""^\s*([-*+]|\d+[.)])\s+""", RegexOption.MULTILINE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun startOfDay(ts: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
