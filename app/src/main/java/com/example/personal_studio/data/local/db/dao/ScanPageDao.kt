package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.personal_studio.data.local.db.entity.ScanPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanPageDao {

    @Query("SELECT * FROM scan_pages WHERE docId = :docId ORDER BY ordinal ASC")
    fun observePages(docId: Long): Flow<List<ScanPageEntity>>

    @Query("SELECT * FROM scan_pages WHERE id = :id")
    suspend fun get(id: Long): ScanPageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScanPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ScanPageEntity>): List<Long>

    @Update
    suspend fun update(entity: ScanPageEntity)

    @Query("DELETE FROM scan_pages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Rewrites ordinals atomically. Called when user finishes a reorder session.
     * The two-pass trick (NEGATIVE staging, then final values) avoids hitting the
     * UNIQUE(docId, ordinal) index during the shuffle.
     */
    @Transaction
    suspend fun shiftOrdinalsForReorder(docId: Long, orderedPageIds: List<Long>) {
        orderedPageIds.forEachIndexed { index, id -> setOrdinal(id, -(index + 1)) }
        orderedPageIds.forEachIndexed { index, id -> setOrdinal(id, index) }
    }

    @Query("UPDATE scan_pages SET ordinal = :ordinal WHERE id = :id")
    suspend fun setOrdinal(id: Long, ordinal: Int)

    @Query("SELECT COALESCE(MAX(ordinal), -1) + 1 FROM scan_pages WHERE docId = :docId")
    suspend fun nextOrdinal(docId: Long): Int

    @Query("SELECT * FROM scan_pages WHERE docId = :docId ORDER BY ordinal ASC")
    suspend fun getPages(docId: Long): List<ScanPageEntity>
}
