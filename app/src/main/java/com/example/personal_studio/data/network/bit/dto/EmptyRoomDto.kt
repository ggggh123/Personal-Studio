package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 校区 ggzdpx.do 一行。 */
@Serializable
data class CampusDto(
    @SerialName("DM") val code: String? = null,
    @SerialName("MC") val name: String? = null,
)

@Serializable
data class CampusResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("ggzdpx") val ggzdpx: Rows)
    @Serializable data class Rows(val rows: List<CampusDto> = emptyList())
}

/** 教学楼 cxjxl.do 一行。 */
@Serializable
data class BuildingDto(
    @SerialName("JXLMC") val name: String? = null,
    @SerialName("JXLDM") val code: String? = null,
    @SerialName("XXXQDM") val campusCode: String? = null,
    @SerialName("XXXQDM_DISPLAY") val campusName: String? = null,
)

@Serializable
data class BuildingResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxjxl") val cxjxl: Rows)
    @Serializable data class Rows(val rows: List<BuildingDto> = emptyList())
}

/** 教室占用 cxkxjasqk.do 一行。ZYJC = 逗号分隔的「忙」节次串,可为 null(全天空)。 */
@Serializable
data class RoomOccupancyDto(
    @SerialName("JASMC") val roomName: String? = null,
    @SerialName("ZYJC") val busyPeriods: String? = null,
)

@Serializable
data class RoomOccupancyResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxkxjasqk") val cxkxjasqk: Rows)
    @Serializable data class Rows(val rows: List<RoomOccupancyDto> = emptyList())
}
