package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 某学期排名详情（"获取详细信息"）。字段名为假设，真机修正。 */
@Serializable
data class GradeRankDto(
    @SerialName("XNXQDM") val termCode: String? = null,
    @SerialName("BJPM") val classRank: Int? = null,
    @SerialName("BJZRS") val classTotal: Int? = null,
    @SerialName("ZYPM") val majorRank: Int? = null,
    @SerialName("ZYZRS") val majorTotal: Int? = null,
)

@Serializable
data class GradeRankResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxstupm") val cxstupm: Rows? = null)
    @Serializable data class Rows(val rows: List<GradeRankDto> = emptyList())
}
