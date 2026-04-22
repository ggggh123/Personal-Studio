package com.example.personal_studio.data.remote.llm

/**
 * Stream chunks returned by [LLMProvider]. A single generation produces zero or more
 * [Text] values followed by exactly one [Done] or one [Error].
 */
sealed interface LlmChunk {
    data class Text(val delta: String) : LlmChunk
    /**
     * Terminal chunk on success. [model] is the provider-reported identifier for the
     * model that actually served the response (so callers can record it alongside the
     * persisted message and later switching the active model doesn't retroactively
     * relabel historical replies). `null` if the provider doesn't know or the test
     * fake doesn't care.
     */
    data class Done(val totalTokens: Int?, val model: String? = null) : LlmChunk
    data class Error(val message: String, val retryable: Boolean) : LlmChunk
}
