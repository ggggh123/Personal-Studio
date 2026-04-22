package com.example.personal_studio.domain.chat

import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
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
        // 1. Persist the new user message (including any attached image).
        val userId = repo.appendMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = userText,
            attachedImagePath = userImagePath,
        )
        emit(SendChunk.UserPersisted(userId))

        // 2. Build the full conversation history for the LLM.
        //    We include text of every prior turn and reload image bytes from disk for
        //    any message (including the one we just appended) that has an attached image.
        val history = repo.listMessages(sessionId)
        val messages = buildList<LlmMessage> {
            if (!systemPrompt.isNullOrBlank()) {
                add(LlmMessage(LlmRole.SYSTEM, systemPrompt))
            }
            history.forEach { m ->
                val role = when (m.role) {
                    MessageRole.USER -> LlmRole.USER
                    MessageRole.AI -> LlmRole.ASSISTANT
                    MessageRole.SYSTEM -> LlmRole.SYSTEM
                }
                val images = m.attachedImagePath
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                    ?.let { listOf(it.readBytes()) }
                    ?: emptyList()
                add(LlmMessage(role, m.contentMarkdown, images))
            }
        }

        // 3. Stream the response, timing from the first delta so network latency /
        //    slow-to-start model behavior isn't counted in the displayed duration.
        val buffer = StringBuilder()
        var firstDeltaMs: Long? = null
        llm.generate(messages).collect { chunk ->
            when (chunk) {
                is LlmChunk.Text -> {
                    if (firstDeltaMs == null) firstDeltaMs = System.currentTimeMillis()
                    buffer.append(chunk.delta)
                    emit(SendChunk.Delta(chunk.delta))
                }
                is LlmChunk.Done -> {
                    val body = buffer.toString()
                    val duration = firstDeltaMs?.let { System.currentTimeMillis() - it } ?: 0L
                    val tokens = estimateTokens(body)
                    val aiId = repo.appendMessage(
                        sessionId = sessionId,
                        role = MessageRole.AI,
                        content = body,
                        attachedImagePath = null,
                        generationMs = duration,
                        tokenCount = tokens,
                        modelUsed = chunk.model,
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

/**
 * Rough token count. CJK codepoints count ~1 token each (tokenizers keep them whole);
 * latin/digits/punctuation are ~¼ token each. This is an approximation — OpenAI's BPE
 * produces different numbers — but it's stable, fast, and close enough for a UI
 * footer that exists to give the user a sense of scale.
 */
internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (cp in text.codePoints()) {
        val isCjk = (cp in 0x3040..0x30FF) ||
            (cp in 0x3400..0x4DBF) ||
            (cp in 0x4E00..0x9FFF) ||
            (cp in 0xAC00..0xD7AF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x20000..0x2EBEF)
        if (isCjk) cjk++ else other++
    }
    return (cjk + (other + 3) / 4).coerceAtLeast(1)
}
