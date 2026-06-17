package com.example.personal_studio.domain.chat

import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import com.example.personal_studio.data.repository.FakeChatRepository
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerateTitleUseCaseTest {

    @Test fun `non-empty title from LLM is trimmed and persisted`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession("session #001")
        repo.appendMessage(sid, MessageRole.USER, "极限怎么求?", emptyList())
        repo.appendMessage(sid, MessageRole.AI, "洛必达法则...", emptyList())
        repo.appendMessage(sid, MessageRole.USER, "谢谢", emptyList())

        val llm = FakeLLMProvider(textChunks = listOf("高数 · 极限 "))
        val useCase = GenerateTitleUseCase(repo, llm)

        val result = useCase(sid)
        assertEquals("高数 · 极限", result)
        assertEquals("高数 · 极限", repo.getSession(sid)?.title)
    }

    @Test fun `empty LLM output leaves session title unchanged`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession("session #042")
        repo.appendMessage(sid, MessageRole.USER, "hi", emptyList())

        val llm = FakeLLMProvider(textChunks = emptyList())
        val useCase = GenerateTitleUseCase(repo, llm)

        val result = useCase(sid)
        assertNull(result)
        assertEquals("session #042", repo.getSession(sid)?.title)
    }
}
