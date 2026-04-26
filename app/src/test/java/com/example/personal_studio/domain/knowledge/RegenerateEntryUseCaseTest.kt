package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegenerateEntryUseCaseTest {

    @Test fun overwritesSummaryAndStdQuestion_keepsTitleAndCategory() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            // Repo emits new draft from LLM:
            draftToReturn = KbEntryDraft(
                source = KbDraftSource.FromChatMessage(1, 2),
                title = "AI 新标题",                  // SHOULD be ignored
                categorySuggestion = "AI 新分类",     // SHOULD be ignored
                standardizedQuestion = "新规范化题目",
                summaryMarkdown = "## 核心概念\n新内容",
                relatedEntryTitles = listOf("ignored"),
                originalImagePath = null,
            )
        }
        val original = KbEntry(
            id = 7,
            title = "用户已 rename",                  // SHOULD be preserved
            categoryId = 1,                          // SHOULD be preserved
            categoryName = "用户已 recategorize",
            source = KbSource.CHAT_MESSAGE,
            sourceChatMessageId = 2,
            sourceChatSessionId = 1,
            sourceScanPageId = null,
            originalImagePath = null,
            standardizedQuestion = "旧题目",
            summaryMarkdown = "旧摘要",
            createdAt = 0,
            updatedAt = 0,
        )
        RegenerateEntryUseCase(repo).invoke(original)
        val saved = repo.lastUpdatedEntry!!
        assertEquals("用户已 rename", saved.title)              // preserved
        assertEquals(1L, saved.categoryId)                       // preserved
        assertEquals("新规范化题目", saved.standardizedQuestion)  // overwritten
        assertEquals("## 核心概念\n新内容", saved.summaryMarkdown) // overwritten
    }
}
