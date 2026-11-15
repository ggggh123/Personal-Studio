package com.example.personal_studio.feature.scanner.library

import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.domain.scanner.AddPageToDocumentUseCase
import com.example.personal_studio.domain.scanner.CreateScanDocumentUseCase
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import com.example.personal_studio.domain.scanner.ReorderPagesUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ScanDocumentDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    // navDocId>0 = 打开已有;<=0 = 新建。captureAndEnhance/exportUc 走 androidTest 才需真实,
    // 此处 mock(reorder/delete/新建/退出清理 路径不碰它们)。
    private fun newVm(repo: FakeScanRepository, navDocId: Long) = ScanDocumentDetailViewModel(
        navDocId = navDocId,
        context = mockk(relaxed = true),
        repo = repo,
        createDoc = CreateScanDocumentUseCase(repo),
        addPage = AddPageToDocumentUseCase(repo),
        captureAndEnhance = mockk(relaxed = true),
        deleteDoc = DeleteScanDocumentUseCase(repo),
        reorderUc = ReorderPagesUseCase(repo),
        removePageUc = RemovePageUseCase(repo),
        exportUc = mockk(relaxed = true),
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

    @Test fun `new doc (navDocId 0) writes nothing before the first page`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo, 0L)
        assertTrue(vm.isNew)
        assertEquals(0, repo.observeDocuments(SortMode.TIME_DESC).first().size)
    }

    @Test fun `first confirmed page creates the new doc with that page`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo, 0L)
        vm.confirmPage(File("tmp.jpg"), emptyList(), ScanFilter.BW)
        val docs = repo.observeDocuments(SortMode.TIME_DESC).first()
        assertEquals(1, docs.size)
        assertEquals(1, repo.observePages(docs.single().id).first().size)
        assertEquals(1, vm.state.value.pages.size)
    }

    @Test fun `abandoning a new doc leaves nothing behind`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo, 0L)           // 新建,没拍就退出
        vm.onExit()
        assertEquals(0, repo.observeDocuments(SortMode.TIME_DESC).first().size)
    }

    @Test fun `onExit keeps an existing doc`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("keep")
        repo.appendPage(docId, "a", "a", ScanFilter.BW, null)
        val vm = newVm(repo, docId)        // 打开已有
        vm.onExit()
        assertEquals(1, repo.observeDocuments(SortMode.TIME_DESC).first().size)
    }
}
