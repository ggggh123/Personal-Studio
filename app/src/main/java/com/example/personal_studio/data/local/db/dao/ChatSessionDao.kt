package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personal_studio.data.local.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

data class ChatSessionSummaryRow(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val msgCount: Int,
    val lastSnippet: String?,
)

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query(
        "SELECT s.id AS id, s.title AS title, s.updatedAt AS updatedAt, " +
        "(SELECT COUNT(*) FROM chat_messages WHERE sessionId = s.id) AS msgCount, " +
        "(SELECT contentMarkdown FROM chat_messages WHERE sessionId = s.id ORDER BY createdAt DESC LIMIT 1) AS lastSnippet " +
        "FROM chat_sessions s ORDER BY s.updatedAt DESC"
    )
    fun observeSessionSummaries(): Flow<List<ChatSessionSummaryRow>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: Long): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ChatSessionEntity): Long

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameTitle(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun countSessions(): Int

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
