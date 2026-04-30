package com.example.personal_studio.feature.timeline.vm

import app.cash.turbine.test
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.timeline.AddCourseSeriesUseCase
import com.example.personal_studio.domain.timeline.CheckCourseConflictUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddCourseViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeTimelineRepository()
    private val semesterMock = mockk<SemesterPreferences>(relaxed = true)
    private val timetableMock = mockk<TimetablePreferences>(relaxed = true)

    @Before fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        // startDate / periods are non-suspend Flow properties — use `every`, not `coEvery`.
        every { semesterMock.startDate } returns MutableStateFlow<LocalDate?>(LocalDate.of(2026, 9, 7))
        every { timetableMock.periods } returns MutableStateFlow(DefaultTimetable.PERIODS)
    }
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test fun `save disabled when title blank`() = runTest {
        val vm = makeVm()
        vm.ui.test {
            assertFalse(awaitItem().saveEnabled)
            cancel()
        }
    }

    @Test fun `save enabled with all required fields`() = runTest {
        val vm = makeVm()
        vm.onTitleChange("高数")
        vm.onToggleWeekday(1)
        // UnconfinedTestDispatcher means the init-block + state updates already ran synchronously.
        // Assert directly on the latest state value rather than chasing turbine emissions.
        val snapshot = vm.ui.value
        assertTrue(snapshot.saveEnabled)
    }

    private fun makeVm(): AddCourseViewModel {
        val addUC = AddCourseSeriesUseCase(
            repo = repo,
            timetableProvider = { DefaultTimetable.PERIODS },
            zone = ZoneId.of("Asia/Shanghai"),
            nowProvider = { 0L },
        )
        return AddCourseViewModel(
            addCourse = addUC,
            checkConflict = CheckCourseConflictUseCase(repo),
            semester = semesterMock,
            timetable = timetableMock,
        )
    }
}
