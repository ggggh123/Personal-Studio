package com.example.personal_studio.feature.settings.vm

import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.auth.LogoutUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsHomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val creds = MutableStateFlow<SavedCredentials?>(SavedCredentials("1120230021", "pw", NetworkMode.LOCAL))
    private val logout = mockk<LogoutUseCase>(relaxed = true)

    private fun vm() = SettingsHomeViewModel(
        semester = mockk(relaxed = true) { every { startDate } returns flowOf(null) },
        timetable = mockk(relaxed = true) { every { periods } returns flowOf(emptyList()) },
        timeline = mockk(relaxed = true) { every { observeCourseSeriesList() } returns flowOf(emptyList()) },
        userPrefs = FakeUserPreferencesRepository(),
        credPrefs = mockk<ImportCredentialPrefs> { every { observeAll() } returns creds },
        logout = logout,
    )

    @Test fun `the school account shows masked and goes away when signed out`() = runTest(dispatcher) {
        val sut = vm()
        val job = launch { sut.account.collect {} }
        assertEquals("1120 **** 21", sut.account.value)
        creds.value = null
        assertEquals(null, sut.account.value)
        job.cancel()
    }

    @Test fun `logout passes on whether to delete the school data`() = runTest {
        val sut = vm()
        sut.logout(clearSchoolData = true)
        coVerify { logout.invoke(true) }
        sut.logout(clearSchoolData = false)
        coVerify { logout.invoke(false) }
    }
}
