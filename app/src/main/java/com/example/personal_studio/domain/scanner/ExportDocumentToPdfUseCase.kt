package com.example.personal_studio.domain.scanner

import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.data.scanner.PdfExporter
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class ExportDocumentToPdfUseCase @Inject constructor(
    private val repo: ScanRepository,
    private val exporter: PdfExporter,
) {
    suspend operator fun invoke(docId: Long): File {
        val doc = repo.observeDocument(docId).first()
            ?: error("document $docId not found")
        val pages = repo.observePages(docId).first()
        check(pages.isNotEmpty()) { "document $docId has no pages" }
        return exporter.export(
            docId = docId,
            safeTitle = PdfExporter.safeTitleOf(doc.title),
            imagePaths = pages.map { it.enhancedImagePath },
        )
    }
}
