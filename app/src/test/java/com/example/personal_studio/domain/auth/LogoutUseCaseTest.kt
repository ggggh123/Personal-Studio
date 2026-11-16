package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.example.personal_studio.data.network.bit.BitApiClient

class LogoutUseCaseTest {
    @Test fun `logout clears creds, cancels schedulers, disables sync`() = runTest {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true)
        val gradesSyncPrefs = mockk<GradesSyncPrefs>(relaxed = true)
        val ddlSyncPrefs = mockk<DdlSyncPrefs>(relaxed = true)
        val gradesScheduler = mockk<GradesPollScheduler>(relaxed = true)
        val ddlScheduler = mockk<DdlPollScheduler>(relaxed = true)
        val api = mockk<BitApiClient>(relaxed = true)
        val clear = mockk<ClearSchoolDataUseCase>(relaxed = true)
        val useCase = LogoutUseCase(credPrefs, gradesSyncPrefs, ddlSyncPrefs, gradesScheduler, ddlScheduler, api, clear)

        useCase.invoke()

        verify { credPrefs.clear() }
        verify { gradesScheduler.cancel() }
        verify { ddlScheduler.cancel() }
        coVerify { gradesSyncPrefs.setEnabled(false) }
        coVerify { ddlSyncPrefs.setEnabled(false) }
        coVerify { api.clearBrowserSession() }
        coVerify { ddlSyncPrefs.clearIcalUrl() }
        coVerify(exactly = 0) { clear.invoke() }
    }

    @Test fun `logout can also clear school data`() = runTest {
        val clear = mockk<ClearSchoolDataUseCase>(relaxed = true)
        val useCase = LogoutUseCase(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), clear,
        )

        useCase.invoke(clearSchoolData = true)

        coVerify(exactly = 1) { clear.invoke() }
    }
}
