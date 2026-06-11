package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.entity.ChatMessageEntity
import com.example.personal_studio.data.local.db.entity.ChatSessionEntity
import com.example.personal_studio.domain.model.ChatMessage
import com.example.personal_studio.domain.model.ChatSession
import com.example.personal_studio.domain.model.ChatSessionSummary
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ChatRepository {
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeSessionSummaries(): Flow<List<ChatSessionSummary>>
    suspend fun getSession(id: Long): ChatSession?
    suspend fun createSession(initialTitle: String): Long
    suspend fun renameSession(id: Long, title: String)
    suspend fun deleteSession(id: Long)
    suspend fun countSessions(): Int

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>>
    suspend fun listMessages(sessionId: Long): List<ChatMessage>
    suspend fun appendMessage(
        sessionId: Long,
        role: MessageRole,
        content: String,
        attachedImagePath: String?,
        generationMs: Long? = null,
        tokenCount: Int? = null,
        modelUsed: String? = null,
    ): Long
}

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
) : ChatRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        sessionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeSessionSummaries(): Flow<List<ChatSessionSummary>> =
        sessionDao.observeSessionSummaries().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getSession(id: Long): ChatSession? =
        sessionDao.getById(id)?.toDomain()

    override suspend fun createSession(initialTitle: String): Long {
        val now = System.currentTimeMillis()
        return sessionDao.insert(
            ChatSessionEntity(title = initialTitle, createdAt = now, updatedAt = now)
        )
    }

    override suspend fun renameSession(id: Long, title: String) {
        sessionDao.renameTitle(id, title, System.currentTimeMillis())
    }

    override suspend fun deleteSession(id: Long) { sessionDao.delete(id) }

    override suspend fun countSessions(): Int = sessionDao.countSessions()

    override fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messageDao.observeBySession(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun listMessages(sessionId: Long): List<ChatMessage> =
        messageDao.listBySession(sessionId).map { it.toDomain() }

    override suspend fun appendMessage(
        sessionId: Long,
        role: MessageRole,
        content: String,
        attachedImagePath: String?,
        generationMs: Long?,
        tokenCount: Int?,
        modelUsed: String?,
    ): Long {
        val now = System.currentTimeMillis()
        val id = messageDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role.toEntity(),
                contentMarkdown = content,
                attachedImagePath = attachedImagePath,
                createdAt = now,
                generationMs = generationMs,
                tokenCount = tokenCount,
                modelUsed = modelUsed,
            )
        )
        sessionDao.touch(sessionId, now)
        return id
    }
}

// Mapping helpers

private fun ChatSessionEntity.toDomain() = ChatSession(
    id = id, title = title, iconHint = iconHint,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun com.example.personal_studio.data.local.db.dao.ChatSessionSummaryRow.toDomain() =
    ChatSessionSummary(
        id = id, title = title, updatedAt = updatedAt,
        msgCount = msgCount, lastSnippet = lastSnippet,
    )

private fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id, sessionId = sessionId, role = role.toDomain(),
    contentMarkdown = contentMarkdown, attachedImagePath = attachedImagePath,
    createdAt = createdAt,
    generationMs = generationMs, tokenCount = tokenCount, modelUsed = modelUsed,
)

private fun com.example.personal_studio.data.local.db.entity.MessageRole.toDomain() =
    when (this) {
        com.example.personal_studio.data.local.db.entity.MessageRole.USER -> MessageRole.USER
        com.example.personal_studio.data.local.db.entity.MessageRole.AI -> MessageRole.AI
        com.example.personal_studio.data.local.db.entity.MessageRole.SYSTEM -> MessageRole.SYSTEM
    }

private fun MessageRole.toEntity() =
    when (this) {
        MessageRole.USER -> com.example.personal_studio.data.local.db.entity.MessageRole.USER
        MessageRole.AI -> com.example.personal_studio.data.local.db.entity.MessageRole.AI
        MessageRole.SYSTEM -> com.example.personal_studio.data.local.db.entity.MessageRole.SYSTEM
    }
