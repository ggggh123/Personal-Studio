package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamRowDto(
    @SerialName("KCM") val kcm: String? = null,
    @SerialName("YWKCM") val ywkcm: String? = null,
    @SerialName("KCH") val kch: String? = null,
    @SerialName("KSSJMS") val kssjms: String? = null,
    @SerialName("KSRQ") val ksrq: String? = null,
    @SerialName("JASMC") val jasmc: String? = null,
    @SerialName("ZWH") val zwh: String? = null,
    @SerialName("ZJJSXM") val zjjsxm: String? = null,
    @SerialName("KSMC") val ksmc: String? = null,
    @SerialName("XNXQDM") val xnxqdm: String? = null,
)

@Serializable
data class ExamResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxxsksap") val cxxsksap: Rows)
    @Serializable data class Rows(val rows: List<ExamRowDto> = emptyList())
}
