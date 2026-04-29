package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test-only fake. Two modes:
 *  - For streaming chat tests: emits [textChunks] as Text chunks then Done.
 *  - For structured-output tests: pop responses from [structuredResponses] queue.
 *
 * Captures call history for assertion:
 *  - [lastMessages]: most recent generate() messages
 *  - [recordedMessages]: full history of generateStructured() calls (multi-message)
 *  - [recordedSchemas]: matching history of jsonSchema arguments
 */
class FakeLLMProvider(
    private val textChunks: List<String> = listOf("hello, ", "world"),
) : LLMProvider {
    override val name: String = "fake"

    /** Snapshot of the last [generate] call's messages, for chat-stream tests. */
    var lastMessages: List<LlmMessage> = emptyList()
        private set

    /** Each call to generateStructured pops the head; throws if empty. */
    val structuredResponses: ArrayDeque<Result<String>> = ArrayDeque()

    /** Captures the messages list passed to each generateStructured call. */
    val recordedMessages: MutableList<List<LlmMessage>> = mutableListOf()

    /** Captures the schema strings passed to each generateStructured call. */
    val recordedSchemas: MutableList<String> = mutableListOf()

    override fun generate(
        messages: List<LlmMessage>,
        temperature: Float,
    ): Flow<LlmChunk> = flow {
        lastMessages = messages
        textChunks.forEach { emit(LlmChunk.Text(it)) }
        emit(LlmChunk.Done(totalTokens = textChunks.sumOf { it.length }))
    }

    override suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
        temperature: Float,
    ): String {
        recordedMessages += messages
        recordedSchemas += jsonSchema
        val next = structuredResponses.removeFirstOrNull()
            ?: error("FakeLLMProvider: no queued response")
        return next.getOrThrow()
    }
}
