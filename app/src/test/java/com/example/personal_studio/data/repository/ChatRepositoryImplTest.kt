package com.example.personal_studio.data.repository

import app.cash.turbine.test
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeChatRepositoryTest {

    @Test fun `new session appears in observeSessions`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession(initialTitle = "test")

        repo.observeSessions().test {
            val sessions = awaitItem()
            assertEquals(1, sessions.size)
            assertEquals(id, sessions[0].id)
            assertEquals("test", sessions[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `appendMessage updates observeMessages for that session`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession(initialTitle = "x")
        repo.appendMessage(sid, MessageRole.USER, "hi", emptyList())

        repo.observeMessages(sid).test {
            val msgs = awaitItem()
            assertEquals(1, msgs.size)
            assertEquals("hi", msgs[0].contentMarkdown)
            assertEquals(MessageRole.USER, msgs[0].role)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `appendMessage bumps session updatedAt`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession(initialTitle = "x")

        var before: List<com.example.personal_studio.domain.model.ChatSession> = emptyList()
        repo.observeSessions().test {
            before = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        Thread.sleep(5)
        repo.appendMessage(sid, MessageRole.USER, "hi", emptyList())

        var after: List<com.example.personal_studio.domain.model.ChatSession> = emptyList()
        repo.observeSessions().test {
            after = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(after[0].updatedAt > before[0].updatedAt)
    }

    @Test fun `countSessions returns current number of sessions`() = runTest {
        val repo = FakeChatRepository()
        assertEquals(0, repo.countSessions())
        repo.createSession("a")
        repo.createSession("b")
        assertEquals(2, repo.countSessions())
    }
}
