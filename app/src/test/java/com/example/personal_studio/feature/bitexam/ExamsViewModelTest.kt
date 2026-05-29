package com.example.personal_studio.feature.bitexam

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExamsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_000_000_000_000L
    private fun row(id: Long, start: Long, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "E$id", startAt = start, endAt = start + 7200_000L,
        isDone = done, location = "r", notes = "座位: 1",
        sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = "u$id", createdAt = 1L, updatedAt = 1L,
    )
    private fun vm(dao: TimelineDao) = ExamsViewModel(
        dao = dao, toggleDone = mockk(relaxed = true), cancelReminders = mockk(relaxed = true),
        scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true), sync = mockk(relaxed = true),
        credPrefs = mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
        nowProvider = { now },
    )

    @Test fun `splits upcoming vs past`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeImportedExams() } returns flowOf(listOf(
                row(1, now + 2 * 3600_000L, false),
                row(2, now - 3 * 3600_000L, false),
            ))
        }
        val vm = vm(dao)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.uiState.value.upcoming.map { it.id })
        assertEquals(listOf(2L), vm.uiState.value.past.map { it.id })
        job.cancel()
    }

    @Test fun `refresh without creds emits NeedLogin`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val vm = vm(dao)
        vm.events.test { vm.onRefresh(); advanceUntilIdle(); assertEquals(ExamsEvent.NeedLogin, awaitItem()) }
    }

    @Test fun `toggle done delegates`() = runTest {
        val toggle = mockk<com.example.personal_studio.domain.timeline.ToggleDoneUseCase>(relaxed = true)
        val cancel = mockk<com.example.personal_studio.domain.timeline.CancelRemindersUseCase>(relaxed = true)
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val vm = ExamsViewModel(dao, toggle, cancel, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) }, { now })
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleDone(5L, true); advanceUntilIdle()
        coVerify { toggle.invoke(5L, true) }
        coVerify { cancel.invoke(5L) }
        job.cancel()
    }
}
