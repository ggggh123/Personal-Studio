package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType

@Entity(
    tableName = "timeline_items",
    indices = [
        Index("startAt"),
        Index("seriesId"),
        Index(value = ["endAt", "type"]),
    ],
)
data class TimelineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val type: TimelineType,
    val title: String,
    val description: String? = null,

    val startAt: Long,
    val endAt: Long? = null,

    val isDone: Boolean = false,
    val doneAt: Long? = null,

    val location: String? = null,
    val instructor: String? = null,
    val notes: String? = null,
    /** Optional course credit hours (学分). Series-level: all rows of the same
     *  series share this value. Float to support 0.5 / 1.5 / 3.0 etc. */
    val credits: Float? = null,

    val seriesId: Long? = null,
    val periodIndex: Int? = null,
    val periodEndIndex: Int? = null,
    val weekdayCode: Int? = null,
    val weekIndexInSemester: Int? = null,

    val colorOverride: Int? = null,

    val sourceType: TimelineSource = TimelineSource.MANUAL,
    val sourceExternalId: String? = null,
    val kbEntryIdsJson: String = "[]",

    val createdAt: Long,
    val updatedAt: Long,
)
