package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Single semester row from dqxnxq.do / xnxqcx.do.
 *
 * Real BIT response keys (verified via DoD logcat):
 *   "DM":"2025-2026-2"  ← term code (学期代码) — what getSchedule's XNXQDM wants
 *   "MC":"2025-2026学年 2学期"  ← display name
 *   "SFSY":1            ← 是否使用 (in-use flag; the dqxnxq endpoint returns
 *                          only the single current term, so this is always 1)
 *   "XNDM":"2025-2026"  ← academic year (unused)
 *
 * NOTE: the term entity uses `DM`/`MC`, NOT `XNXQDM`/`XNXQMC`. The latter are
 * the field names on the *schedule* rows (ScheduleRowDto) and the getSchedule
 * form parameter — different sub-system, same conceptual value.
 */
@Serializable
data class TermDto(
    @SerialName("DM") val code: String,
    @SerialName("MC") val display: String? = null,
    @SerialName("SFSY") val isCurrent: Int? = null,
)

@Serializable
data class TermListResponse(val datas: Datas) {
    @Serializable data class Datas(
        @SerialName("dqxnxq") val dqxnxq: Rows? = null,
        @SerialName("xnxqcx") val xnxqcx: Rows? = null,
    )
    @Serializable data class Rows(val rows: List<TermDto>)
}
