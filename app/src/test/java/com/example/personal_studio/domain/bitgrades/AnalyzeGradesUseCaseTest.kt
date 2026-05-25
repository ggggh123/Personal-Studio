package com.example.personal_studio.domain.bitgrades

import app.cash.turbine.test
import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzeGradesUseCaseTest {
    @Test fun `streams text deltas from llm`() = runTest {
        val llm = mockk<LLMProvider> {
            every { generate(any(), any()) } returns flowOf(
                LlmChunk.Text("你的"), LlmChunk.Text("趋势向好"), LlmChunk.Done(0),
            )
        }
        val book = GradeBook(emptyList(), 3.5, 30.0, null, null)
        val out = StringBuilder()
        AnalyzeGradesUseCase(llm, BuildGradeSummaryUseCase()).invoke(book).test {
            out.append(awaitItem()); out.append(awaitItem()); awaitComplete()
        }
        assertEquals("你的趋势向好", out.toString())
    }
}
