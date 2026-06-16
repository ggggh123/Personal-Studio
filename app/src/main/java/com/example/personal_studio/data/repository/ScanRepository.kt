package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import com.example.personal_studio.data.local.db.entity.ScanPageEntity
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ScanRepository {
    /** Emits the full document list, re-sorted whenever the DB changes. */
    fun observeDocuments(sort: SortMode): Flow<List<ScanDocument>>

    /** 同 observeDocuments,但带封面缩略图路径,按 [sort] 排序。 */
    fun observeDocumentSummaries(sort: SortMode): Flow<List<ScanDocumentSummary>>

    /** Emits a single document (or null if deleted). */
    fun observeDocument(docId: Long): Flow<ScanDocument?>

    /** Emits the ordered page list for a document. */
    fun observePages(docId: Long): Flow<List<ScanPage>>

    /** One-shot page fetch; returns null if not found. */
    suspend fun getPage(pageId: Long): ScanPage?

    /**
     * Creates a new document row with pageCount=0, coverPageId=null.
     * Returns the new document id. Caller must eventually call [finalizeDocument].
     */
    suspend fun createPendingDocument(defaultTitle: String): Long

    /**
     * Appends a new page at the end of [docId].
     * Returns the new page id. Updates document's updatedAt.
     */
    suspend fun appendPage(
        docId: Long,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ): Long

    /**
     * Replaces the enhancedImagePath and filter of an existing page in-place.
     * Does NOT touch originalImagePath or ordinal.
     */
    suspend fun updatePageFilter(pageId: Long, enhancedImagePath: String, filter: ScanFilter)

    /**
     * Replaces all image paths, filter, and cornersJson of an existing page.
     * Deletes the old image files from disk.
     */
    suspend fun replacePage(
        pageId: Long,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    )

    /**
     * Deletes a single page and its image files from disk.
     * Does NOT auto-shift ordinals — caller should follow up with [reorderPages] if needed.
     */
    suspend fun deletePage(pageId: Long)

    /**
     * Atomically rewrites the ordinals of all pages in [docId] to match [orderedPageIds].
     * Uses a two-pass negative-staging trick to avoid UNIQUE index conflicts mid-shuffle.
     */
    suspend fun reorderPages(docId: Long, orderedPageIds: List<Long>)

    /**
     * Sets pageCount and coverPageId on the document row.
     * Must be called after all pages are appended (e.g. end of capture flow).
     */
    suspend fun finalizeDocument(docId: Long)

    /** Renames the document and bumps updatedAt. */
    suspend fun renameDocument(docId: Long, newTitle: String)

    /**
     * Deletes the document row (pages CASCADE via FK) and wipes the file directory.
     */
    suspend fun deleteDocument(docId: Long)

    /** Returns the per-document image directory (filesDir/scans/<docId>). */
    fun documentDir(docId: Long): File
}

