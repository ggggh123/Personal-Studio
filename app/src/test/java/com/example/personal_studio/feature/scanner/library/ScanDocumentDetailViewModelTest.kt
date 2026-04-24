package com.example.personal_studio.feature.scanner.library

import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.ReorderPagesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanDocumentDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private fun newVm(repo: FakeScanRepository, docId: Long) = ScanDocumentDetailViewModel(
        docId = docId,
        repo = repo,
        reorderUc = ReorderPagesUseCase(repo),
        removePageUc = RemovePageUseCase(repo),
    )

    @Test fun `reorderPages persists the given order`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("d")
        val ids = (0..2).map { repo.appendPage(docId, "$it", "$it", ScanFilter.BW, null) }
        val vm = newVm(repo, docId)
        vm.reorderPages(listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf(ids[2], ids[0], ids[1]), repo.observePages(docId).first().map { it.id })
    }

    @Test fun `reorderPages ignores empty input`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("d")
        val ids = (0..1).map { repo.appendPage(docId, "$it", "$it", ScanFilter.BW, null) }
        val vm = newVm(repo, docId)
        vm.reorderPages(emptyList())
        assertEquals(ids, repo.observePages(docId).first().map { it.id })
    }

    @Test fun `deletePage propagates to repo`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("d")
        val pid = repo.appendPage(docId, "a", "a", ScanFilter.BW, null)
        repo.appendPage(docId, "b", "b", ScanFilter.BW, null)
        val vm = newVm(repo, docId)
        vm.deletePage(pid)
        assertEquals(1, repo.observePages(docId).first().size)
    }
}
