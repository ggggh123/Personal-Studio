package com.example.personal_studio.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(tokenizer = "simple")
@Entity(tableName = "kb_entries_fts")
data class KbEntryFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val titleBigrams: String,
    val summaryBigrams: String,
    val standardizedQuestionBigrams: String,
)
