package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单门课成绩行（cjcx 成绩查询 app）。字段名为假设，真机 DoD 验证后修正。
 * score 用 String：BIT 成绩可能是数字"92"或等级"优"/"通过"。若真机发现 CJ 返回
 * 的是 JSON number 而非 string，改用自定义序列化器或 Double（见 Task 23）。
 */
@Serializable
data class GradeRowDto(
    @SerialName("XNXQDM") val termCode: String? = null,
    @SerialName("XNXQMC") val termName: String? = null,
    @SerialName("KCM") val courseName: String? = null,
    @SerialName("KCH") val courseCode: String? = null,
    @SerialName("XF") val credit: Double? = null,
    @SerialName("CJ") val score: String? = null,
    @SerialName("JD") val gradePoint: Double? = null,
    @SerialName("DJCJMC") val gradeLetter: String? = null,
    @SerialName("KCXZMC") val category: String? = null,
    @SerialName("CXCKDM_DISPLAY") val attemptType: String? = null,
)

@Serializable
data class GradeListResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxstuxqcj") val cxstuxqcj: Rows? = null)
    @Serializable data class Rows(
        val rows: List<GradeRowDto> = emptyList(),
        @SerialName("totalSize") val totalSize: Int? = null,
    )
}
