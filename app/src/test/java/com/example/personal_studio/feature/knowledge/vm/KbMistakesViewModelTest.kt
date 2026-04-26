package com.example.personal_studio.feature.knowledge.vm

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
class KbMistakesViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun forwardsRepoMistakes() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            mistakes.value = listOf(
                KbEntry(1, "Q1", 1, "数学", KbSource.CHAT_MESSAGE, 1, 1, null, "/img1.jpg", "求 x", "## …", 0, 0),
                KbEntry(2, "Q2", 1, "数学", KbSource.SCAN, null, null, 9, "/img2.jpg", "证明", "## …", 0, 0),
            )
        }
        val vm = KbMistakesViewModel(repo)
        vm.uiState.test {
            // Drain initial empty state from stateIn seed.
            var s = awaitItem()
            while (s.entries.isEmpty()) s = awaitItem()
            assertEquals(2, s.entries.size)
            assertEquals(setOf(1L, 2L), s.entries.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
