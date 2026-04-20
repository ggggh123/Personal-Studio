package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLLMProvider(
    private val textChunks: List<String> = listOf("hello, ", "world"),
) : LLMProvider {
    override val name: String = "fake"

    override fun generateText(prompt: String, systemPrompt: String?, temperature: Float): Flow<LlmChunk> = flow {
        textChunks.forEach { emit(LlmChunk.Text(it)) }
        emit(LlmChunk.Done(totalTokens = textChunks.sumOf { it.length }))
    }

    override fun generateMultimodal(prompt: String, images: List<ByteArray>, systemPrompt: String?): Flow<LlmChunk> =
        generateText(prompt, systemPrompt)

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String = "{}"
}
