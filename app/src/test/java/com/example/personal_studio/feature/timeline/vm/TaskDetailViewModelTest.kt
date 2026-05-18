package com.example.personal_studio.feature.timeline.vm

import com.example.personal_studio.data.repository.FakeTimelineRepository
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.CancelRemindersUseCase
import com.example.personal_studio.domain.timeline.DeleteItemUseCase
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.domain.timeline.ToggleDoneUseCase
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    private val cancel: CancelRemindersUseCase = mockk(relaxed = true)
    private val schedule: ScheduleRemindersUseCase = mockk(relaxed = true)

    @Test fun `toggle done switches isDone in repo`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(taskItem(id = 1, isDone = false)))
        }
        val vm = TaskDetailViewModel(
            repo = repo,
            toggleDone = ToggleDoneUseCase(repo, nowProvider = { 1L }),
            deleteItem = DeleteItemUseCase(repo),
            cancel = cancel,
            schedule = schedule,
        )
        vm.load(1)
        vm.onToggleDone()
        assertTrue(repo.findById(1)!!.isDone)
    }

    @Test fun `delete removes item`() = runTest {
        val repo = FakeTimelineRepository().apply {
            preload(listOf(taskItem(id = 1)))
        }
        val vm = TaskDetailViewModel(repo,
            ToggleDoneUseCase(repo), DeleteItemUseCase(repo), cancel, schedule)
        vm.load(1)
        vm.onDelete()
        assertNull(repo.findById(1))
    }

    private fun taskItem(id: Long, isDone: Boolean = false) = TimelineItem(
        id = id, type = TimelineType.TASK, title = "x", description = null,
        startAt = 0, endAt = null, isDone = isDone, doneAt = if (isDone) 0 else null,
        location = null, instructor = null, notes = null,
        seriesId = null, periodIndex = null, periodEndIndex = null,
        weekdayCode = null, weekIndexInSemester = null, colorOverride = null,
        sourceType = TimelineSource.MANUAL, sourceExternalId = null,
        kbEntryIds = emptyList(), createdAt = 0, updatedAt = 0,
    )
}
