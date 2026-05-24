package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一门课在某学期的一次成绩记录。
 *
 * 唯一索引 `(termCode, courseCode, attemptType)` 是这条记录的自然身份键，让
 * `@Insert(REPLACE)` 在增量 upsert 时按"学期×课程×考试类型"去重（M4 出分提醒轮询
 * 用这条路径）。M1 的整库同步走 clear-then-insert（[ReplaceGradesUseCase]），不依赖它。
 *
 * ⚠ 该键的去重正确性依赖 courseCode(KCH) 非空——若真机发现 cjcx 不返回/改名 KCH，
 * 同一学期的多门课会塌缩到 (termCode, "", attemptType) 而被 REPLACE 覆盖，只剩最后一条。
 * Task 23 真机验证须确认 KCH 有值。
 */
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
