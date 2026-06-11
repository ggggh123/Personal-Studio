package com.example.personal_studio.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.entity.ChatMessageEntity
import com.example.personal_studio.data.local.db.entity.ChatSessionEntity
import com.example.personal_studio.data.local.db.entity.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun `inserting a session and observing it round-trips`() = runTest {
        val id = db.chatSessionDao().insert(
            ChatSessionEntity(title = "test session", createdAt = 1000, updatedAt = 1000)
        )
        val fetched = db.chatSessionDao().getById(id)
        assertNotNull(fetched)
        assertEquals("test session", fetched!!.title)
    }

    @Test fun `deleting a session cascades to its messages`() = runTest {
        val sessionId = db.chatSessionDao().insert(
            ChatSessionEntity(title = "x", createdAt = 1, updatedAt = 1)
        )
        db.chatMessageDao().insert(
            ChatMessageEntity(
                sessionId = sessionId, role = MessageRole.USER,
                contentMarkdown = "hi", createdAt = 2,
            )
        )
        db.chatSessionDao().delete(sessionId)
        assertEquals(0, db.chatMessageDao().listBySession(sessionId).size)
    }

    @Test fun `message list orders by createdAt`() = runTest {
        val s = db.chatSessionDao().insert(
            ChatSessionEntity(title = "x", createdAt = 1, updatedAt = 1)
        )
        db.chatMessageDao().insert(ChatMessageEntity(sessionId = s, role = MessageRole.USER,
            contentMarkdown = "first", createdAt = 10))
        db.chatMessageDao().insert(ChatMessageEntity(sessionId = s, role = MessageRole.AI,
            contentMarkdown = "second", createdAt = 20))

        val list = db.chatMessageDao().listBySession(s)
        assertEquals(listOf("first", "second"), list.map { it.contentMarkdown })
    }

    @Test fun `session summaries aggregate msgCount and latest snippet`() = runTest {
        // 会话 A 有 2 条消息,会话 B 空 → 验证聚合 SQL 的 COUNT 与末条子查询
        val a = db.chatSessionDao().insert(
            ChatSessionEntity(title = "A", createdAt = 1, updatedAt = 1)
        )
        val b = db.chatSessionDao().insert(
            ChatSessionEntity(title = "B", createdAt = 2, updatedAt = 2)
        )
        db.chatMessageDao().insert(ChatMessageEntity(sessionId = a, role = MessageRole.USER,
            contentMarkdown = "older", createdAt = 10))
        db.chatMessageDao().insert(ChatMessageEntity(sessionId = a, role = MessageRole.AI,
            contentMarkdown = "newest", createdAt = 20))

        val rows = db.chatSessionDao().observeSessionSummaries().first()
        val rowA = rows.first { it.id == a }
        val rowB = rows.first { it.id == b }
        assertEquals(2, rowA.msgCount)
        assertEquals("newest", rowA.lastSnippet)
        assertEquals(0, rowB.msgCount)
        assertNull(rowB.lastSnippet)
    }
}
