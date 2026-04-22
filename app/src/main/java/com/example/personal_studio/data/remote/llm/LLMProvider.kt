package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow

enum class LlmRole { SYSTEM, USER, ASSISTANT }

/** One turn of a conversation. [images] are raw JPEG/PNG bytes. */
data class LlmMessage(
    val role: LlmRole,
    val text: String,
    val images: List<ByteArray> = emptyList(),
)

/**
 * Provider-agnostic LLM contract.
 *
 * Implementations should only need to implement [generate] + [generateStructured].
 * The single-prompt convenience wrappers are provided here as default implementations
 * so callers like Settings "test ping" and the title generator don't need to build a
 * messages list.
 *
 * Implementations in this project:
 *  - [OpenRouterProvider] — OpenAI-compatible API (default)
 */
interface LLMProvider {
    /** Human-readable name for UI/debug. */
    val name: String

    /**
     * Multi-turn streaming generation — the primary entry point.
     *
     * Callers pass the full conversation history (including the current user turn)
     * so the model has context. Returns a cold [Flow] that emits [LlmChunk.Text]
     * deltas as the server streams them, terminated by exactly one [LlmChunk.Done]
     * on success or one [LlmChunk.Error] on failure.
     */
    fun generate(
        messages: List<LlmMessage>,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk>

    /** One-shot text generation. Default delegates to [generate]. */
    fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk> = generate(
        messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(LlmMessage(LlmRole.SYSTEM, systemPrompt))
            add(LlmMessage(LlmRole.USER, prompt))
        },
        temperature = temperature,
    )

    /** One-shot multimodal generation. Default delegates to [generate]. */
    fun generateMultimodal(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk> = generate(
        messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(LlmMessage(LlmRole.SYSTEM, systemPrompt))
            add(LlmMessage(LlmRole.USER, prompt, images = images))
        },
        temperature = temperature,
    )

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
