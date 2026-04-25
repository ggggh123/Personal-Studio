package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.kb.KbImageStore
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbDraftSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KnowledgeRepositoryDraftTest {

    private lateinit var fakeLlm: FakeLLMProvider
    private lateinit var loader: SourceContextLoader
    private lateinit var repo: KnowledgeRepositoryImpl

    @Before fun setup() {
        fakeLlm = FakeLLMProvider()
        loader = mockk()
        // Repo + DAO collaborators are mocked; we only test draftFromSource here.
        val entryDao = mockk<KbEntryDao>(relaxed = true).also {
            coEvery { it.recentTitles(any()) } returns listOf("二次函数", "韦达定理")
        }
        val categoryDao = mockk<KbCategoryDao>(relaxed = true).also {
            coEvery { it.observeAll() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        }
        repo = KnowledgeRepositoryImpl(
            db = mockk<AppDatabase>(relaxed = true),
            entryDao = entryDao,
            categoryDao = categoryDao,
            ftsDao = mockk<KbFtsDao>(relaxed = true),
            imageStore = mockk<KbImageStore>(relaxed = true),
            llm = fakeLlm,
            contextLoader = loader,
        )
    }

    private fun mockChatLoaderReturns(text: String) {
        coEvery { loader.load(any()) } returns SourceContextLoader.LoadedContext(
            messages = listOf(LlmMessage(LlmRole.USER, text)),
            stagedImagePath = "/tmp/staged.jpg",
            fallbackTitle = "session-X",
        )
    }

    @Test fun parsesValidJsonOnFirstTry() = runTest {
        mockChatLoaderReturns("Q+A text")
        fakeLlm.structuredResponses += Result.success(
            """{
                "title":"二次函数判别式",
                "categorySuggestion":"数学",
                "isQuestion":true,
                "standardizedQuestion":"求 Δ 的值",
                "summaryMarkdown":"## 核心概念\n…",
                "relatedEntryTitles":["二次函数"]
            }""",
        )
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertEquals("二次函数判别式", draft.title)
        assertEquals("数学", draft.categorySuggestion)
        assertEquals("求 Δ 的值", draft.standardizedQuestion)
        assertTrue(draft.summaryMarkdown.startsWith("## 核心概念"))
        assertEquals(listOf("二次函数"), draft.relatedEntryTitles)
        assertFalse(draft.isFallback)
        assertEquals("/tmp/staged.jpg", draft.originalImagePath)
    }

    @Test fun retriesOnceOnGarbage_thenSucceeds() = runTest {
        mockChatLoaderReturns("Q+A")
        fakeLlm.structuredResponses += Result.success("not json at all")
        fakeLlm.structuredResponses += Result.success(
            """{
                "title":"X","categorySuggestion":"其它","isQuestion":false,
                "standardizedQuestion":null,"summaryMarkdown":"## 核心概念\n.","relatedEntryTitles":[]
            }""",
        )
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertEquals("X", draft.title)
        assertFalse(draft.isFallback)
        assertEquals(2, fakeLlm.recordedMessages.size)
    }

    @Test fun fallsBackWhenBothCallsReturnGarbage() = runTest {
        mockChatLoaderReturns("Q+A")
        fakeLlm.structuredResponses += Result.success("garbage 1")
        fakeLlm.structuredResponses += Result.success("garbage 2")
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertTrue(draft.isFallback)
        assertEquals(KbDraftFallbackReason.JSON_PARSE_FAILED, draft.fallbackReason)
        assertEquals("session-X", draft.title)
        assertEquals("其它", draft.categorySuggestion)
        assertNotNull(draft.summaryMarkdown)
        assertTrue(draft.summaryMarkdown.contains("原始内容"))
    }

    @Test fun fallbackOnMissingRequiredFields() = runTest {
        mockChatLoaderReturns("Q+A")
        // valid JSON but missing summaryMarkdown
        fakeLlm.structuredResponses += Result.success("""{"title":"X"}""")
        fakeLlm.structuredResponses += Result.success("""{"title":"Y"}""")
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertTrue(draft.isFallback)
        assertEquals(KbDraftFallbackReason.MISSING_REQUIRED_FIELDS, draft.fallbackReason)
    }
}
