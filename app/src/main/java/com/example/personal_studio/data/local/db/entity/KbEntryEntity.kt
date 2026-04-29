package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class KbSourceType { CHAT_MESSAGE, CHAT_SESSION, SCAN }

@Entity(
    tableName = "kb_entries",
    indices = [
        Index("createdAt"),
        Index("categoryId"),
        Index("sourceType"),
    ],
)
data class KbEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long?,
    val sourceType: KbSourceType,
    val sourceChatMessageId: Long?,
    val sourceChatSessionId: Long?,
    val sourceScanPageId: Long?,
    val originalImagePath: String?,
    val standardizedQuestion: String?,
    val summaryMarkdown: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReviewedAt: Long? = null,
)
