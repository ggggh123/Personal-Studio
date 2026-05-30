package com.example.personal_studio.feature.bitimport

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.ImportCoursesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `initial state defaults to Credentials screen, empty form, LOCAL mode`() = runTest {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            coEvery { observeAll() } returns MutableStateFlow(null)
        }
        val importUseCase = mockk<ImportCoursesUseCase>(relaxed = true)

        val vm = ImportViewModel(importUseCase, credPrefs)
        vm.uiState.test {
            val first = awaitItem()
            assertEquals(ImportScreen.Credentials, first.currentScreen)
            assertEquals("", first.username)
            assertEquals(NetworkMode.LOCAL, first.networkMode)
        }
    }

    @Test fun `startWithSavedCreds triggers import when creds saved`() = runTest {
        val importUseCase = mockk<ImportCoursesUseCase>(relaxed = true) {
            every { import(any(), any()) } returns flowOf()
        }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns
                MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val vm = ImportViewModel(importUseCase, creds)
        vm.startWithSavedCreds()
        advanceUntilIdle()
        verify { importUseCase.import(any(), any()) }
    }

    /** 取消导入必须「退出向导」(done=true 触发 onClose 回上级),而不是把已登录用户
     *  送回 Credentials 屏 —— 那对已登录用户渲染成 TerminalSplash,会卡死在裸 logo。 */
    @Test fun `cancel exits wizard via done flag, not back to Credentials splash`() = runTest {
        val importUseCase = mockk<ImportCoursesUseCase>(relaxed = true) {
            every { import(any(), any()) } returns
                flowOf(com.example.personal_studio.domain.bitimport.model.ImportStep.Cancelled)
        }
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val vm = ImportViewModel(importUseCase, credPrefs)
        vm.onLogin()
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.done)
    }
}
