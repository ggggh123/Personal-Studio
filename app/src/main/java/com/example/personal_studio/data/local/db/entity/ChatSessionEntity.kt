package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [Index("updatedAt")],
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                  // "session #001" until AI auto-summarises
    val iconHint: String? = null,       // reserved for a future emoji/icon classifier
    val createdAt: Long,
    val updatedAt: Long,
)
