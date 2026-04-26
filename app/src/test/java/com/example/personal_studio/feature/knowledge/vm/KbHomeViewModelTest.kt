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

    @Test fun searchQuery_typeAndClear_swapsBetweenSearchAndBrowse() = runTest {
        val repo = FakeKnowledgeRepository()
        // Distinct fixtures so we can tell which path served the result.
        repo.allEntries.value = listOf(mkEntry(100), mkEntry(101))
        repo.searchResults.value = listOf(mkEntry(200))
        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            // Drain to populated browse state.
            var s = awaitItem()
            while (s.entries.isEmpty()) s = awaitItem()
            assertEquals(setOf(100L, 101L), s.entries.map { it.id }.toSet())
            assertEquals(false, s.isSearching)

            // Type → switches to search-mode entries.
            vm.onSearchChange("math")
            while (!s.isSearching || s.entries.singleOrNull()?.id != 200L) s = awaitItem()
            assertTrue(s.isSearching)
            assertEquals(listOf(200L), s.entries.map { it.id })

            // Clear → switches back to browse-mode entries.
            vm.onSearchChange("")
            while (s.isSearching || s.entries.map { it.id }.toSet() != setOf(100L, 101L)) {
                s = awaitItem()
            }
            assertEquals(false, s.isSearching)
            assertEquals(setOf(100L, 101L), s.entries.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rescueSearchIfSparse_setsRescuedEntries_andClearsOnNewQuery() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.searchResults.value = listOf(mkEntry(1))   // AND returns 1 (< SPARSE_THRESHOLD=5)
        repo.orSearchResults = (10L..15L).map { mkEntry(it) }  // OR returns 6 (> 1)
        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            var s = awaitItem()
            vm.onSearchChange("微积分")
            while (!s.isSearching) s = awaitItem()
            // Wait for the AND result to land in entries.
            while (s.entries.singleOrNull()?.id != 1L) s = awaitItem()

            vm.rescueSearchIfSparse()
            while (s.rescuedEntries == null) s = awaitItem()
            assertEquals(6, s.rescuedEntries!!.size)

            // Typing again must clear stale rescue.
            vm.onSearchChange("微积分 极限")
            while (s.rescuedEntries != null) s = awaitItem()
            assertEquals(null, s.rescuedEntries)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
