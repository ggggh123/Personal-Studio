package com.example.personal_studio.feature.chat.vm

import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `sessions flow maps summaries to ui state`() = runTest {
        val repo = FakeChatRepository()
        val s1 = repo.createSession("alpha")
        val vm = ChatListViewModel(repo)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.sessions.size)
            assertEquals(s1, state.sessions[0].id)
            assertEquals("alpha", state.sessions[0].title)
            assertEquals(0, state.sessions[0].msgCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `onRename updates title`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("old")
        val vm = ChatListViewModel(repo)
        vm.onRename(id, "新名字")
        assertEquals("新名字", repo.getSession(id)?.title)
    }

    @Test fun `onDelete removes session`() = runTest {
        val repo = FakeChatRepository()
        val id = repo.createSession("x")
        val vm = ChatListViewModel(repo)
        vm.onDelete(id)
        assertEquals(0, repo.countSessions())
    }

    @Test fun `createNewSession yields new id via callback and persists`() = runTest {
        val repo = FakeChatRepository()
        val vm = ChatListViewModel(repo)
        var createdId: Long? = null
        vm.createNewSession { createdId = it }
        assertNotNull(createdId)
        assertEquals(1, repo.countSessions())
    }
}
