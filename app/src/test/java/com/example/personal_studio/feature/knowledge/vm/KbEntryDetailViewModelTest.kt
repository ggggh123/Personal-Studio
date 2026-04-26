package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
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
        val vm = KbEntryDetailViewModel(repo, SavedStateHandle(mapOf("entryId" to 7L)))
        // stateIn(WhileSubscribed) only starts the upstream once a collector subscribes,
        // so we must drive subscription via Turbine — same convention as KbHomeViewModelTest.
        vm.uiState.test {
            var s = awaitItem()
            while (s !is KbEntryDetailUiState.Loaded) s = awaitItem()
            assertEquals("T", s.entry.title)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
