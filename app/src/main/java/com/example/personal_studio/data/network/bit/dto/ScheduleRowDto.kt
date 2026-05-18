package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single row from BIT 教务's "查询学生综合学期课表" endpoint. One row can
 *  describe a course meeting weekly on a single weekday × period range; the
 *  SKZC bitmap tells us which semester weeks it actually meets on. */
@Serializable
data class ScheduleRowDto(
    @SerialName("XNXQDM") val xnxqdm: String? = null,
    @SerialName("KCM") val kcm: String? = null,
    @SerialName("SKJS") val skjs: String? = null,
    @SerialName("JASMC") val jasmc: String? = null,
    @SerialName("YPSJDD") val ypsjdd: String? = null,
    @SerialName("SKZC") val skzc: String? = null,
    @SerialName("SKXQ") val skxq: Int? = null,
    @SerialName("KSJC") val ksjc: Int? = null,
    @SerialName("JSJC") val jsjc: Int? = null,
    @SerialName("XXXQMC") val xxxqmc: String? = null,
    @SerialName("KCH") val kch: String? = null,
    @SerialName("XF") val xf: Float? = null,
    @SerialName("XS") val xs: Int? = null,
    @SerialName("KCXZDM_DISPLAY") val kcxzdmDisplay: String? = null,
    @SerialName("KCLBDM_DISPLAY") val kclbdmDisplay: String? = null,
    @SerialName("KKDWDM_DISPLAY") val kkdwdmDisplay: String? = null,
)

@Serializable
data class ScheduleResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxxszhxqkb") val cxxszhxqkb: Rows)
    @Serializable data class Rows(val rows: List<ScheduleRowDto>)
}
