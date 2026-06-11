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
    val detailPath: String? = null,   // cjfx 成绩分析 相对路径(用于拉详情)
    val courseAvg: Double? = null,    // 该课平均分
    val classRankText: String? = null, // 本人成绩在班级中占(原文,如"前20%")
    val majorRankText: String? = null, // 本人成绩在专业中占(≈年级排名)
    val courseMaxScore: Double? = null,   // cjfx 最高分
    val courseStudyCount: Int? = null,    // cjfx 学习人数(用来按顺序统计估 σ)
    val classSize: Int? = null,           // cjfx 班级人数
    val majorSize: Int? = null,           // cjfx 专业人数
    val schoolRankText: String? = null,   // 本人成绩在所有学生中占(全校百分位)
)
