package com.example.personal_studio.feature.scanner.doc

import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.scanner.AddPageToDocumentUseCase
import com.example.personal_studio.domain.scanner.CreateScanDocumentUseCase
import com.example.personal_studio.domain.scanner.DeleteScanDocumentUseCase
import com.example.personal_studio.domain.scanner.FinalizeScanDocumentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentBuilderViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private fun newVm(repo: FakeScanRepository = FakeScanRepository(), resumeId: Long? = null) =
        DocumentBuilderViewModel(
            resumeDocId = resumeId,
            repo = repo,
            createDoc = CreateScanDocumentUseCase(repo),
            addPage = AddPageToDocumentUseCase(repo),
            finalize = FinalizeScanDocumentUseCase(repo),
            deleteDoc = DeleteScanDocumentUseCase(repo),
        )

    @Test fun `init creates pending doc with fresh id`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo)
        val id = vm.state.value.docId
        assertNotNull(id)
        val doc = repo.observeDocument(id!!).first()
        assertNotNull(doc)
        assertEquals(0, doc!!.pageCount)
        assertNull(doc.coverPageId)
    }

    @Test fun `onPageCaptured appends to doc`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo)
        vm.onPageCaptured("a.jpg", "a.jpg", ScanFilter.BW, null)
        vm.onPageCaptured("b.jpg", "b.jpg", ScanFilter.BW, null)
        assertEquals(2, repo.observePages(vm.state.value.docId!!).first().size)
    }

    @Test fun `finish sets pageCount and coverId`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo)
        vm.onPageCaptured("a.jpg", "a.jpg", ScanFilter.BW, null)
        vm.finish()
        val doc = repo.observeDocument(vm.state.value.docId!!).first()
        assertEquals(1, doc!!.pageCount)
        assertNotNull(doc.coverPageId)
    }

    @Test fun `cancel deletes the pending doc`() = runTest {
        val repo = FakeScanRepository()
        val vm = newVm(repo)
        val id = vm.state.value.docId!!
        vm.cancel()
        assertNull(repo.observeDocument(id).first())
    }
}
