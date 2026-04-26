package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateEntryUseCaseTest {
    @Test fun forwardsToRepository() = runTest {
        val repo = FakeKnowledgeRepository()
        val e = KbEntry(
            id = 1, title = "T", categoryId = null, categoryName = null,
            source = KbSource.CHAT_MESSAGE, sourceChatMessageId = 1, sourceChatSessionId = 1,
            sourceScanPageId = null, originalImagePath = null,
            standardizedQuestion = null, summaryMarkdown = "## …",
            createdAt = 0, updatedAt = 0,
        )
        UpdateEntryUseCase(repo)(e)
        assertEquals(e, repo.lastUpdatedEntry)
    }
}
