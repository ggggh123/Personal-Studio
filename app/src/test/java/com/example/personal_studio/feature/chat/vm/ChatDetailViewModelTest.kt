package com.example.personal_studio.feature.chat.vm

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import com.example.personal_studio.data.repository.FakeChatRepository
import com.example.personal_studio.domain.chat.GenerateTitleUseCase
import com.example.personal_studio.domain.chat.SendMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `sending a message produces streaming then persisted messages`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession("test")
        val llm = FakeLLMProvider(textChunks = listOf("hel", "lo world"))
        val vm = ChatDetailViewModel(
            sessionId = sid,
            repo = repo,
            send = SendMessageUseCase(repo, llm),
            titleGen = GenerateTitleUseCase(repo, llm),
            prefs = FakeUserPreferencesRepository(),
        )
        vm.uiState.test {
            awaitItem()  // initial empty

            vm.onInputChanged("hi")
            vm.onSend()

            var sawStreaming = false
            var sawPersisted = false
            while (!sawPersisted) {
                val s = awaitItem()
                if (s.streamingText != null) sawStreaming = true
                if (s.streamingText == null && s.messages.any {
                    it.role == com.example.personal_studio.domain.model.MessageRole.AI
                }) {
                    sawPersisted = true
                }
            }
            assertTrue(sawStreaming)

            val finalState = expectMostRecentItem()
            assertEquals(2, finalState.messages.size)
            assertEquals("hello world", finalState.messages.last().contentMarkdown)
            assertNull(finalState.streamingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `error during streaming surfaces as errorBanner`() = runTest {
        val repo = FakeChatRepository()
        val sid = repo.createSession("x")
        val llm = object : com.example.personal_studio.data.remote.llm.LLMProvider {
            override val name = "fake-err"
            override fun generate(
                messages: List<com.example.personal_studio.data.remote.llm.LlmMessage>,
                temperature: Float,
            ) = kotlinx.coroutines.flow.flow {
                emit(com.example.personal_studio.data.remote.llm.LlmChunk.Error("boom", true))
            }
            override suspend fun generateStructured(
                messages: List<com.example.personal_studio.data.remote.llm.LlmMessage>,
                jsonSchema: String,
                temperature: Float,
            ): String = error("test fake — not used")
        }
        val vm = ChatDetailViewModel(
            sessionId = sid,
            repo = repo,
            send = SendMessageUseCase(repo, llm),
            titleGen = GenerateTitleUseCase(repo, llm),
            prefs = FakeUserPreferencesRepository(),
        )
        vm.uiState.test {
            awaitItem()
            vm.onInputChanged("hi")
            vm.onSend()
            var banner: String? = null
            while (banner == null) {
                banner = awaitItem().errorBanner
            }
            assertEquals("boom", banner)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
