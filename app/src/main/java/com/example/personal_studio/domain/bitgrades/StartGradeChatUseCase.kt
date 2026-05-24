package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.model.MessageRole
import javax.inject.Inject

/** 建一个预置成绩上下文的聊天会话，返回 sessionId 供导航到 P1 聊天详情。
 *  上下文作为 SYSTEM 消息写入：进入 LLM 上下文，但聊天 UI 隐藏 SYSTEM 消息。 */
class StartGradeChatUseCase @Inject constructor(
    private val chatRepo: ChatRepository,
    private val buildSummary: BuildGradeSummaryUseCase,
) {
    suspend fun invoke(book: GradeBook): Long {
        val title = "成绩分析 · ${book.terms.firstOrNull()?.termName ?: "全部"}"
        val sessionId = chatRepo.createSession(title)
        val ctx = "以下是我的成绩单数据，请基于它回答我后续的问题：\n\n" + buildSummary.invoke(book)
        chatRepo.appendMessage(
            sessionId = sessionId,
            role = MessageRole.SYSTEM,
            content = ctx,
            attachedImagePath = null,
        )
        return sessionId
    }
}
