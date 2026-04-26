package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKbUseCaseTest {

    private fun mk(id: Long) = KbEntry(
        id = id, title = "T$id", categoryId = null, categoryName = null,
        source = KbSource.CHAT_MESSAGE, sourceChatMessageId = 1, sourceChatSessionId = 1,
        sourceScanPageId = null, originalImagePath = null,
        standardizedQuestion = null, summaryMarkdown = "## …", createdAt = 0, updatedAt = 0,
    )

    @Test fun returnsAndResults_whenEnoughHits() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.searchResults.value = (1L..6L).map { mk(it) }       // AND has ≥ 5 hits
        repo.orSearchResults = (1L..20L).map { mk(it) }          // not used because AND was sufficient
        val out = SearchKbUseCase(repo).invoke("any").first()
        assertEquals(6, out.size)
    }

    @Test fun fallsBackToOr_whenAndHitsLessThan5() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.searchResults.value = listOf(mk(1L), mk(2L))         // AND has < 5
        repo.orSearchResults = (1L..10L).map { mk(it) }           // OR returns more
        val out = SearchKbUseCase(repo).invoke("any").first()
        assertEquals(10, out.size)
    }

    @Test fun emptyQueryReturnsEmpty() = runTest {
        val repo = FakeKnowledgeRepository()
        val out = SearchKbUseCase(repo).invoke("").first()
        assertEquals(0, out.size)
    }
}
