package com.example.personal_studio.core.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.DdlNotifier
import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.DdlSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitddl.DdlDiffResult
import com.example.personal_studio.domain.bitddl.DetectNewDdlUseCase
import com.example.personal_studio.domain.bitddl.ReplaceImportedDdlUseCase
import com.example.personal_studio.domain.bitddl.SyncAssignmentsUseCase
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DdlPollWorkerTest {
    private fun ev(uid: String) = DdlEvent(uid, "t", "d", "c", 1L)
    private fun enabled() = DdlSyncState(true, 12, null, emptySet(), "URL")
    private fun disabled() = DdlSyncState(false, 12, null, emptySet(), null)
    private fun creds() = SavedCredentials("u", "p", NetworkMode.LOCAL)

    private fun worker(
        pollPrefs: DdlSyncPrefs,
        credPrefs: ImportCredentialPrefs,
        sync: SyncAssignmentsUseCase,
        detector: DetectNewDdlUseCase = mockk(relaxed = true),
        replacer: ReplaceImportedDdlUseCase = mockk(relaxed = true),
        api: BitApiClient = mockk(relaxed = true),
        notifier: DdlNotifier = mockk(relaxed = true),
        scheduler: DdlPollScheduler = mockk(relaxed = true),
    ) = DdlPollWorker(
        appContext = mockk<Context>(relaxed = true),
        params = mockk<WorkerParameters>(relaxed = true),
        pollPrefs = pollPrefs, credPrefs = credPrefs, sync = sync, detector = detector,
        replacer = replacer, apiClient = api, notifier = notifier, scheduler = scheduler,
    )

    @Test fun `disabled returns success and does not sync`() = runTest {
        val pollPrefs = mockk<DdlSyncPrefs> { coEvery { snapshot() } returns disabled() }
        val sync = mockk<SyncAssignmentsUseCase>(relaxed = true)
        val r = worker(pollPrefs, mockk(relaxed = true), sync).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify(exactly = 0) { sync.syncForBackground(any()) }
    }

    @Test fun `missing creds disables and returns success`() = runTest {
        val pollPrefs = mockk<DdlSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns enabled() }
        val credPrefs = mockk<ImportCredentialPrefs> { every { observeAll() } returns MutableStateFlow(null) }
        val sync = mockk<SyncAssignmentsUseCase>(relaxed = true)
        val r = worker(pollPrefs, credPrefs, sync).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { pollPrefs.setEnabled(false) }
        coVerify(exactly = 0) { sync.syncForBackground(any()) }
    }

    @Test fun `wrong credentials triggers strict stop`() = runTest {
        val pollPrefs = mockk<DdlSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns enabled() }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds())
        }
        val sync = mockk<SyncAssignmentsUseCase> {
            coEvery { syncForBackground(any()) } returns BackgroundDdlResult.Stop(DdlSyncError.WrongCredentials)
        }
        val notifier = mockk<DdlNotifier>(relaxed = true)
        val scheduler = mockk<DdlPollScheduler>(relaxed = true)
        val r = worker(pollPrefs, credPrefs, sync, notifier = notifier, scheduler = scheduler).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { credPrefs.clear() }
        coVerify { pollPrefs.setEnabled(false) }
        coVerify { scheduler.cancel() }
        coVerify { notifier.notifyStop(any(), DdlSyncError.WrongCredentials) }
    }

    @Test fun `transient returns retry`() = runTest {
        val pollPrefs = mockk<DdlSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns enabled() }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds())
        }
        val sync = mockk<SyncAssignmentsUseCase> { coEvery { syncForBackground(any()) } returns BackgroundDdlResult.Transient }
        val r = worker(pollPrefs, credPrefs, sync).doWork()
        assertEquals(ListenableWorker.Result.retry(), r)
        coVerify(exactly = 0) { pollPrefs.setEnabled(false) }
    }

    @Test fun `first run replaces silently without notify`() = runTest {
        val pollPrefs = mockk<DdlSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns enabled() }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds())
        }
        val events = listOf(ev("a"), ev("b"))
        val sync = mockk<SyncAssignmentsUseCase> { coEvery { syncForBackground(any()) } returns BackgroundDdlResult.Ok(events) }
        val detector = mockk<DetectNewDdlUseCase>()
        coEvery { detector.invoke(events) } returns DdlDiffResult(emptyList(), setOf("a", "b"), isFirstRun = true)
        val replacer = mockk<ReplaceImportedDdlUseCase>(relaxed = true)
        val notifier = mockk<DdlNotifier>(relaxed = true)
        val r = worker(pollPrefs, credPrefs, sync, detector, replacer, notifier = notifier).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { replacer.invoke(events, any()) }
        coVerify(exactly = 0) { notifier.notifyNewDdls(any(), any()) }
        coVerify { pollPrefs.setLastSeenUids(setOf("a", "b")) }
    }

    @Test fun `subsequent new events notify`() = runTest {
        val pollPrefs = mockk<DdlSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns enabled() }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(creds())
        }
        val newOne = ev("c")
        val events = listOf(ev("a"), newOne)
        val sync = mockk<SyncAssignmentsUseCase> { coEvery { syncForBackground(any()) } returns BackgroundDdlResult.Ok(events) }
        val detector = mockk<DetectNewDdlUseCase>()
        coEvery { detector.invoke(events) } returns DdlDiffResult(listOf(newOne), setOf("a", "c"), isFirstRun = false)
        val replacer = mockk<ReplaceImportedDdlUseCase>(relaxed = true)
        val notifier = mockk<DdlNotifier>(relaxed = true)
        val r = worker(pollPrefs, credPrefs, sync, detector, replacer, notifier = notifier).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { replacer.invoke(events, any()) }
        coVerify { notifier.notifyNewDdls(any(), listOf(newOne)) }
    }
}
