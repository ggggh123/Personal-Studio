package com.example.personal_studio.data.remote.llm

/**
 * Stream chunks returned by [LLMProvider]. A single generation produces zero or more
 * [Text] values followed by exactly one [Done] or one [Error].
 */
sealed interface LlmChunk {
    data class Text(val delta: String) : LlmChunk
    data class Done(val totalTokens: Int?) : LlmChunk
    data class Error(val message: String, val retryable: Boolean) : LlmChunk
}