@Singleton
class ScanRepositoryImpl @Inject constructor(
    private val docDao: ScanDocumentDao,
    private val pageDao: ScanPageDao,
    private val scansRoot: File,     // = filesDir/scans, provided by ScannerModule
) : ScanRepository {

    override fun observeDocuments(sort: SortMode): Flow<List<ScanDocument>> =
        when (sort) {
            SortMode.TIME_DESC -> docDao.observeAllByTimeDesc()
            SortMode.ALPHA_ASC -> docDao.observeAllByAlphaAsc()
            SortMode.RECENT_UPDATED -> docDao.observeAllByRecentUpdated()
        }.map { list -> list.map { it.toDomain() } }

    override fun observeDocumentSummaries(sort: SortMode): Flow<List<ScanDocumentSummary>> =
        docDao.observeDocumentSummaries().map { rows ->
            val mapped = rows.map {
                ScanDocumentSummary(
                    it.id, it.title, it.createdAt, it.updatedAt,
                    it.pageCount, it.coverPageId, it.coverPath,
                )
            }
            when (sort) {
                SortMode.TIME_DESC -> mapped.sortedByDescending { it.createdAt }
                SortMode.ALPHA_ASC -> mapped.sortedBy { it.title.lowercase() }
                SortMode.RECENT_UPDATED -> mapped.sortedByDescending { it.updatedAt }
            }
        }

    override fun observeDocument(docId: Long): Flow<ScanDocument?> =
        docDao.observe(docId).map { it?.toDomain() }

    override fun observePages(docId: Long): Flow<List<ScanPage>> =
        pageDao.observePages(docId).map { list -> list.map { it.toDomain() } }

    override suspend fun getPage(pageId: Long): ScanPage? =
        pageDao.get(pageId)?.toDomain()

    override suspend fun createPendingDocument(defaultTitle: String): Long {
        val now = System.currentTimeMillis()
        val id = docDao.insert(
            ScanDocumentEntity(
                title = defaultTitle, createdAt = now, updatedAt = now,
                pageCount = 0, coverPageId = null,
            )
        )
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
        val now = System.currentTimeMillis()
        val pageId = pageDao.insert(
            ScanPageEntity(
                docId = docId, ordinal = pageDao.nextOrdinal(docId),
                originalImagePath = originalImagePath,
                enhancedImagePath = enhancedImagePath,
                filter = filter.name, cornersJson = cornersJson,
                createdAt = now,
            )
        )
        docDao.recountPages(docId, now)
        return pageId
    }

    override suspend fun updatePageFilter(pageId: Long, enhancedImagePath: String, filter: ScanFilter) {
        val existing = pageDao.get(pageId) ?: return
        pageDao.update(existing.copy(enhancedImagePath = enhancedImagePath, filter = filter.name))
        docDao.touchUpdatedAt(existing.docId, System.currentTimeMillis())
    }

    override suspend fun replacePage(
        pageId: Long,
        originalImagePath: String,
        enhancedImagePath: String,
        filter: ScanFilter,
        cornersJson: String?,
    ) {
        val existing = pageDao.get(pageId) ?: return
        runCatching { File(existing.originalImagePath).delete() }
        if (existing.enhancedImagePath != existing.originalImagePath) {
            runCatching { File(existing.enhancedImagePath).delete() }
        }
        pageDao.update(
            existing.copy(
                originalImagePath = originalImagePath,
                enhancedImagePath = enhancedImagePath,
                filter = filter.name,
                cornersJson = cornersJson,
            )
        )
        docDao.touchUpdatedAt(existing.docId, System.currentTimeMillis())
    }

    override suspend fun deletePage(pageId: Long) {
        val page = pageDao.get(pageId) ?: return
        runCatching { File(page.originalImagePath).delete() }
        if (page.enhancedImagePath != page.originalImagePath) {
            runCatching { File(page.enhancedImagePath).delete() }
        }
        pageDao.deleteById(pageId)
        docDao.recountPages(page.docId, System.currentTimeMillis())
    }

    override suspend fun reorderPages(docId: Long, orderedPageIds: List<Long>) {
        pageDao.shiftOrdinalsForReorder(docId, orderedPageIds)
        docDao.touchUpdatedAt(docId, System.currentTimeMillis())
    }

    override suspend fun finalizeDocument(docId: Long) {
        val pages = pageDao.getPages(docId)
        docDao.finalize(
            id = docId,
            count = pages.size,
            coverId = pages.firstOrNull()?.id,
            now = System.currentTimeMillis(),
        )
    }

    override suspend fun renameDocument(docId: Long, newTitle: String) {
        docDao.rename(docId, newTitle, System.currentTimeMillis())
    }

    override suspend fun deleteDocument(docId: Long) {
        docDao.delete(docId)  // pages CASCADE
        documentDir(docId).deleteRecursively()
    }

    override fun documentDir(docId: Long): File =
        File(scansRoot, docId.toString())
}

// Mappers

private fun ScanDocumentEntity.toDomain() = ScanDocument(
    id = id, title = title, createdAt = createdAt, updatedAt = updatedAt,
    pageCount = pageCount, coverPageId = coverPageId,
)

private fun ScanPageEntity.toDomain() = ScanPage(
    id = id, docId = docId, ordinal = ordinal,
    originalImagePath = originalImagePath,
    enhancedImagePath = enhancedImagePath,
    filter = ScanFilter.valueOf(filter),
    cornersJson = cornersJson, createdAt = createdAt,
)
