package com.example.personal_studio.data.repository

import app.cash.turbine.test
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionSummaryTest {
    @Test fun `summary reports message count and last snippet`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("数学作业")
        repo.appendMessage(id, MessageRole.USER, "第一条", emptyList())
        repo.appendMessage(id, MessageRole.AI, "末条预览", emptyList())
        repo.observeSessionSummaries().test {
            val row = awaitItem().first { it.id == id }
            assertEquals(2, row.msgCount)
            assertEquals("末条预览", row.lastSnippet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `empty session has zero count and null snippet`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("空会话")
        repo.observeSessionSummaries().test {
            val row = awaitItem().first { it.id == id }
            assertEquals(0, row.msgCount)
            assertEquals(null, row.lastSnippet)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
