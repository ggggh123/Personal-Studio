package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 新乐学(畅课)返回结构,只取用到的字段。时间均为 UTC ISO-8601("2026-11-24T15:55:00Z")。 */
@Serializable
data class EclassSemestersDto(val semesters: List<EclassSemesterDto> = emptyList())

@Serializable
data class EclassSemesterDto(
    val id: Long,
    val name: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    val sort: Int? = null,
)

@Serializable
data class EclassCoursesDto(
    val courses: List<EclassCourseDto> = emptyList(),
    val page: Int? = null,
    val pages: Int? = null,
)

@Serializable
data class EclassCourseDto(val id: Long, val name: String? = null)

@Serializable
data class EclassHomeworkPageDto(
    @SerialName("homework_activities") val items: List<EclassHomeworkDto> = emptyList(),
    val page: Int? = null,
    val pages: Int? = null,
)

@Serializable
data class EclassHomeworkDto(
    val id: Long,
    val title: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val deadline: String? = null,
    val submitted: Boolean? = null,
    val published: Boolean? = null,
    /** An object whose `description` is the teacher's HTML; kept loose because its shape varies. */
    val data: JsonElement? = null,
)
