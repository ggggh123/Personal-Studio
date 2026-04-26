package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import javax.inject.Inject

/**
 * Re-runs the LLM against the entry's original source and overwrites only
 * the AI-derived fields ([standardizedQuestion] + [summaryMarkdown]). Title,
 * category, and relations are preserved (so user edits aren't blown away).
 *
 * For SCAN entries the [docId] isn't stored on the entry — pass -1L and
 * SourceContextLoader will derive it from the page row.
 */
class RegenerateEntryUseCase @Inject constructor(
    private val repo: KnowledgeRepository,
) {
    suspend operator fun invoke(entry: KbEntry) {
        val source = rebuildSource(entry)
            ?: error("entry ${entry.id} has incomplete source pointers")
        val freshDraft = repo.draftFromSource(source)
        repo.updateEntry(
            entry.copy(
                standardizedQuestion = freshDraft.standardizedQuestion,
                summaryMarkdown = freshDraft.summaryMarkdown,
            ),
        )
    }

    private fun rebuildSource(e: KbEntry): KbDraftSource? = when (e.source) {
        KbSource.CHAT_MESSAGE -> {
            val sid = e.sourceChatSessionId ?: return null
            val mid = e.sourceChatMessageId ?: return null
            KbDraftSource.FromChatMessage(sessionId = sid, aiMessageId = mid)
        }
        KbSource.CHAT_SESSION -> {
            val sid = e.sourceChatSessionId ?: return null
            KbDraftSource.FromChatSession(sessionId = sid)
        }
        KbSource.SCAN -> {
            val pid = e.sourceScanPageId ?: return null
            KbDraftSource.FromScanPage(docId = -1L, pageId = pid)
        }
    }
}
