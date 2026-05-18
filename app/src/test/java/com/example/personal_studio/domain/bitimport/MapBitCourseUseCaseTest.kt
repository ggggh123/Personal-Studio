package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.network.bit.dto.ScheduleRowDto
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MapBitCourseUseCaseTest {

    private fun newUseCase(startDate: LocalDate?): MapBitCourseUseCase {
        val semester = mockk<SemesterPreferences> {
            coEvery { this@mockk.startDate } returns flowOf(startDate)
        }
        val timetable = mockk<TimetablePreferences> {
            coEvery { periods } returns flowOf(DefaultTimetable.PERIODS)
        }
        return MapBitCourseUseCase(semester, timetable, nowProvider = { 1_700_000_000_000L })
    }

    @Test fun `single-row 5-week course expands into 5 entities`() = runBlocking {
        val row = ScheduleRowDto(
            kcm = "高等数学A",
            skjs = "张三",
            jasmc = "信息楼301",
            xxxqmc = "良乡校区",
            skzc = "11111000000000000",
            skxq = 1, ksjc = 1, jsjc = 2, xf = 4.0f, kch = "08110510",
            kcxzdmDisplay = "必修", xs = 80, kkdwdmDisplay = "计算机学院",
        )
        val useCase = newUseCase(LocalDate.of(2026, 2, 23))
        val items = useCase.invoke(row, baseSeriesId = 100L, kchToSeries = mutableMapOf())
        assertEquals(5, items.size)
        assertTrue(items.all { it.type == TimelineType.COURSE })
        assertTrue(items.all { it.sourceType == TimelineSource.IMPORTED_PORTAL })
        assertTrue(items.all { it.title == "高等数学A" })
        assertTrue(items.all { it.instructor == "张三" })
        assertTrue(items.all { it.location == "良乡校区·信息楼301" })
        assertTrue(items.all { it.credits == 4.0f })
        assertTrue(items.all { it.sourceExternalId == "08110510" })
        assertTrue(items.all { it.notes == "必修 · 80学时 · 计算机学院" })
        assertEquals(listOf(1, 2, 3, 4, 5), items.map { it.weekIndexInSemester })
        assertTrue(items.all { it.seriesId == 100L })
    }

    @Test fun `missing campus uses just classroom`() = runBlocking {
        val row = ScheduleRowDto(
            kcm = "X", skzc = "1", skxq = 1, ksjc = 1, jsjc = 1,
            jasmc = "信息楼301", xxxqmc = null,
        )
        val useCase = newUseCase(LocalDate.of(2026, 2, 23))
        val items = useCase.invoke(row, baseSeriesId = 1L, kchToSeries = mutableMapOf())
        assertEquals("信息楼301", items.single().location)
    }

    @Test fun `missing both campus and classroom yields null location`() = runBlocking {
        val row = ScheduleRowDto(
            kcm = "X", skzc = "1", skxq = 1, ksjc = 1, jsjc = 1,
            jasmc = null, xxxqmc = null,
        )
        val useCase = newUseCase(LocalDate.of(2026, 2, 23))
        val items = useCase.invoke(row, baseSeriesId = 1L, kchToSeries = mutableMapOf())
        assertNull(items.single().location)
    }

    @Test fun `same KCH shares seriesId across two rows`() = runBlocking {
        val row1 = ScheduleRowDto(kcm = "X", skzc = "1", skxq = 1, ksjc = 1, jsjc = 1, kch = "K1")
        val row2 = ScheduleRowDto(kcm = "X", skzc = "1", skxq = 3, ksjc = 1, jsjc = 1, kch = "K1")
        val useCase = newUseCase(LocalDate.of(2026, 2, 23))
        val map = mutableMapOf<String, Long>()
        val a = useCase.invoke(row1, baseSeriesId = 100L, kchToSeries = map).single().seriesId
        val b = useCase.invoke(row2, baseSeriesId = 100L, kchToSeries = map).single().seriesId
        assertEquals(a, b)
    }

    @Test fun `missing required field throws`() = runBlocking {
        val row = ScheduleRowDto(kcm = null, skzc = "1", skxq = 1, ksjc = 1, jsjc = 1)
        val useCase = newUseCase(LocalDate.of(2026, 2, 23))
        try {
            useCase.invoke(row, baseSeriesId = 1L, kchToSeries = mutableMapOf())
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("KCM" in (e.message ?: ""))
        }
    }
}
