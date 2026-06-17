package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole { USER, AI, SYSTEM }

@Entity(
    tableName = "chat_messages",
    indices = [Index("sessionId"), Index("createdAt")],
    foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: MessageRole,
    val contentMarkdown: String,
    // 存「图路径 JSON 数组」字符串(单条消息可多图,≤6);向后兼容旧的裸单路径。
    // 编解码见 data/repository/ChatImagePaths.kt。列名/类型不变,故不需 Room 迁移。
    val attachedImagePath: String? = null,
    val sourceScanPageId: Long? = null,  // P2 wiring; stays null in P1
    val createdAt: Long,
    // Only populated for AI messages: wall-clock duration from first streamed delta
    // to the `done` event, and an approximation of the token count based on the
    // content's codepoints. Both null for user / system messages.
    val generationMs: Long? = null,
    val tokenCount: Int? = null,
    /**
     * Provider-reported identifier for the model that produced this AI reply
     * (e.g. `google/gemini-2.0-flash-exp:free`). Locked in at persistence time so
     * swapping models later doesn't retroactively relabel old replies. Null for
     * non-AI messages, legacy rows, or providers that don't report it.
     */
    val modelUsed: String? = null,
)
