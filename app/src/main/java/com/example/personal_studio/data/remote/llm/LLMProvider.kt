package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic LLM contract. Implementations:
 *  - [GeminiProvider] (P0, only one for now)
 *  - future: OpenAIProvider, ClaudeProvider (P6+ if desired)
 */
interface LLMProvider {
    /** Human-readable name for UI/debug. */
    val name: String

    /** Text-only streaming generation. */
    fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk>

    /** Multimodal streaming generation: image bytes + text prompt. */
    fun generateMultimodal(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk>

    /**
     * Non-streaming structured output. Implementations should instruct the model
     * to return JSON matching [jsonSchema] and return the raw JSON string.
     * Callers handle parsing + retry-on-failure policy.
     */
    suspend fun generateStructured(
        prompt: String,
        jsonSchema: String,
    ): String
}
