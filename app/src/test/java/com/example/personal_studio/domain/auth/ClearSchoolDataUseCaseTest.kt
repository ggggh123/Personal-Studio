package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesAnalysisCache
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearSchoolDataUseCaseTest {
    @Test fun `removes downloaded school data and its reminders, keeps manual items`() = runTest {
        val grades = mockk<GradesDao>(relaxed = true)
        val sources = listOf("IMPORTED_PORTAL", "IMPORTED_LEXUE", "IMPORTED_EXAM")
        val timeline = mockk<TimelineDao>(relaxed = true) {
            coEvery { idsBySources(sources) } returns listOf(4L, 9L)
        }
        val cancel = mockk<CancelRemindersUseCase>(relaxed = true)
        val cache = mockk<GradesAnalysisCache>(relaxed = true)
        val gradesPrefs = mockk<GradesSyncPrefs>(relaxed = true)
        val ddlPrefs = mockk<DdlSyncPrefs>(relaxed = true)
        val login = mockk<LoginPrefs>(relaxed = true)

        ClearSchoolDataUseCase(grades, timeline, cancel, cache, gradesPrefs, ddlPrefs, login).invoke()

        coVerify { grades.clearGrades() }
        coVerify { grades.clearRanks() }
        coVerify { cache.clear() }
        coVerify { cancel(4L) }
        coVerify { cancel(9L) }
        coVerify(exactly = 1) { timeline.deleteBySources(sources) }
        coVerify { gradesPrefs.setLastSeenSignature(emptySet()) }
        coVerify { ddlPrefs.setLastSeenUids(emptySet()) }
        coVerify { login.resetSyncHistory() }
    }
}
