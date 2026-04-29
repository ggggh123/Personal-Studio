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
 * Provider-agnostic LLM contract. Two structured-output entry points:
 *  - the **multi-message** overload supports vision + system/user history
 *  - the legacy single-prompt overload remains for back-compat (defaults to wrapping
 *    the prompt in a single USER message and delegating to the new overload)
 */
interface LLMProvider {
    val name: String

    fun generate(messages: List<LlmMessage>, temperature: Float = 0.7f): Flow<LlmChunk>

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
     * Multi-message structured output. Implementations should set
     * `response_format = json_object` (OpenAI-compatible) and assemble vision
     * content parts when an LlmMessage carries images.
     */
    suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
        temperature: Float = 0.3f,
    ): String

    /** Legacy single-prompt overload. Delegates to the multi-message form. */
    suspend fun generateStructured(prompt: String, jsonSchema: String): String =
        generateStructured(
            messages = listOf(LlmMessage(LlmRole.USER, prompt)),
            jsonSchema = jsonSchema,
        )
}
