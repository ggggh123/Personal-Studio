package com.example.personal_studio.feature.knowledge.vm

import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbCategory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KbHomeViewModelTest {

    private val now = System.currentTimeMillis()

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun mkEntry(id: Long, catId: Long? = 1, mistake: Boolean = false) = KbEntry(
        id = id, title = "T$id", categoryId = catId, categoryName = "数学",
        source = KbSource.CHAT_MESSAGE, sourceChatMessageId = id, sourceChatSessionId = 1,
        sourceScanPageId = null, originalImagePath = null,
        standardizedQuestion = if (mistake) "Q" else null,
        summaryMarkdown = "## 核心概念\n", createdAt = now, updatedAt = now,
    )

    @Test fun observesEntries_categories_and_counts() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.allEntries.value = listOf(mkEntry(1), mkEntry(2, mistake = true))
        repo.categories.value = listOf(KbCategory(1, "数学", true), KbCategory(2, "物理", true))
        repo.notesCount.value = 1
        repo.mistakesCount.value = 1
        repo.categoryCounts.value = mapOf(1L to 1, 2L to 0)

        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            // First emission may be the initial empty state from stateIn's seed; skip until populated.
            var s = awaitItem()
            while (s.notesCount == 0 && s.entries.isEmpty()) {
                s = awaitItem()
            }
            assertEquals(1, s.notesCount)
            assertEquals(1, s.mistakesCount)
            assertEquals(2, s.categories.size)
            assertEquals(2, s.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun toggleNotes_flipsShowNotesFlag() = runTest {
        val repo = FakeKnowledgeRepository()
        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            var s = awaitItem()
            // Initial showNotes is true.
            assertTrue(s.showNotes)
            vm.onToggleNotes()
            // Drain emissions until the flag actually flipped.
            while (s.showNotes) s = awaitItem()
            assertEquals(false, s.showNotes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun selectCategory_setsFilter() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.categories.value = listOf(KbCategory(1, "数学", true))
        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            awaitItem()
            vm.onSelectCategory(1L)
            var s = awaitItem()
            while (s.selectedCategoryId == null) s = awaitItem()
            assertEquals(1L, s.selectedCategoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
