package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeChatRepository
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.data.repository.FakeScanRepository
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KbEntryDetailViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun emitsEntry_whenRepoEmits() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            entryFlow.value = KbEntry(
                id = 7, title = "T", categoryId = 1, categoryName = "数学",
                source = KbSource.CHAT_MESSAGE, sourceChatMessageId = 1, sourceChatSessionId = 1,
                sourceScanPageId = null, originalImagePath = null,
                standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
                createdAt = 0, updatedAt = 0,
            )
        }
        val vm = KbEntryDetailViewModel(
            repo = repo,
            updateUseCase = com.example.personal_studio.domain.knowledge.UpdateEntryUseCase(repo),
            deleteUseCase = com.example.personal_studio.domain.knowledge.DeleteEntryUseCase(repo),
            regenerateUseCase = com.example.personal_studio.domain.knowledge.RegenerateEntryUseCase(repo),
            chatRepo = com.example.personal_studio.data.repository.FakeChatRepository(),
            scanRepo = com.example.personal_studio.data.repository.FakeScanRepository(),
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
        )
        // stateIn(WhileSubscribed) only starts the upstream once a collector subscribes,
        // so we must drive subscription via Turbine — same convention as KbHomeViewModelTest.
        vm.uiState.test {
            var s = awaitItem()
            while (s !is KbEntryDetailUiState.Loaded) s = awaitItem()
            assertEquals("T", s.entry.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun entry(source: KbSource, sessionId: Long? = null, pageId: Long? = null) = KbEntry(
        id = 7, title = "T", categoryId = null, categoryName = null, source = source,
        sourceChatMessageId = null, sourceChatSessionId = sessionId, sourceScanPageId = pageId,
        originalImagePath = null, standardizedQuestion = null, summaryMarkdown = "", createdAt = 0, updatedAt = 0,
    )

    private fun vm(repo: FakeKnowledgeRepository, chat: FakeChatRepository, scans: FakeScanRepository) = KbEntryDetailViewModel(
        repo = repo,
        updateUseCase = com.example.personal_studio.domain.knowledge.UpdateEntryUseCase(repo),
        deleteUseCase = com.example.personal_studio.domain.knowledge.DeleteEntryUseCase(repo),
        regenerateUseCase = com.example.personal_studio.domain.knowledge.RegenerateEntryUseCase(repo),
        chatRepo = chat,
        scanRepo = scans,
        savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
    )

    @Test fun sourceLinksToTheConversation_orSaysItWasDeleted() = runTest {
        val chat = FakeChatRepository()
        val sid = chat.createSession("数列极限")
        val repo = FakeKnowledgeRepository().apply { entryFlow.value = entry(KbSource.CHAT_SESSION, sessionId = sid) }
        vm(repo, chat, FakeScanRepository()).source.test {
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertEquals(KbSourceLink("问答 · 数列极限", KbSourceTarget.Chat(sid)), s)
            cancelAndIgnoreRemainingEvents()
        }
        chat.deleteSession(sid)
        vm(repo, chat, FakeScanRepository()).source.test {
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertEquals(KbSourceLink("原对话已删除", null), s)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun sourceLinksToTheScannedPage() = runTest {
        val scans = FakeScanRepository()
        val doc = scans.createPendingDocument("讲义")
        scans.appendPage(doc, "o1", "e1", ScanFilter.COLOR, null)
        val second = scans.appendPage(doc, "o2", "e2", ScanFilter.COLOR, null)
        val repo = FakeKnowledgeRepository().apply { entryFlow.value = entry(KbSource.SCAN, pageId = second) }
        vm(repo, FakeChatRepository(), scans).source.test {
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertEquals(KbSourceLink("扫描 · 讲义 第 2 页", KbSourceTarget.ScanPage(second)), s)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
