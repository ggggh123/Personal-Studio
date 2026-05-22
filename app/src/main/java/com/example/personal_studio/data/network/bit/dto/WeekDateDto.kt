package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One day from cxzkbrq.do (查正课表日期). When the request specifies `"ZC":"1"`
 * (week 1), the response lists all seven days of week 1 with their calendar
 * dates — so the entry with `XQ == 1` (Monday) is the semester's first day.
 *
 * Real response shape: `{"code":"0","data":[{"XQ":1,"RQ":"2026-02-23"}, ...]}`.
 */
@Serializable
data class WeekDateDto(
    @SerialName("XQ") val weekday: Int,
    @SerialName("RQ") val date: String,
)

@Serializable
data class WeekDateResponse(val data: List<WeekDateDto>)
