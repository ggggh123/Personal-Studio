package com.example.personal_studio.data.repository

import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class FakeScanRepository(
    private val rootDir: File = File(System.getProperty("java.io.tmpdir"), "fake-scans").apply { mkdirs() },
) : ScanRepository {

    private val seqDoc = AtomicLong(0)
    private val seqPage = AtomicLong(0)

    private val docs = MutableStateFlow<List<ScanDocument>>(emptyList())
    private val pagesByDoc = mutableMapOf<Long, MutableStateFlow<List<ScanPage>>>()

    override fun observeDocuments(sort: SortMode): Flow<List<ScanDocument>> = docs.map { list ->
        when (sort) {
            SortMode.TIME_DESC -> list.sortedByDescending { it.createdAt }
            SortMode.ALPHA_ASC -> list.sortedBy { it.title.lowercase() }
            SortMode.RECENT_UPDATED -> list.sortedByDescending { it.updatedAt }
        }
    }

    override fun observeDocument(docId: Long): Flow<ScanDocument?> =
        docs.map { list -> list.firstOrNull { it.id == docId } }

    override fun observePages(docId: Long): Flow<List<ScanPage>> =
        pagesByDoc.getOrPut(docId) { MutableStateFlow(emptyList()) }

    override suspend fun getPage(pageId: Long): ScanPage? =
        pagesByDoc.values.firstNotNullOfOrNull { flow -> flow.value.firstOrNull { it.id == pageId } }

    override suspend fun createPendingDocument(defaultTitle: String): Long {
        val id = seqDoc.incrementAndGet()
        val now = System.currentTimeMillis()
        docs.update { it + ScanDocument(id, defaultTitle, now, now, 0, null) }
        documentDir(id).mkdirs()
        return id
    }

    override suspend fun appendPage(
        docId: Long,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ): Long {
        val pageId = seqPage.incrementAndGet()
        val current = pagesByDoc.getOrPut(docId) { MutableStateFlow(emptyList()) }
        val ordinal = current.value.size
        val page = ScanPage(pageId, docId, ordinal, originalImagePath, enhancedImagePath, filter, cornersJson, System.currentTimeMillis())
        current.update { it + page }
        val newCount = current.value.size
        val now = System.currentTimeMillis()
        docs.update { list -> list.map { if (it.id == docId) it.copy(pageCount = newCount, updatedAt = now) else it } }
        return pageId
    }

    override suspend fun updatePageFilter(pageId: Long, enhancedImagePath: String, filter: ScanFilter) {
        pagesByDoc.values.forEach { flow ->
            flow.update { list -> list.map { if (it.id == pageId) it.copy(enhancedImagePath = enhancedImagePath, filter = filter) else it } }
        }
    }

    override suspend fun replacePage(pageId: Long, originalImagePath: String, enhancedImagePath: String, filter: ScanFilter, cornersJson: String?) {
        pagesByDoc.values.forEach { flow ->
            flow.update { list -> list.map { if (it.id == pageId) it.copy(originalImagePath = originalImagePath, enhancedImagePath = enhancedImagePath, filter = filter, cornersJson = cornersJson) else it } }
        }
    }

    override suspend fun deletePage(pageId: Long) {
        val ownerDocId = pagesByDoc.entries
            .firstOrNull { (_, flow) -> flow.value.any { it.id == pageId } }
            ?.key
        pagesByDoc.values.forEach { flow ->
            flow.update { list -> list.filter { it.id != pageId } }
        }
        if (ownerDocId != null) {
            val newCount = pagesByDoc[ownerDocId]?.value?.size ?: 0
            val now = System.currentTimeMillis()
            docs.update { list -> list.map { if (it.id == ownerDocId) it.copy(pageCount = newCount, updatedAt = now) else it } }
        }
    }

    override suspend fun reorderPages(docId: Long, orderedPageIds: List<Long>) {
        val flow = pagesByDoc[docId] ?: return
        flow.update { list ->
            val byId = list.associateBy { it.id }
            orderedPageIds.mapIndexedNotNull { idx, id -> byId[id]?.copy(ordinal = idx) }
        }
    }

    override suspend fun finalizeDocument(docId: Long) {
        val pages = pagesByDoc[docId]?.value.orEmpty()
        docs.update { list -> list.map { if (it.id == docId) it.copy(pageCount = pages.size, coverPageId = pages.firstOrNull()?.id) else it } }
    }

    override suspend fun renameDocument(docId: Long, newTitle: String) {
        docs.update { list -> list.map { if (it.id == docId) it.copy(title = newTitle, updatedAt = System.currentTimeMillis()) else it } }
    }

    override suspend fun deleteDocument(docId: Long) {
        docs.update { list -> list.filter { it.id != docId } }
        pagesByDoc.remove(docId)
        documentDir(docId).deleteRecursively()
    }

    override fun documentDir(docId: Long): File = File(rootDir, docId.toString())
}
