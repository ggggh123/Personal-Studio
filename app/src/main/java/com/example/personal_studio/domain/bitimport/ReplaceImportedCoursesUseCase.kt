package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Conflict strategy B (per spec §5.4): wipe all IMPORTED_PORTAL rows whose
 * startAt lies in [semesterStart, semesterStart + 25 weeks), then insert the
 * new rows. MANUAL rows in the same range are untouched.
 *
 * The 25-week window comfortably covers any normal semester length (16-20
 * weeks typical, with a few weeks of buffer).
 */
class ReplaceImportedCoursesUseCase @Inject constructor(
    private val dao: TimelineDao,
) {
    suspend operator fun invoke(
        semesterStart: LocalDate,
        zone: ZoneId,
        newItems: List<TimelineItemEntity>,
    ): Int {
        val startEpoch = semesterStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endEpoch = semesterStart.plusWeeks(25).atStartOfDay(zone).toInstant().toEpochMilli()
        val deleted = dao.deleteImportedInRange(startEpoch, endEpoch)
        dao.insertAll(newItems)
        return deleted
    }
}
