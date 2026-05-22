package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.core.util.SkzcExpander
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.dto.ScheduleRowDto
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Converts one BIT ScheduleRow into N TimelineItemEntities — one per `'1'` in
 * its SKZC bitmap. Field mapping decisions are documented in the spec §5.2.
 *
 * `baseSeriesId` should be `dao.maxSeriesId() + 1` at the start of an import
 * session. `kchToSeries` is a session-scoped scratchpad so rows with the same
 * KCH (same course, multiple weekly slots) share a seriesId.
 *
 * `nowProvider` is an explicit constructor parameter (NOT a default-value
 * lambda) because Hilt + Dagger do not honour Kotlin default values for
 * `() -> Long` lambdas — documented gotcha from P4.
 */
class MapBitCourseUseCase @Inject constructor(
    private val semesterPrefs: SemesterPreferences,
    private val timetablePrefs: TimetablePreferences,
    private val nowProvider: () -> Long,
) {

    suspend operator fun invoke(
        row: ScheduleRowDto,
        baseSeriesId: Long,
        kchToSeries: MutableMap<String, Long>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineItemEntity> {
        val title = row.kcm ?: throw IllegalArgumentException("ScheduleRow missing KCM")
        val weekday = row.skxq ?: throw IllegalArgumentException("ScheduleRow missing SKXQ")
        val periodStart = row.ksjc ?: throw IllegalArgumentException("ScheduleRow missing KSJC")
        val periodEnd = row.jsjc ?: periodStart
        val skzc = row.skzc ?: throw IllegalArgumentException("ScheduleRow missing SKZC")
        val weeks = SkzcExpander.expand(skzc)
        if (weeks.isEmpty()) return emptyList()

        val semesterStart = semesterPrefs.startDate.first()
            ?: throw IllegalStateException("SemesterPreferences.startDate must be resolved before mapping")
        val periods = timetablePrefs.periods.first()

        val location = listOfNotNull(
            row.xxxqmc?.takeIf { it.isNotBlank() },
            row.jasmc?.takeIf { it.isNotBlank() },
        ).joinToString("·").ifBlank { null }

        val notes = listOfNotNull(
            row.kcxzdmDisplay?.takeIf { it.isNotBlank() },
            row.xs?.let { "${it}学时" },
            row.kkdwdmDisplay?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null }

        val seriesId = row.kch?.let { kch ->
            kchToSeries.getOrPut(kch) { baseSeriesId + kchToSeries.size }
        } ?: (baseSeriesId + kchToSeries.size + 1).also { kchToSeries["__anon_${it}"] = it }

        val now = nowProvider()
        return weeks.map { weekIdx ->
            val (startAt, endAt) = computeEpoch(
                semesterStart, weekIdx, weekday, periodStart, periodEnd, periods, zone,
            )
            TimelineItemEntity(
                id = 0,
                type = TimelineType.COURSE,
                title = title,
                description = null,
                startAt = startAt,
                endAt = endAt,
                isDone = false,
                doneAt = null,
                location = location,
                instructor = row.skjs?.takeIf { it.isNotBlank() },
                notes = notes,
                credits = row.xf,
                seriesId = seriesId,
                periodIndex = periodStart,
                periodEndIndex = periodEnd,
                weekdayCode = weekday,
                weekIndexInSemester = weekIdx,
                colorOverride = null,
                sourceType = TimelineSource.IMPORTED_PORTAL,
                sourceExternalId = row.kch,
                kbEntryIdsJson = "[]",
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun computeEpoch(
        semesterStart: LocalDate,
        weekIdx: Int,
        weekday: Int,
        periodStart: Int,
        periodEnd: Int,
        periods: List<TimetablePeriod>,
        zone: ZoneId,
    ): Pair<Long, Long> {
        val date = semesterStart.plusWeeks((weekIdx - 1).toLong())
            .plusDays((weekday - 1).toLong())
        val startTime = periods.firstOrNull { it.index == periodStart }
            ?.let { LocalTime.parse(it.startHHmm) }
            ?: throw IllegalStateException("Timetable missing period $periodStart")
        val endTime = periods.firstOrNull { it.index == periodEnd }
            ?.let { LocalTime.parse(it.endHHmm) }
            ?: throw IllegalStateException("Timetable missing period $periodEnd")
        val startAt = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        val endAt = date.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
        return startAt to endAt
    }
}
