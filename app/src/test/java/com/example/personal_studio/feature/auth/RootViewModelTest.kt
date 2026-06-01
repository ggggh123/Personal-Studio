package com.example.personal_studio.feature.auth

import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.ui.navigation.NavRoutes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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

class RootViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `seen login goes to PROFILE`() = runTest {
        val prefs = mockk<LoginPrefs> { every { observe } returns flowOf(true) }
        val vm = RootViewModel(prefs)
        val job = launch { vm.startDestination.collect {} }
        advanceUntilIdle()
        assertEquals(NavRoutes.PROFILE, vm.startDestination.value)
        job.cancel()
    }

    @Test fun `unseen login goes to LOGIN`() = runTest {
        val prefs = mockk<LoginPrefs> { every { observe } returns flowOf(false) }
        val vm = RootViewModel(prefs)
        val job = launch { vm.startDestination.collect {} }
        advanceUntilIdle()
        assertEquals(NavRoutes.LOGIN, vm.startDestination.value)
        job.cancel()
    }
}
