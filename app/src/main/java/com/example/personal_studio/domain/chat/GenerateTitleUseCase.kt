package com.example.personal_studio.domain.chat

import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.data.repository.ChatRepository
import javax.inject.Inject

class GenerateTitleUseCase @Inject constructor(
    private val repo: ChatRepository,
    private val llm: LLMProvider,
) {
    suspend operator fun invoke(sessionId: Long): String? {
        val messages = repo.listMessages(sessionId)
        if (messages.isEmpty()) return null

        val transcript = messages.joinToString("\n\n") { m ->
            val who = when (m.role) {
                com.example.personal_studio.domain.model.MessageRole.USER -> "USER"
                com.example.personal_studio.domain.model.MessageRole.AI -> "AI"
                com.example.personal_studio.domain.model.MessageRole.SYSTEM -> "SYS"
            }
            "$who: ${m.contentMarkdown.take(400)}"
        }

        val prompt = """基于以下对话转录，生成一个简洁的中文标题。
要求：6-12 个字；不要加引号；不要加 "关于"、"讨论"、"对话" 等冗词；直接写内容核心。

TRANSCRIPT:
$transcript

TITLE:"""

        val buf = StringBuilder()
        llm.generateText(prompt, temperature = 0.3f).collect { chunk ->
            when (chunk) {
                is LlmChunk.Text -> buf.append(chunk.delta)
                is LlmChunk.Done, is LlmChunk.Error -> {}
            }
        }

        val title = buf.toString().trim().trim('"', '\'', '「', '」', '《', '》').take(20)
        if (title.isBlank()) return null

        repo.renameSession(sessionId, title)
        return title
    }
}
