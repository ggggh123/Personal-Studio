package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GradesDao {
    @Query("SELECT * FROM grade_entries ORDER BY termCode DESC, courseName")
    fun observeAll(): Flow<List<GradeEntryEntity>>

    @Query("SELECT * FROM grade_entries")
    suspend fun listAll(): List<GradeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<GradeEntryEntity>)

    @Query("DELETE FROM grade_entries")
    suspend fun clearGrades(): Int

    @Query("SELECT * FROM term_ranks")
    fun observeRanks(): Flow<List<TermRankEntity>>

    @Query("SELECT * FROM term_ranks")
    suspend fun listRanks(): List<TermRankEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRanks(rows: List<TermRankEntity>)

    @Query("DELETE FROM term_ranks")
    suspend fun clearRanks(): Int
}
