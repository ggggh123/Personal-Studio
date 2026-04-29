package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KbEntryDao {

    @Insert
    suspend fun insert(e: KbEntryEntity): Long

    @Update
    suspend fun update(e: KbEntryEntity)

    @Query("DELETE FROM kb_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM kb_entries WHERE id = :id")
    fun observe(id: Long): Flow<KbEntryEntity?>

    @Query("SELECT * FROM kb_entries WHERE id = :id")
    suspend fun get(id: Long): KbEntryEntity?

    @Query(
        """SELECT * FROM kb_entries
           WHERE (:categoryId IS NULL OR categoryId = :categoryId)
             AND (:notesOnly = 0 OR standardizedQuestion IS NULL)
           ORDER BY createdAt DESC""",
    )
    fun observeAll(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntryEntity>>

    @Query(
        """SELECT * FROM kb_entries
           WHERE standardizedQuestion IS NOT NULL
           ORDER BY createdAt DESC""",
    )
    fun observeMistakes(): Flow<List<KbEntryEntity>>

    @Query(
        """SELECT e.* FROM kb_entries e
           JOIN kb_entries_fts f ON f.rowid = e.id
           WHERE kb_entries_fts MATCH :ftsQuery
           ORDER BY e.createdAt DESC""",
    )
    fun searchFlow(ftsQuery: String): Flow<List<KbEntryEntity>>

    @Query(
        """SELECT e.* FROM kb_entries e
           JOIN kb_entries_fts f ON f.rowid = e.id
           WHERE kb_entries_fts MATCH :ftsQuery
           ORDER BY e.createdAt DESC""",
    )
    suspend fun searchOnce(ftsQuery: String): List<KbEntryEntity>

    @Query(
        """SELECT e.* FROM kb_entries e
           JOIN kb_relations r ON r.toEntryId = e.id
           WHERE r.fromEntryId = :id
           ORDER BY r.weight DESC""",
    )
    fun observeRelated(id: Long): Flow<List<KbEntryEntity>>

    @Query("SELECT * FROM kb_entries WHERE title IN (:titles)")
    suspend fun findByTitles(titles: List<String>): List<KbEntryEntity>

    @Query("SELECT title FROM kb_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentTitles(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE standardizedQuestion IS NULL")
    fun countNotes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE standardizedQuestion IS NOT NULL")
    fun countMistakes(): Flow<Int>

    @Query("SELECT categoryId AS id, COUNT(*) AS count FROM kb_entries WHERE categoryId IS NOT NULL GROUP BY categoryId")
    fun countByCategory(): Flow<List<CategoryCountRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(rels: List<KbRelationEntity>)

    data class CategoryCountRow(val id: Long, val count: Int)
}
