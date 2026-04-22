package com.example.personal_studio.domain.scanner

import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import javax.inject.Inject

class CreateScanDocumentUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(defaultTitle: String): Long = repo.createPendingDocument(defaultTitle)
}

class AddPageToDocumentUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(
        docId: Long,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ): Long = repo.appendPage(docId, originalImagePath, enhancedImagePath, filter, cornersJson)
}

class RemovePageUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(pageId: Long) = repo.deletePage(pageId)
}

class ReorderPagesUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(docId: Long, orderedPageIds: List<Long>) =
        repo.reorderPages(docId, orderedPageIds)
}

class RecapturePageUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(
        pageId: Long,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ) = repo.replacePage(pageId, originalImagePath, enhancedImagePath, filter, cornersJson)
}

class RenameScanDocumentUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(docId: Long, newTitle: String) =
        repo.renameDocument(docId, newTitle)
}

class DeleteScanDocumentUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(docId: Long) = repo.deleteDocument(docId)
}

class FinalizeScanDocumentUseCase @Inject constructor(private val repo: ScanRepository) {
    suspend operator fun invoke(docId: Long) = repo.finalizeDocument(docId)
}

/** For chat --from-camera with save-to-lib checkbox: creates a 1-page doc
 *  with the already-captured files. */
class CreateDocFromSinglePageUseCase @Inject constructor(
    private val createDoc: CreateScanDocumentUseCase,
    private val addPage: AddPageToDocumentUseCase,
    private val finalize: FinalizeScanDocumentUseCase,
) {
    suspend operator fun invoke(
        defaultTitle: String,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ): Long {
        val docId = createDoc(defaultTitle)
        addPage(docId, originalImagePath, enhancedImagePath, filter, cornersJson)
        finalize(docId)
        return docId
    }
}
