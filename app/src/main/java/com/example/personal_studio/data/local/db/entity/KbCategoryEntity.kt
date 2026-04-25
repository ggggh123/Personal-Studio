package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_categories",
    indices = [Index("name", unique = true)],
)
data class KbCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val name: String,
    val seeded: Boolean = false,
    val createdAt: Long,
)
