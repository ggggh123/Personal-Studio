package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitimport.model.SyncAllProgress
import com.example.personal_studio.domain.bitimport.model.SyncFailure
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.domain.bitimport.model.SyncSourceState
import com.example.personal_studio.domain.bitimport.model.SyncSourceStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCenterTest {

    private fun progress(vararg states: Pair<SyncSource, SyncSourceStatus>, done: Boolean = true) = SyncAllProgress(
        states = SyncSource.values().associateWith { src ->
            val s = states.toMap()[src] ?: SyncSourceStatus.OK
            SyncSourceState(s, failure = if (s == SyncSourceStatus.FAILED) SyncFailure.NETWORK else null)
        },
        done = done,
    )

    @Test fun `a sync with any success records the time and first-sync flag`() = runTest {
        val result = progress(SyncSource.GRADES to SyncSourceStatus.FAILED)
        val useCase = mockk<SyncAllUseCase> { every { run(any(), any()) } returns flowOf(result) }
        val login = mockk<LoginPrefs>(relaxed = true)
        val center = SyncCenter(useCase, login, this.backgroundScope + UnconfinedTestDispatcher(testScheduler), { 1234L })

        center.startAll()

        assertEquals(result, center.progress.value)
        coVerify { login.setFirstSyncDone(true) }
        coVerify { login.setLastSyncAt(1234L) }
    }

    @Test fun `retry runs only the failed sources with the previous results`() = runTest {
        val first = progress(SyncSource.EXAMS to SyncSourceStatus.FAILED, SyncSource.GRADES to SyncSourceStatus.FAILED)
        val useCase = mockk<SyncAllUseCase> { every { run(any(), any()) } returns flowOf(first) }
        val center = SyncCenter(useCase, mockk(relaxed = true), this.backgroundScope + UnconfinedTestDispatcher(testScheduler), { 0L })

        center.startAll()
        center.retryFailed()

        verify { useCase.run(SyncSource.values().toSet(), emptyMap()) }
        verify { useCase.run(setOf(SyncSource.EXAMS, SyncSource.GRADES), first.states) }
    }

    @Test fun `no credentials records nothing`() = runTest {
        val none = SyncAllProgress(emptyMap(), done = true, noCredentials = true)
        val useCase = mockk<SyncAllUseCase> { every { run(any(), any()) } returns flowOf(none) }
        val login = mockk<LoginPrefs>(relaxed = true)
        val center = SyncCenter(useCase, login, this.backgroundScope + UnconfinedTestDispatcher(testScheduler), { 0L })

        center.startAll()

        coVerify(exactly = 0) { login.setFirstSyncDone(any()) }
        coVerify(exactly = 0) { login.setLastSyncAt(any()) }
    }
}
