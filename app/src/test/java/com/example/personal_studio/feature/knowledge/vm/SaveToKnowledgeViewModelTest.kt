package com.example.personal_studio.feature.knowledge.vm

import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.knowledge.SaveToKnowledgeUseCase
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaveToKnowledgeViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startDraft_emitsLoadingThenPreview() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            draftToReturn = sampleDraft()
        }
        val vm = SaveToKnowledgeViewModel(SaveToKnowledgeUseCase(repo))

        vm.uiState.test {
            assertEquals(SaveToKnowledgeUiState.Idle, awaitItem())
            vm.startDraft(KbDraftSource.FromChatMessage(1, 2))
            assertTrue(awaitItem() is SaveToKnowledgeUiState.Loading)
            val preview = awaitItem() as SaveToKnowledgeUiState.Preview
            assertEquals("T", preview.draft.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun startDraft_networkError_emitsErrorState() = runTest {
        val repo = object : FakeKnowledgeRepository() {
            override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft {
                throw java.io.IOException("net down")
            }
        }
        val vm = SaveToKnowledgeViewModel(SaveToKnowledgeUseCase(repo))
        vm.uiState.test {
            assertEquals(SaveToKnowledgeUiState.Idle, awaitItem())
            vm.startDraft(KbDraftSource.FromChatMessage(1, 2))
            assertTrue(awaitItem() is SaveToKnowledgeUiState.Loading)
            val err = awaitItem() as SaveToKnowledgeUiState.Error
            assertTrue(err.message.contains("net down"))
        }
    }

    @Test fun commit_emitsSavingThenSavedWithEntryId() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            draftToReturn = sampleDraft()
            savedEntryIdToReturn = 99L
        }
        val vm = SaveToKnowledgeViewModel(SaveToKnowledgeUseCase(repo))
        vm.startDraft(KbDraftSource.FromChatMessage(1, 2))
        vm.uiState.test {
            // Drain the latest Preview
            val preview = awaitItem() as SaveToKnowledgeUiState.Preview
            vm.commit(preview.draft)
            assertTrue(awaitItem() is SaveToKnowledgeUiState.Saving)
            val saved = awaitItem() as SaveToKnowledgeUiState.Saved
            assertEquals(99L, saved.entryId)
        }
    }

    private fun sampleDraft() = KbEntryDraft(
        source = KbDraftSource.FromChatMessage(1, 2),
        title = "T", categorySuggestion = "数学",
        standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
        relatedEntryTitles = emptyList(), originalImagePath = null,
    )
}
