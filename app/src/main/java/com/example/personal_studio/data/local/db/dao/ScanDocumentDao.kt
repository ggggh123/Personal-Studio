package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDocumentDao {

    @Query("SELECT * FROM scan_documents ORDER BY createdAt DESC")
    fun observeAllByTimeDesc(): Flow<List<ScanDocumentEntity>>

    @Query("SELECT * FROM scan_documents ORDER BY title COLLATE NOCASE ASC")
    fun observeAllByAlphaAsc(): Flow<List<ScanDocumentEntity>>

    @Query("SELECT * FROM scan_documents ORDER BY updatedAt DESC")
    fun observeAllByRecentUpdated(): Flow<List<ScanDocumentEntity>>

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

    @Query("UPDATE scan_documents SET pageCount = :count, coverPageId = :coverId, updatedAt = :now WHERE id = :id")
    suspend fun finalize(id: Long, count: Int, coverId: Long?, now: Long)

    @Query("DELETE FROM scan_documents WHERE id = :id")
    suspend fun delete(id: Long)
}
