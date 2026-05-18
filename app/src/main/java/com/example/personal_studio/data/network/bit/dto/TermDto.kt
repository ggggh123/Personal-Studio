package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Single semester row, e.g. XNXQDM="2024-2025-2". */
@Serializable
data class TermDto(
    @SerialName("XNXQDM") val code: String,
    @SerialName("XNXQMC") val display: String? = null,
    @SerialName("DQXNXQ") val isCurrent: Int? = null,         // 1 = 当前学期
)

@Serializable
data class TermListResponse(val datas: Datas) {
    @Serializable data class Datas(
        @SerialName("dqxnxq") val dqxnxq: Rows? = null,
        @SerialName("xnxqcx") val xnxqcx: Rows? = null,
    )
    @Serializable data class Rows(val rows: List<TermDto>)
}
