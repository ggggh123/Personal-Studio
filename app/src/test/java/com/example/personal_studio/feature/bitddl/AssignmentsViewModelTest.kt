package com.example.personal_studio.feature.bitddl

import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitddl.SyncAssignmentsUseCase
import com.example.personal_studio.domain.bitddl.model.DdlSyncStep
import com.example.personal_studio.domain.bitimport.ResolveNetworkModeUseCase
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

class AssignmentsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun resolveEcho(): ResolveNetworkModeUseCase {
        val m = mockk<ResolveNetworkModeUseCase>()
        coEvery { m.invoke(any()) } returns NetworkMode.LOCAL
        return m
    }

    private val now = 1_000_000_000_000L
    private fun row(id: Long, due: Long, done: Boolean) = TimelineItemEntity(
        id = id, type = TimelineType.TASK, title = "T$id", description = "",
        startAt = due, isDone = done, sourceType = TimelineSource.IMPORTED_LEXUE,
        sourceExternalId = "u$id", createdAt = 1L, updatedAt = 1L, courseName = "C",
    )

    private fun vm(dao: TimelineDao) = AssignmentsViewModel(
        dao = dao,
        toggleDone = mockk(relaxed = true),
        cancelReminders = mockk(relaxed = true),
        scheduleReminders = mockk(relaxed = true),
        repo = mockk(relaxed = true),
        sync = mockk(relaxed = true),
        credPrefs = mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
        resolveNetworkMode = resolveEcho(), nowProvider = { now },
    )

    @Test fun `splits upcoming incomplete ascending vs done-or-overdue`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeLexueDdls() } returns flowOf(listOf(
                row(1, now + 2 * 3600_000L, done = false),
                row(2, now + 1 * 3600_000L, done = false),
                row(3, now + 5 * 3600_000L, done = true),
                row(4, now - 3600_000L, done = false),
            ))
        }
        val vm = vm(dao)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(2L, 1L), vm.uiState.value.upcoming.map { it.id })
        assertEquals(setOf(3L, 4L), vm.uiState.value.doneOrOverdue.map { it.id }.toSet())
        job.cancel()
    }

    @Test fun `toggle done delegates to use case`() = runTest {
        val toggle = mockk<com.example.personal_studio.domain.timeline.ToggleDoneUseCase>(relaxed = true)
        val cancel = mockk<com.example.personal_studio.domain.timeline.CancelRemindersUseCase>(relaxed = true)
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeLexueDdls() } returns flowOf(emptyList()) }
        val vm = AssignmentsViewModel(
            dao = dao, toggleDone = toggle, cancelReminders = cancel,
            scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true),
            sync = mockk(relaxed = true),
            credPrefs = mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
            resolveNetworkMode = resolveEcho(), nowProvider = { now },
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onToggleDone(5L, true)
        advanceUntilIdle()
        coVerify { toggle.invoke(5L, true) }
        coVerify { cancel.invoke(5L) }
        job.cancel()
    }

    @Test fun `refresh auto-fallback persists the winning mode and clears syncing`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeLexueDdls() } returns flowOf(emptyList()) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val sync = mockk<SyncAssignmentsUseCase> {
            every { syncAuto(any(), any()) } answers {
                secondArg<(NetworkMode) -> Unit>().invoke(NetworkMode.WEBVPN)
                flowOf(DdlSyncStep.SwitchingMode(NetworkMode.WEBVPN), DdlSyncStep.Done(0, 0))
            }
        }
        val vm = AssignmentsViewModel(
            dao = dao, toggleDone = mockk(relaxed = true), cancelReminders = mockk(relaxed = true),
            scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true),
            sync = sync, credPrefs = creds, resolveNetworkMode = resolveEcho(), nowProvider = { now },
        )
        val job = launch { vm.uiState.collect {} }
        vm.onRefresh()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.syncing)
        verify { creds.save("u", "p", NetworkMode.WEBVPN) }
        job.cancel()
    }

    @Test fun `refresh accumulates progress steps for the UI`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeLexueDdls() } returns flowOf(emptyList()) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val sync = mockk<SyncAssignmentsUseCase> {
            every { syncAuto(any(), any()) } returns flowOf(DdlSyncStep.FetchingCalendar, DdlSyncStep.Done(5, 2))
        }
        val vm = AssignmentsViewModel(
            dao = dao, toggleDone = mockk(relaxed = true), cancelReminders = mockk(relaxed = true),
            scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true),
            sync = sync, credPrefs = creds, resolveNetworkMode = resolveEcho(), nowProvider = { now },
        )
        val job = launch { vm.uiState.collect {} }
        vm.onRefresh(); advanceUntilIdle()
        assertEquals(listOf("登录并拉取乐学日历…", "完成 · 5 项作业"), vm.uiState.value.syncSteps)
        job.cancel()
    }

    @Test fun `refresh without creds emits NeedLogin event`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeLexueDdls() } returns flowOf(emptyList()) }
        val vm = AssignmentsViewModel(
            dao = dao, toggleDone = mockk(relaxed = true), cancelReminders = mockk(relaxed = true),
            scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true),
            sync = mockk(relaxed = true),
            credPrefs = mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
            resolveNetworkMode = resolveEcho(), nowProvider = { 0L },
        )
        vm.events.test {
            vm.onRefresh()
            advanceUntilIdle()
            assertEquals(AssignmentsEvent.NeedLogin, awaitItem())
        }
    }
}
