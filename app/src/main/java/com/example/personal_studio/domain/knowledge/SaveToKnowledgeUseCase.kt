package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import javax.inject.Inject

/**
 * Two-phase: [draft] resolves the draft (LLM call), [commit] persists user-edited result.
 */
class SaveToKnowledgeUseCase @Inject constructor(
    private val repo: KnowledgeRepository,
) {
    suspend fun draft(source: KbDraftSource): KbEntryDraft = repo.draftFromSource(source)
    suspend fun commit(draft: KbEntryDraft): Long = repo.saveEntry(draft)
}
