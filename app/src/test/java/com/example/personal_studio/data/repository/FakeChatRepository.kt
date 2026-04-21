package com.example.personal_studio.data.repository

import com.example.personal_studio.domain.model.ChatMessage
import com.example.personal_studio.domain.model.ChatSession
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

class FakeChatRepository : ChatRepository {

    private val sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    private val messagesBySession = MutableStateFlow<Map<Long, List<ChatMessage>>>(emptyMap())
    private val seq = AtomicLong(0)

    override fun observeSessions(): Flow<List<ChatSession>> = sessions

    override suspend fun getSession(id: Long): ChatSession? =
        sessions.value.firstOrNull { it.id == id }

    override suspend fun createSession(initialTitle: String): Long {
        val id = seq.incrementAndGet()
        val now = System.currentTimeMillis()
        val s = ChatSession(id = id, title = initialTitle, iconHint = null, createdAt = now, updatedAt = now)
        sessions.value = (sessions.value + s).sortedByDescending { it.updatedAt }
        return id
    }

    override suspend fun renameSession(id: Long, title: String) {
        sessions.value = sessions.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = System.currentTimeMillis()) else it
        }.sortedByDescending { it.updatedAt }
    }

    override suspend fun deleteSession(id: Long) {
        sessions.value = sessions.value.filterNot { it.id == id }
        messagesBySession.value = messagesBySession.value - id
    }

    override suspend fun countSessions(): Int = sessions.value.size

    override fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messagesBySession.map { it[sessionId].orEmpty() }

    override suspend fun listMessages(sessionId: Long): List<ChatMessage> =
        messagesBySession.value[sessionId].orEmpty()

    override suspend fun appendMessage(
        sessionId: Long,
        role: MessageRole,
        content: String,
        attachedImagePath: String?,
    ): Long {
        val id = seq.incrementAndGet()
        val now = System.currentTimeMillis()
        val msg = ChatMessage(
            id = id, sessionId = sessionId, role = role,
            contentMarkdown = content, attachedImagePath = attachedImagePath,
            createdAt = now,
        )
        val existing = messagesBySession.value[sessionId].orEmpty()
        messagesBySession.value = messagesBySession.value + (sessionId to existing + msg)
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(updatedAt = now) else it
        }.sortedByDescending { it.updatedAt }
        return id
    }
}
