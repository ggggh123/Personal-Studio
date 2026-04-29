package com.example.personal_studio.data.repository

import com.example.personal_studio.data.kb.KbImageStore
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.local.db.entity.MessageRole
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
import com.example.personal_studio.domain.model.KbDraftSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads source content (chat / scan) and shapes it into the LlmMessage list
 * the LLM will see, plus a staged copy of the original image (if any) under
 * filesDir/kb/tmp_*.jpg that becomes the entry's originalImagePath on commit.
 */
@Singleton
class SourceContextLoader @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val scanDocumentDao: ScanDocumentDao,
    private val scanPageDao: ScanPageDao,
    private val imageStore: KbImageStore,
) {

    data class LoadedContext(
        val messages: List<LlmMessage>,
        /** filesDir/kb/tmp_<uuid>.jpg path; null when source has no image (CHAT_SESSION etc.). */
        val stagedImagePath: String?,
        /** Used when AI parse fails entirely so the fallback Draft has a sensible title. */
        val fallbackTitle: String,
    )

    suspend fun load(source: KbDraftSource): LoadedContext = withContext(Dispatchers.IO) {
        when (source) {
            is KbDraftSource.FromChatMessage -> loadChatMessage(source)
            is KbDraftSource.FromChatSession -> loadChatSession(source)
            is KbDraftSource.FromScanPage -> loadScanPage(source)
        }
    }

    private suspend fun loadChatMessage(s: KbDraftSource.FromChatMessage): LoadedContext {
        val all = chatMessageDao.getAllForSession(s.sessionId)
        val ai = all.firstOrNull { it.id == s.aiMessageId }
            ?: error("AI message ${s.aiMessageId} not found in session ${s.sessionId}")
        val precedingUser = all
            .filter { it.id < ai.id && it.role == MessageRole.USER }
            .maxByOrNull { it.id }
        val text = buildString {
            append("### 用户问题\n")
            append(precedingUser?.contentMarkdown.orEmpty())
            append("\n\n### AI 回答\n")
            append(ai.contentMarkdown)
        }
        val imageBytes = precedingUser?.attachedImagePath?.let { File(it).takeIf(File::exists)?.readBytes() }
        val staged = precedingUser?.attachedImagePath?.let { imageStore.stageCopy(it) }

        val messages = listOf(
            LlmMessage(LlmRole.USER, text, images = listOfNotNull(imageBytes)),
        )
        val sessionTitle = chatSessionDao.getById(s.sessionId)?.title ?: "会话"
        return LoadedContext(messages, staged, fallbackTitle = sessionTitle)
    }

    private suspend fun loadChatSession(s: KbDraftSource.FromChatSession): LoadedContext {
        val all = chatMessageDao.getAllForSession(s.sessionId)
        // Pack the conversation into ONE USER message so the model treats it as a
        // text-to-summarize task, not a continuing dialogue. Earlier multi-turn
        // formatting caused the model to focus only on the latest exchange.
        // Truncate from the OLDEST end if over budget so the most recent rounds
        // stay intact.
        val budget = TOTAL_CHARS_BUDGET
        val keptOldestFirst = mutableListOf<com.example.personal_studio.data.local.db.entity.ChatMessageEntity>()
        var charCount = 0
        for (m in all.asReversed()) {
            keptOldestFirst += m
            charCount += m.contentMarkdown.length
            if (charCount > budget) break
        }
        keptOldestFirst.reverse()

        val convoText = buildString {
            append("以下是一段共 ")
            append(keptOldestFirst.size)
            append(" 条消息的完整对话（按时间顺序）。请整体归纳为一张知识卡片，不要只关注最后一轮。\n\n")
            // Pair user→ai turns into rounds; SYSTEM messages just get inlined.
            var roundNo = 0
            var i = 0
            while (i < keptOldestFirst.size) {
                val m = keptOldestFirst[i]
                when (m.role) {
                    MessageRole.USER -> {
                        roundNo += 1
                        append("### 第 ").append(roundNo).append(" 轮\n")
                        append("**用户**：").append(m.contentMarkdown).append('\n')
                        // Greedy-pair with the next AI message if one follows.
                        val next = keptOldestFirst.getOrNull(i + 1)
                        if (next != null && next.role == MessageRole.AI) {
                            append("**AI**：").append(next.contentMarkdown).append("\n\n")
                            i += 2
                            continue
                        }
                        append('\n')
                    }
                    MessageRole.AI -> {
                        // Orphan AI message (shouldn't normally happen).
                        append("**AI**：").append(m.contentMarkdown).append("\n\n")
                    }
                    MessageRole.SYSTEM -> {
                        append("_(system: ").append(m.contentMarkdown).append(")_\n\n")
                    }
                }
                i += 1
            }
            append("---\n请基于以上完整对话生成知识卡片。")
        }

        val messages = listOf(LlmMessage(LlmRole.USER, convoText))
        val sessionTitle = chatSessionDao.getById(s.sessionId)?.title ?: "会话"
        return LoadedContext(messages, stagedImagePath = null, fallbackTitle = sessionTitle)
    }

    private suspend fun loadScanPage(s: KbDraftSource.FromScanPage): LoadedContext {
        val page = scanPageDao.get(s.pageId) ?: error("scan page ${s.pageId} not found")
        // RegenerateEntryUseCase passes docId = -1L because the entry doesn't store
        // it; fall back to the page's docId so re-runs work for SCAN entries.
        val effectiveDocId = if (s.docId > 0) s.docId else page.docId
        val doc = scanDocumentDao.getById(effectiveDocId)
        val docTitle = doc?.title ?: "扫描文档"
        val imageBytes = File(page.enhancedImagePath).readBytes()
        val staged = imageStore.stageCopy(page.enhancedImagePath)
        val text = "请识别图中内容并归档为一张知识卡片。如果是题目，按题目处理（isQuestion=true）。文档标题：$docTitle"
        val messages = listOf(LlmMessage(LlmRole.USER, text, images = listOf(imageBytes)))
        return LoadedContext(messages, staged, fallbackTitle = docTitle)
    }

    companion object {
        /** Soft cap on conversation chars sent to the LLM in CHAT_SESSION mode. ~6 k chars ≈ 4 k tokens for CJK. */
        private const val TOTAL_CHARS_BUDGET = 6_000
    }
}
