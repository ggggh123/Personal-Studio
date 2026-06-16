package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import kotlinx.coroutines.flow.Flow

data class ScanDocumentSummaryRow(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPageId: Long?,
    val coverPath: String?,
)

@Dao
interface ScanDocumentDao {

    @Query("SELECT * FROM scan_documents ORDER BY createdAt DESC")
    fun observeAllByTimeDesc(): Flow<List<ScanDocumentEntity>>

    @Query("SELECT * FROM scan_documents ORDER BY title COLLATE NOCASE ASC")
    fun observeAllByAlphaAsc(): Flow<List<ScanDocumentEntity>>

    @Query("SELECT * FROM scan_documents ORDER BY updatedAt DESC")
    fun observeAllByRecentUpdated(): Flow<List<ScanDocumentEntity>>

    @Query(
        "SELECT d.id AS id, d.title AS title, d.createdAt AS createdAt, d.updatedAt AS updatedAt, " +
        "d.pageCount AS pageCount, d.coverPageId AS coverPageId, " +
        "(SELECT enhancedImagePath FROM scan_pages WHERE docId = d.id ORDER BY ordinal ASC LIMIT 1) AS coverPath " +
        "FROM scan_documents d"
    )
    fun observeDocumentSummaries(): Flow<List<ScanDocumentSummaryRow>>

    @Query("SELECT * FROM scan_documents WHERE id = :id")
    fun observe(id: Long): Flow<ScanDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScanDocumentEntity): Long

    @Update
    suspend fun update(entity: ScanDocumentEntity)

    @Query("UPDATE scan_documents SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long)

    @Query("UPDATE scan_documents SET updatedAt = :now WHERE id = :id")
    suspend fun touchUpdatedAt(id: Long, now: Long)

    /**
     * Recomputes pageCount from scan_pages and bumps updatedAt. Called on
     * every append/delete so the denormalized pageCount stays accurate for
     * pending docs (library row label). coverPageId remains null until
     * [finalize].
     */
    @Query("""
        UPDATE scan_documents
        SET pageCount = (SELECT COUNT(*) FROM scan_pages WHERE docId = :id),
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun recountPages(id: Long, now: Long)

    @Query("UPDATE scan_documents SET pageCount = :count, coverPageId = :coverId, updatedAt = :now WHERE id = :id")
    suspend fun finalize(id: Long, count: Int, coverId: Long?, now: Long)

    @Query("DELETE FROM scan_documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM scan_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScanDocumentEntity?
}
