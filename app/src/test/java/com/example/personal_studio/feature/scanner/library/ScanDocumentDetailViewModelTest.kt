package com.example.personal_studio.feature.scanner.library

import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.RenameScanDocumentUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        renameUc = RenameScanDocumentUseCase(repo),
        deleteDocUc = DeleteScanDocumentUseCase(repo),
    )

    @Test fun `commitReorder persists new order`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("d")
        val ids = (0..2).map { repo.appendPage(docId, "$it", "$it", ScanFilter.BW, null) }
        val vm = newVm(repo, docId)
        vm.enterReorder()
        vm.moveInDraft(0, 2)
        vm.commitReorder()
        assertEquals(listOf(ids[1], ids[2], ids[0]), repo.observePages(docId).first().map { it.id })
        // reorderMode should reset after commit
        assertFalse(vm.state.value.reorderMode)
        assertTrue(vm.state.value.reorderDraft.isEmpty())
    }

    @Test fun `cancelReorder drops the draft without persisting`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("d")
        val ids = (0..2).map { repo.appendPage(docId, "$it", "$it", ScanFilter.BW, null) }
        val vm = newVm(repo, docId)
        vm.enterReorder()
        vm.moveInDraft(0, 2)
        vm.cancelReorder()
        // Original order still in repo
        assertEquals(ids, repo.observePages(docId).first().map { it.id })
        assertFalse(vm.state.value.reorderMode)
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

    @Test fun `renameDoc updates the document title`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("old")
        val vm = newVm(repo, docId)
        vm.renameDoc("new-title")
        assertEquals("new-title", repo.observeDocument(docId).first()!!.title)
    }

    @Test fun `deleteDoc removes the document`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("x")
        val vm = newVm(repo, docId)
        vm.deleteDoc()
        assertEquals(null, repo.observeDocument(docId).first())
    }
}
