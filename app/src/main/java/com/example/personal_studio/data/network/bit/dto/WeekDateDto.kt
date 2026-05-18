package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Returned by POST /jwapp/sys/wdkbby/wdkbByController/cxzkbrq.do. Tells the
 *  client which semester week "today" sits in, plus the dates of every day in
 *  that week — enough to back-solve the week-1 Monday calendar date. */
@Serializable
data class WeekDateDto(
    @SerialName("XQ") val weekday: Int,
    @SerialName("RQ") val date: String,
)

@Serializable
data class WeekDateResponse(val data: List<WeekDateDto>, val currentWeek: Int? = null)
