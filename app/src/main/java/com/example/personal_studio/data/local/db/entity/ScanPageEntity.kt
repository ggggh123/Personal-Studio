package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_pages",
    foreignKeys = [ForeignKey(
        entity = ScanDocumentEntity::class,
        parentColumns = ["id"], childColumns = ["docId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("docId"),
        Index(value = ["docId", "ordinal"], unique = true),
    ],
)
data class ScanPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val ordinal: Int,
    val originalImagePath: String,
    val enhancedImagePath: String,
    val filter: String,         // "COLOR" | "GRAYSCALE" | "BW"
    val cornersJson: String?,
    val createdAt: Long,
)
