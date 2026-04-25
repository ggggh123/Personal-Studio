package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "kb_relations",
    primaryKeys = ["fromEntryId", "toEntryId"],
    foreignKeys = [
        ForeignKey(
            entity = KbEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KbEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["toEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("toEntryId")],
)
data class KbRelationEntity(
    val fromEntryId: Long,
    val toEntryId: Long,
    val weight: Float = 1f,
)
