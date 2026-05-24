package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.model.MessageRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartGradeChatUseCaseTest {
    @Test fun `creates session and seeds system context`() = runTest {
        val repo = mockk<ChatRepository> {
            coEvery { createSession(any()) } returns 42L
            coEvery { appendMessage(any(), any(), any(), any(), any(), any(), any()) } returns 1L
        }
        val book = GradeBook(
            terms = listOf(TermGrades("2024-2025-2", "24春", emptyList(), 3.5, null)),
            overallGpa = 3.5, totalCredits = 30.0, overallRank = null,
        )
        val sid = StartGradeChatUseCase(repo, BuildGradeSummaryUseCase()).invoke(book)
        assertEquals(42L, sid)
        coVerify { repo.createSession(match { it.contains("成绩") }) }
        coVerify { repo.appendMessage(42L, MessageRole.SYSTEM, any(), null, any(), any(), any()) }
    }
}
