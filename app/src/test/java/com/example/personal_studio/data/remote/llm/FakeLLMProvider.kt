package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test-only fake. Ignores the conversation it's given and emits a preset stream.
 * Optionally captures the most recent `messages` list so tests can assert what
 * was sent.
 */
class FakeLLMProvider(
    private val textChunks: List<String> = listOf("hello, ", "world"),
) : LLMProvider {
    override val name: String = "fake"

    /** Snapshot of the last [generate] call's messages, for test assertions. */
    var lastMessages: List<LlmMessage> = emptyList()
        private set

    override fun generate(
        messages: List<LlmMessage>,
        temperature: Float,
    ): Flow<LlmChunk> = flow {
        lastMessages = messages
        textChunks.forEach { emit(LlmChunk.Text(it)) }
        emit(LlmChunk.Done(totalTokens = textChunks.sumOf { it.length }))
    }

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String = "{}"
}
