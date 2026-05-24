package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grade_entries",
    indices = [Index(value = ["termCode", "courseCode", "attemptType"], unique = true)],
)
data class GradeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termCode: String,
    val termName: String,
    val courseName: String,
    val courseCode: String,
    val credit: Double,
    val score: String,
    val gradePoint: Double?,
    val gradeLetter: String?,
    val category: String?,
    val attemptType: String,
    val isPass: Boolean,
    val fetchedAt: Long,
)
