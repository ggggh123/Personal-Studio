package com.example.personal_studio.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListGroupingTest {
    // now = 2024-06-15 12:00 固定时刻(本地)，用相对偏移构造时间戳避免时区脆弱。
    private val now = 1_718_424_000_000L

    @Test fun `rowTime today shows clock, yesterday shows 昨天, older shows md`() {
        assertEquals("HH:mm 形态", 5, ChatListGrouping.rowTime(now - 60_000, now).length) // "11:59" 长度5
        assertEquals("昨天", ChatListGrouping.rowTime(now - 26 * 3_600_000L, now))
        assertEquals("3天前", ChatListGrouping.rowTime(now - 3 * 86_400_000L, now))
    }

    @Test fun `group splits into 今天 and 更早 in order`() {
        val groups = ChatListGrouping.group(
            listOf(now - 3_600_000L, now - 3 * 86_400_000L, now - 60_000L), now,
        ) { it }
        assertEquals(listOf("今天", "更早"), groups.map { it.label })
        assertEquals(2, groups[0].items.size)   // 两条今天
        assertEquals(1, groups[1].items.size)   // 一条更早
    }

    @Test fun `empty yields no groups`() {
        assertEquals(emptyList<ChatListGrouping.Group<Long>>(), ChatListGrouping.group(emptyList<Long>(), now) { it })
    }
}
