package com.example.personal_studio.domain.chat

import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import com.example.personal_studio.data.repository.FakeChatRepository
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageUseCaseTest {

    @Test fun `happy path emits user-persisted then streams then AI-persisted`() = runTest {
        val repo = FakeChatRepository()
        val sessionId = repo.createSession("test")
        val llm = FakeLLMProvider(textChunks = listOf("hello ", "world"))
        val useCase = SendMessageUseCase(repo, llm)

        val chunks = useCase(
            sessionId = sessionId,
            userText = "hi",
            userImagePath = null,
        ).toList()

        assertEquals(4, chunks.size)
        assertTrue(chunks[0] is SendChunk.UserPersisted)
        assertTrue(chunks[1] is SendChunk.Delta && (chunks[1] as SendChunk.Delta).text == "hello ")
        assertTrue(chunks[2] is SendChunk.Delta && (chunks[2] as SendChunk.Delta).text == "world")
        assertTrue(chunks[3] is SendChunk.AiPersisted)

        val messages = repo.listMessages(sessionId)
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("hi", messages[0].contentMarkdown)
        assertEquals(MessageRole.AI, messages[1].role)
        assertEquals("hello world", messages[1].contentMarkdown)
    }

    @Test fun `error chunk halts stream and no AI message is persisted`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession("x")
        val llm = FakeLLMProviderThatErrors("fail")
        val useCase = SendMessageUseCase(repo, llm)

        val chunks = useCase(sid, "hi", null).toList()

        assertTrue(chunks.any { it is SendChunk.UserPersisted })
        assertTrue(chunks.any { it is SendChunk.Error && it.message == "fail" })
        assertTrue(chunks.none { it is SendChunk.AiPersisted })

        val messages = repo.listMessages(sid)
        assertEquals(1, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
    }
}

private class FakeLLMProviderThatErrors(private val errorMessage: String) :
    com.example.personal_studio.data.remote.llm.LLMProvider {
    override val name = "fake-err"
    override fun generate(
        messages: List<com.example.personal_studio.data.remote.llm.LlmMessage>,
        temperature: Float,
    ) = kotlinx.coroutines.flow.flow {
        emit(com.example.personal_studio.data.remote.llm.LlmChunk.Error(errorMessage, false))
    }
    override suspend fun generateStructured(
        messages: List<com.example.personal_studio.data.remote.llm.LlmMessage>,
        jsonSchema: String,
        temperature: Float,
    ): String = error("test fake — not used")
}
