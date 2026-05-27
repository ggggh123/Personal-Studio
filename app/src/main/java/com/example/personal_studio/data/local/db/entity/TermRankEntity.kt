package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 单学期排名；总排名用保留键 termCode = "OVERALL"。 */
@Entity(tableName = "term_ranks")
data class TermRankEntity(
    @PrimaryKey val termCode: String,
    val termName: String,
    val weightedGpa: Double,
    val classRank: Int?,
    val classTotal: Int?,
    val majorRank: Int?,
    val majorTotal: Int?,
    val fetchedAt: Long,
)
