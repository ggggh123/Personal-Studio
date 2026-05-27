package com.example.personal_studio.core.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.GradesNotifier
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.DetectNewGradesUseCase
import com.example.personal_studio.domain.bitgrades.DiffResult
import com.example.personal_studio.domain.bitgrades.JsxsdDetailParser
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GradePollWorkerTest {

    private fun entry(code: String, score: String) = GradeEntryEntity(
        termCode = "t", termName = "t", courseName = code, courseCode = code,
        credit = 3.0, score = score, gradePoint = 4.0, gradeLetter = null, category = null,
        attemptType = "正常", isPass = true, fetchedAt = 1L,
    )

    private fun stateEnabled(sigs: Set<String> = emptySet()) = GradesSyncState(
        enabled = true, intervalHours = 6, lastSyncAt = null, lastSeenSignature = sigs,
    )

    private fun stateDisabled() = GradesSyncState(
        enabled = false, intervalHours = 6, lastSyncAt = null, lastSeenSignature = emptySet(),
    )

    private fun savedCreds() = SavedCredentials(
        username = "u", password = "p", lastMode = NetworkMode.LOCAL,
    )

    private fun newWorker(
        pollPrefs: GradesSyncPrefs,
        credPrefs: ImportCredentialPrefs,
        sync: SyncGradesUseCase,
        detector: DetectNewGradesUseCase = mockk(relaxed = true),
        api: BitApiClient = mockk(relaxed = true),
        dao: GradesDao = mockk(relaxed = true),
        notifier: GradesNotifier = mockk(relaxed = true),
        scheduler: GradesPollScheduler = mockk(relaxed = true),
    ): GradePollWorker = GradePollWorker(
        appContext = mockk<Context>(relaxed = true),
        params = mockk<WorkerParameters>(relaxed = true),
        pollPrefs = pollPrefs,
        credPrefs = credPrefs,
        sync = sync,
        detector = detector,
        detailParser = JsxsdDetailParser(),
        apiClient = api,
        gradesDao = dao,
        notifier = notifier,
        scheduler = scheduler,
    )

    @Test
    fun `pollEnabled false returns success and does not sync`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs> { coEvery { snapshot() } returns stateDisabled() }
        val sync = mockk<SyncGradesUseCase>(relaxed = true)
        val r = newWorker(pollPrefs, mockk(relaxed = true), sync).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify(exactly = 0) { sync.syncForBackground(any()) }
    }

    @Test
    fun `missing creds disables and returns success`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns stateEnabled()
        }
        val creds = mockk<ImportCredentialPrefs> {
            every { observeAll() } returns MutableStateFlow(null)
        }
        val sync = mockk<SyncGradesUseCase>(relaxed = true)
        val r = newWorker(pollPrefs, creds, sync).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { pollPrefs.setEnabled(false) }
        coVerify(exactly = 0) { sync.syncForBackground(any()) }
    }

    @Test
    fun `wrong credentials triggers strict stop`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns stateEnabled()
        }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns
                BackgroundSyncResult.Stop(GradesSyncError.WrongCredentials)
        }
        val notifier = mockk<GradesNotifier>(relaxed = true)
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val r = newWorker(
            pollPrefs, creds, sync,
            notifier = notifier, scheduler = scheduler,
        ).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { creds.clear() }
        coVerify { pollPrefs.setEnabled(false) }
        coVerify { scheduler.cancel() }
        coVerify { notifier.notifyStop(any(), GradesSyncError.WrongCredentials) }
    }

    @Test
    fun `transient error returns retry without disabling`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns stateEnabled()
        }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns BackgroundSyncResult.Transient
        }
        val r = newWorker(pollPrefs, creds, sync).doWork()
        assertEquals(ListenableWorker.Result.retry(), r)
        coVerify(exactly = 0) { pollPrefs.setEnabled(false) }
    }

    @Test
    fun `first run with no lastSeen builds baseline silently`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns stateEnabled()
        }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val entries = listOf(entry("A", "90"), entry("B", "85"))
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns BackgroundSyncResult.Ok(entries)
        }
        val detector = mockk<DetectNewGradesUseCase>()
        coEvery { detector.invoke(entries) } returns DiffResult(
            newEntries = emptyList(),
            fullSignature = setOf("t|A|正常|90", "t|B|正常|85"),
            isFirstRun = true,
        )
        val notifier = mockk<GradesNotifier>(relaxed = true)
        val sigSlot = slot<Set<String>>()
        val r = newWorker(pollPrefs, creds, sync, detector, notifier = notifier).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { pollPrefs.setLastSeenSignature(capture(sigSlot)) }
        assertEquals(setOf("t|A|正常|90", "t|B|正常|85"), sigSlot.captured)
        coVerify(exactly = 0) { notifier.notifyNewGrades(any(), any()) }
    }

    @Test
    fun `subsequent run with new entries upserts and notifies`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) {
            coEvery { snapshot() } returns stateEnabled()
        }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        // detailPath 为 null → enrich 走早返回,原样保留
        val newOne = entry("C", "92")
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns
                BackgroundSyncResult.Ok(listOf(entry("A", "90"), newOne))
        }
        val detector = mockk<DetectNewGradesUseCase>()
        coEvery { detector.invoke(any()) } returns DiffResult(
            newEntries = listOf(newOne),
            fullSignature = setOf("t|A|正常|90", "t|C|正常|92"),
            isFirstRun = false,
        )
        val dao = mockk<GradesDao>(relaxed = true)
        val notifier = mockk<GradesNotifier>(relaxed = true)
        val r = newWorker(
            pollPrefs, creds, sync, detector,
            dao = dao, notifier = notifier,
        ).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { dao.upsertAll(listOf(newOne)) }
        coVerify { notifier.notifyNewGrades(any(), listOf(newOne)) }
    }
}
