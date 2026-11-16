package com.example.personal_studio.feature.timeline.vm

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.local.datastore.SemesterPreferences
import com.example.personal_studio.data.local.datastore.TimetablePreferences
import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.DeleteItemUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import com.example.personal_studio.feature.timeline.today.TodayClock
import com.example.personal_studio.feature.timeline.today.TodayFocus
import com.example.personal_studio.feature.timeline.today.item
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 16)
    private val tomorrow = today.plusDays(1)

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun at(h: Int, m: Int = 0, d: LocalDate = today) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private class FixedClock(var time: Long) : TodayClock() {
        override fun now(): Long = time
    }

    private val cancel: CancelRemindersUseCase = mockk(relaxed = true)
    private val schedule: ScheduleRemindersUseCase = mockk(relaxed = true)

    private fun TestScope.newVm(repo: FakeTimelineRepository, clock: FixedClock, focus: TodayFocus = TodayFocus()): TimelineViewModel {
        val semester = mockk<SemesterPreferences> { every { startDate } returns flowOf(null) }
        val timetable = mockk<TimetablePreferences> { every { periods } returns flowOf(DefaultTimetable.PERIODS) }
        val login = mockk<LoginPrefs> {
            every { observeLastSyncAt } returns flowOf(null)
            every { observeFirstSyncDone } returns flowOf(false)
        }
        val vm = TimelineViewModel(
            repo = repo,
            toggleDone = ToggleDoneUseCase(repo, nowProvider = { clock.time }),
            deleteItem = DeleteItemUseCase(repo),
            cancelReminders = cancel,
            scheduleReminders = schedule,
            semester = semester,
            timetable = timetable,
            loginPrefs = login,
            focus = focus,
            clock = clock,
            zone = zone,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test fun `today's items feed the card, the selected day feeds the timeline`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(
                item(1, TimelineType.COURSE, at(8), at(9, 35)),
                item(2, TimelineType.TASK, at(20, d = tomorrow)),
            ))
        }
        val vm = newVm(repo, FixedClock(at(10)))
        var s = vm.uiState.value
        assertEquals(today, s.selectedDay)
        assertEquals(listOf(1L), s.dayItems.map { it.id })
        assertEquals(listOf(2L), s.tomorrowItems.map { it.id })

        vm.onShiftDay(1)
        s = vm.uiState.value
        assertEquals(tomorrow, s.selectedDay)
        assertEquals(tomorrow, s.dayItemsDay)
        assertEquals(listOf(2L), s.dayItems.map { it.id })
        assertEquals(listOf(1L), s.todayItems.map { it.id })
    }

    @Test fun `checking a task saves at once, then plays tick and fold`() = runTest {
        val repo = FakeTimelineRepository().apply { preload(listOf(item(1, TimelineType.TASK, at(20)))) }
        val vm = newVm(repo, FixedClock(at(10)))

        vm.onCheck(1)
        assertTrue(repo.findById(1)!!.isDone)
        assertEquals(CheckPhase.CHECKING, vm.uiState.value.checkPhases[1])
        coVerify { cancel(1) }

        advanceTimeBy(TimelineViewModel.CHECK_MS + 1)
        assertEquals(CheckPhase.COLLAPSING, vm.uiState.value.checkPhases[1])

        advanceTimeBy(TimelineViewModel.COLLAPSE_MS + 1)
        assertNull(vm.uiState.value.checkPhases[1])
    }

    @Test fun `undo restores the task, reschedules and flashes it`() = runTest {
        val repo = FakeTimelineRepository().apply { preload(listOf(item(1, TimelineType.TASK, at(20), done = true))) }
        val vm = newVm(repo, FixedClock(at(10)))

        vm.onUndo(1)
        assertFalse(repo.findById(1)!!.isDone)
        coVerify { schedule(match { it.id == 1L }) }
        assertEquals(1L, vm.uiState.value.flashId)

        advanceTimeBy(TimelineViewModel.FLASH_MS + 1)
        assertNull(vm.uiState.value.flashId)
    }

    @Test fun `a focus request jumps to the item's day`() = runTest {
        val repo = FakeTimelineRepository().apply { preload(listOf(item(7, TimelineType.TASK, at(12, d = tomorrow)))) }
        val focus = TodayFocus()
        val vm = newVm(repo, FixedClock(at(10)), focus)

        focus.focus(7)
        val s = vm.uiState.value
        assertEquals(tomorrow, s.selectedDay)
        assertEquals(TodayView.DAY, s.view)
        assertEquals(7L, s.flashId)
    }

    @Test fun `the selected day follows midnight while it is today`() = runTest {
        val repo = FakeTimelineRepository()
        val clock = FixedClock(at(23, 59))
        val vm = newVm(repo, clock)
        assertEquals(today, vm.uiState.value.selectedDay)

        clock.time = at(0, 1, d = tomorrow)
        advanceTimeBy(61_000)
        assertEquals(tomorrow, vm.uiState.value.today)
        assertEquals(tomorrow, vm.uiState.value.selectedDay)
    }

    @Test fun `week shift moves by seven days and shows the grid`() = runTest {
        val vm = newVm(FakeTimelineRepository(), FixedClock(at(10)))
        vm.onShiftWeek(1)
        val s = vm.uiState.value
        assertEquals(today.plusWeeks(1), s.selectedDay)
        assertEquals(TodayView.WEEK, s.view)
        assertEquals(today.plusWeeks(1), s.weekStart) // today is a Monday
    }
}
