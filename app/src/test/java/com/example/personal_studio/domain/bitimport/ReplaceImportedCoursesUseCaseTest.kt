package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReplaceImportedCoursesUseCaseTest {

    @Test fun `replaces 25-week window only`() = runBlocking {
        val dao = mockk<TimelineDao>(relaxed = true) {
            coEvery { deleteImportedInRange(any(), any()) } returns 17
        }
        val useCase = ReplaceImportedCoursesUseCase(dao)
        val anchor = LocalDate.of(2026, 2, 23)
        val zone = ZoneId.of("Asia/Shanghai")
        val newItems = listOf(stubItem(1L))

        val deleted = useCase.invoke(anchor, zone, newItems)

        assertEquals(17, deleted)
        val expectedStart = anchor.atStartOfDay(zone).toInstant().toEpochMilli()
        val expectedEnd = anchor.plusWeeks(25).atStartOfDay(zone).toInstant().toEpochMilli()
        coVerify(exactly = 1) { dao.deleteImportedInRange(expectedStart, expectedEnd) }
        coVerify(exactly = 1) { dao.insertAll(newItems) }
    }

    private fun stubItem(id: Long) = TimelineItemEntity(
        id = id, type = TimelineType.COURSE, title = "t", description = null,
        startAt = 0, endAt = null, isDone = false, doneAt = null,
        location = null, instructor = null, notes = null, credits = null,
        seriesId = id, periodIndex = 1, periodEndIndex = 1, weekdayCode = 1,
        weekIndexInSemester = 1, colorOverride = null,
        sourceType = TimelineSource.IMPORTED_PORTAL, sourceExternalId = null,
        kbEntryIdsJson = "[]", createdAt = 0, updatedAt = 0,
    )
}
