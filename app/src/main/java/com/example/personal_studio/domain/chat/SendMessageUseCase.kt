package com.example.personal_studio.domain.chat

import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject

sealed interface SendChunk {
    data class UserPersisted(val messageId: Long) : SendChunk
    data class Delta(val text: String) : SendChunk
    data class AiPersisted(val messageId: Long) : SendChunk
    data class Error(val message: String, val retryable: Boolean) : SendChunk
}

class SendMessageUseCase @Inject constructor(
    private val repo: ChatRepository,
    private val llm: LLMProvider,
) {
    operator fun invoke(
        sessionId: Long,
        userText: String,
        userImagePath: String?,
        systemPrompt: String? = SYSTEM_PROMPT,
    ): Flow<SendChunk> = flow {
        val userId = repo.appendMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = userText,
            attachedImagePath = userImagePath,
        )
        emit(SendChunk.UserPersisted(userId))

        val buffer = StringBuilder()
        val llmFlow = if (userImagePath != null) {
            val bytes = File(userImagePath).readBytes()
            llm.generateMultimodal(
                prompt = userText,
                images = listOf(bytes),
                systemPrompt = systemPrompt,
            )
        } else {
            llm.generateText(prompt = userText, systemPrompt = systemPrompt)
        }

        llmFlow.collect { chunk ->
            when (chunk) {
                is LlmChunk.Text -> {
                    buffer.append(chunk.delta)
                    emit(SendChunk.Delta(chunk.delta))
                }
                is LlmChunk.Done -> {
                    val aiId = repo.appendMessage(
                        sessionId = sessionId,
                        role = MessageRole.AI,
                        content = buffer.toString(),
                        attachedImagePath = null,
                    )
                    emit(SendChunk.AiPersisted(aiId))
                }
                is LlmChunk.Error -> {
                    emit(SendChunk.Error(chunk.message, chunk.retryable))
                }
            }
        }
    }

    companion object {
        const val SYSTEM_PROMPT = """你是嵌在一个终端风格学习 App 里的助手。
回答规则：
1. 若用户附了图片，优先基于图片内容作答。
2. 数学公式用 LaTeX 包裹：行内 ${'$'}...${'$'}，块级 ${'$'}${'$'}...${'$'}${'$'}。
3. 中文为主，准确、简洁、可追问。不要寒暄、不要前置声明。
4. 若需要更多信息，直接问。
5. 输出应该可以直接 cat/print——不要在结尾加感叹号、表情或"祝你学习愉快"之类的客套。"""
    }
}
