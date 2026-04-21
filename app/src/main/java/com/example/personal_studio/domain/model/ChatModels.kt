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
)
