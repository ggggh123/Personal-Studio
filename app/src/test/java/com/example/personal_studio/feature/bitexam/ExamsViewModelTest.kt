package com.example.personal_studio.feature.bitexam

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.TimelineDao
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitexam.SyncExamsUseCase
import com.example.personal_studio.domain.bitexam.model.ExamSyncStep
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
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

class ExamsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_000_000_000_000L
    private fun row(
        id: Long, start: Long, done: Boolean,
        endAt: Long? = start + 7200_000L, notes: String? = "座位: 1", instructor: String? = null,
    ) = TimelineItemEntity(
        id = id, type = TimelineType.EXAM, title = "E$id", startAt = start, endAt = endAt,
        isDone = done, location = "r", instructor = instructor, notes = notes,
        sourceType = TimelineSource.IMPORTED_EXAM, sourceExternalId = "u$id", createdAt = 1L, updatedAt = 1L,
    )
    private fun vm(dao: TimelineDao) = ExamsViewModel(
        dao = dao, sync = mockk(relaxed = true),
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

    @Test fun `null endAt with past start lands in past`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeImportedExams() } returns flowOf(listOf(
                row(3, now - 1 * 3600_000L, false, endAt = null),
            ))
        }
        val vm = vm(dao)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(3L), vm.uiState.value.past.map { it.id })
        assertEquals(emptyList<Long>(), vm.uiState.value.upcoming.map { it.id })
        job.cancel()
    }

    @Test fun `seat is extracted from notes prefix`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeImportedExams() } returns flowOf(listOf(
                row(4, now + 1 * 3600_000L, false, notes = "座位: 78"),
            ))
        }
        val vm = vm(dao)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("78", vm.uiState.value.upcoming.single().seat)
        job.cancel()
    }

    @Test fun `invigilator is mapped from instructor`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) {
            every { observeImportedExams() } returns flowOf(listOf(
                row(6, now + 1 * 3600_000L, false, instructor = "刘峡壁"),
            ))
        }
        val vm = vm(dao)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("刘峡壁", vm.uiState.value.upcoming.single().invigilator)
        job.cancel()
    }

    @Test fun `refresh without creds emits NeedLogin`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val vm = vm(dao)
        vm.events.test { vm.onRefresh(); advanceUntilIdle(); assertEquals(ExamsEvent.NeedLogin, awaitItem()) }
    }

    @Test fun `refresh auto-fallback persists the winning mode and clears syncing`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val sync = mockk<SyncExamsUseCase> {
            every { syncAuto(any(), any()) } answers {
                // 模拟首选 LOCAL 不可达、回退到 WEBVPN 成功:回告生效模式。
                secondArg<(NetworkMode) -> Unit>().invoke(NetworkMode.WEBVPN)
                flowOf(ExamSyncStep.SwitchingMode(NetworkMode.WEBVPN), ExamSyncStep.Done(0))
            }
        }
        val vm = ExamsViewModel(dao = dao, sync = sync, credPrefs = creds, nowProvider = { now })
        val job = launch { vm.uiState.collect {} }
        vm.onRefresh()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.syncing)
        verify { creds.save("u", "p", NetworkMode.WEBVPN) }
        job.cancel()
    }

    @Test fun `refresh accumulates progress steps for the UI`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeImportedExams() } returns flowOf(emptyList()) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val sync = mockk<SyncExamsUseCase> {
            every { syncAuto(any(), any()) } returns flowOf(
                ExamSyncStep.LoggingIn, ExamSyncStep.FetchingExams, ExamSyncStep.Done(3),
            )
        }
        val vm = ExamsViewModel(dao = dao, sync = sync, credPrefs = creds, nowProvider = { now })
        val job = launch { vm.uiState.collect {} }
        vm.onRefresh(); advanceUntilIdle()
        assertEquals(listOf("登录中…", "拉取考试安排…", "完成 · 3 场考试"), vm.uiState.value.syncSteps)
        job.cancel()
    }
}
