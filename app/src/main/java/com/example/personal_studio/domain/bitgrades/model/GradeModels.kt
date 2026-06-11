package com.example.personal_studio.domain.bitgrades.model

/** 单门课（领域层，UI 直接消费）。 */
data class GradeItem(
    val courseName: String,
    val courseCode: String,
    val credit: Double,
    val score: String,
    val gradePoint: Double?,
    val gradeLetter: String?,
    val category: String?,
    val attemptType: String,
    val isPass: Boolean,
    val courseAvg: Double? = null,        // 该课平均分
    val classRankText: String? = null,    // 本人成绩在班级中占(原文,如"前20%")
    val majorRankText: String? = null,    // 本人成绩在专业中占
    val id: Long = 0,                     // 来源记录主键（会话内稳定，用于选择参与计算）
    val courseMaxScore: Double? = null,   // cjfx 最高分
    val courseStudyCount: Int? = null,    // cjfx 学习人数(用于 Jensen 修正估 σ)
    val classSize: Int? = null,           // cjfx 班级人数
    val majorSize: Int? = null,           // cjfx 专业人数
    val schoolRankText: String? = null,   // 全校百分位
)

/** 排名（班级/专业），任一可缺。 */
data class TermRank(
    val classRank: Int?, val classTotal: Int?,
    val majorRank: Int?, val majorTotal: Int?,
) {
    /** 专业排名百分比（前 X%），数据不全时为 null。 */
    val majorPercentile: Int?
        get() = if (majorRank != null && majorTotal != null && majorTotal > 0)
            Math.ceil(majorRank * 100.0 / majorTotal).toInt() else null
}

data class TermGrades(
    val termCode: String,
    val termName: String,
    val courses: List<GradeItem>,
    val weightedGpa: Double,
    val avgScore: Double?,
    val rank: TermRank?,
)

/** 成绩单聚合根。terms 按 termCode 倒序（最新在前）。 */
data class GradeBook(
    val terms: List<TermGrades>,
    val overallGpa: Double,
    val totalCredits: Double,
    val overallAvgScore: Double?,
    val overallPeerAvgScore: Double? = null,
    val overallPeerAvgGpa: Double? = null,
    val overallRank: TermRank?,
) {
    val isEmpty: Boolean get() = terms.isEmpty()
}
