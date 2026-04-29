package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveToKnowledgeUseCaseTest {

    @Test fun draft_delegatesToRepository() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            draftToReturn = KbEntryDraft(
                source = KbDraftSource.FromChatMessage(1, 2),
                title = "T", categorySuggestion = "数学",
                standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
                relatedEntryTitles = emptyList(), originalImagePath = null,
            )
        }
        val useCase = SaveToKnowledgeUseCase(repo)
        val draft = useCase.draft(KbDraftSource.FromChatMessage(1, 2))
        assertEquals("T", draft.title)
        assertEquals(KbDraftSource.FromChatMessage(1, 2), repo.lastDraftSource)
    }

    @Test fun commit_delegatesToRepositoryAndReturnsId() = runTest {
        val repo = FakeKnowledgeRepository().apply { savedEntryIdToReturn = 42L }
        val draft = KbEntryDraft(
            source = KbDraftSource.FromChatMessage(1, 2),
            title = "T", categorySuggestion = "数学",
            standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
            relatedEntryTitles = emptyList(), originalImagePath = null,
        )
        val useCase = SaveToKnowledgeUseCase(repo)
        val id = useCase.commit(draft)
        assertEquals(42L, id)
        assertEquals(draft, repo.lastSavedDraft)
    }
}
