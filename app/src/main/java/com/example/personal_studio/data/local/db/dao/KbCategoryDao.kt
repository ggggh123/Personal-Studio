package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KbCategoryDao {

    @Query("SELECT * FROM kb_categories ORDER BY seeded DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<KbCategoryEntity>>

    @Query("SELECT * FROM kb_categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): KbCategoryEntity?

    @Query("SELECT * FROM kb_categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): KbCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(c: KbCategoryEntity): Long

    @Query("DELETE FROM kb_categories WHERE id = :id AND seeded = 0")
    suspend fun deleteUnseeded(id: Long): Int
}
