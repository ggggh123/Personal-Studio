package com.example.personal_studio.domain.model

enum class MessageRole { USER, AI, SYSTEM }

data class ChatSession(
    val id: Long,
    val title: String,
    val iconHint: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: MessageRole,
    val contentMarkdown: String,
    val attachedImagePath: String?,
    val createdAt: Long,
    /** Wall-clock streaming duration for AI turns, in ms. `null` for non-AI messages. */
    val generationMs: Long? = null,
    /** Approximate token count for AI turns (CJK-aware). `null` for non-AI messages. */
    val tokenCount: Int? = null,
    /**
     * Model id that actually produced this reply (e.g. `openai/gpt-4o-mini`). Set by
     * [SendMessageUseCase] at persistence time so the UI labels stay accurate even
     * after the user later switches the active model. Null for non-AI / legacy rows.
     */
    val modelUsed: String? = null,
)

/** 会话概览富行:会话本体 + 消息数 + 末条预览。 */
data class ChatSessionSummary(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val msgCount: Int,
    val lastSnippet: String?,
)
