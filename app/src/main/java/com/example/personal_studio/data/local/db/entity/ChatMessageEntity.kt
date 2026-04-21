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
    val attachedImagePath: String? = null,
    val sourceScanPageId: Long? = null,  // P2 wiring; stays null in P1
    val createdAt: Long,
)
