package com.example.personal_studio.feature.scanner.library

import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.RenameScanDocumentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanLibraryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun `setSort toggles the sort mode in emitted state`() = runTest {
        val repo = FakeScanRepository()
        repo.createPendingDocument("zebra")
        repo.createPendingDocument("alpha")
        val vm = ScanLibraryViewModel(repo, RenameScanDocumentUseCase(repo), DeleteScanDocumentUseCase(repo))

        vm.uiState.test {
            val initial = awaitItem()
            assertEquals(SortMode.TIME_DESC, initial.sort)

            vm.setSort(SortMode.ALPHA_ASC)
            val sorted = expectMostRecentItem()
            assertEquals(SortMode.ALPHA_ASC, sorted.sort)
            assertEquals(listOf("alpha", "zebra"), sorted.docs.map { it.title })

            cancelAndIgnoreRemainingEvents()
        }
    }
}
