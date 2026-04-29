package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity

@Dao
interface KbFtsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fts: KbEntryFtsEntity)

    @Query("DELETE FROM kb_entries_fts WHERE rowid = :id")
    suspend fun delete(id: Long)
}
