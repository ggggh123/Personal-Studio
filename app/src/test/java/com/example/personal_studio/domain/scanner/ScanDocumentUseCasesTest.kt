package com.example.personal_studio.domain.scanner

import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScanDocumentUseCasesTest {

    @Test fun `CreateDocFromSinglePage produces 1-page finalized doc`() = runTest {
        val repo = FakeScanRepository()
        val useCase = CreateDocFromSinglePageUseCase(
            CreateScanDocumentUseCase(repo),
            AddPageToDocumentUseCase(repo),
            FinalizeScanDocumentUseCase(repo),
        )
        val docId = useCase("scan_test", "a.jpg", "a.jpg", ScanFilter.COLOR, null)
        val doc = repo.observeDocument(docId).first()
        assertNotNull(doc)
        assertEquals(1, doc!!.pageCount)
        assertEquals(1, repo.observePages(docId).first().size)
    }

    @Test fun `ReorderPages updates ordinals`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("t")
        val ids = (0..2).map { repo.appendPage(docId, "$it", "$it", ScanFilter.BW, null) }
        ReorderPagesUseCase(repo)(docId, listOf(ids[2], ids[0], ids[1]))
        val pages = repo.observePages(docId).first()
        assertEquals(listOf(ids[2], ids[0], ids[1]), pages.map { it.id })
        assertEquals(listOf(0, 1, 2), pages.map { it.ordinal })
    }

    @Test fun `DeleteScanDocument leaves no observable doc`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("x")
        DeleteScanDocumentUseCase(repo)(docId)
        assertNull(repo.observeDocument(docId).first())
    }

    /**
     * Regression: pending-doc rows showed `0 pages` in the library even
     * after appendPage, because pageCount was only written by finalize.
     * appendPage must keep the denormalized pageCount honest so the
     * library row matches what resume actually loads.
     */
    @Test fun `appendPage updates pageCount on the document`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("pending")
        repo.appendPage(docId, "a.jpg", "a.jpg", ScanFilter.BW, null)
        repo.appendPage(docId, "b.jpg", "b.jpg", ScanFilter.BW, null)
        assertEquals(2, repo.observeDocument(docId).first()!!.pageCount)
    }

    @Test fun `deletePage decrements pageCount on the document`() = runTest {
        val repo = FakeScanRepository()
        val docId = repo.createPendingDocument("pending")
        val pid = repo.appendPage(docId, "a.jpg", "a.jpg", ScanFilter.BW, null)
        repo.appendPage(docId, "b.jpg", "b.jpg", ScanFilter.BW, null)
        repo.deletePage(pid)
        assertEquals(1, repo.observeDocument(docId).first()!!.pageCount)
    }
}
