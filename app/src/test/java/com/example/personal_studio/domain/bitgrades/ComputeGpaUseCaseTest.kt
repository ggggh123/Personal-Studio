package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeGpaUseCaseTest {
    private val useCase = ComputeGpaUseCase()
    private fun g(term: String, code: String, credit: Double, point: Double?) =
        GradeEntryEntity(0, term, term, code, code, credit, "x", point, null, null, "正常", true, 1L)
    private fun gs(term: String, code: String, credit: Double, score: String, point: Double?) =
        GradeEntryEntity(0, term, term, code, code, credit, score, point, null, null, "正常", true, 1L)

    @Test fun `groups by term newest-first and computes per-term + overall gpa`() {
        val book = useCase.invoke(
            entries = listOf(
                g("2024-2025-1", "A", 4.0, 3.0),
                g("2024-2025-2", "B", 2.0, 4.0),
            ),
            ranks = listOf(TermRankEntity("2024-2025-2", "24春", 4.0, 1, 30, 5, 100, 1L)),
        )
        assertEquals(listOf("2024-2025-2", "2024-2025-1"), book.terms.map { it.termCode })
        assertEquals(4.0, book.terms[0].weightedGpa, 0.001)
        assertEquals(5, book.terms[0].rank!!.majorRank)
        // overall = (4×3 + 2×4)/(4+2) = 20/6 = 3.333
        assertEquals(3.3333, book.overallGpa, 0.001)
        assertEquals(6.0, book.totalCredits, 0.001)
    }

    @Test fun `term without rank row has null rank`() {
        val book = useCase.invoke(listOf(g("t", "A", 3.0, 3.0)), emptyList())
        assertEquals(null, book.terms[0].rank)
    }

    @Test fun `computes credit-weighted avg score (overall + per-term)`() {
        val book = useCase.invoke(
            entries = listOf(
                gs("2024-2025-1", "A", 4.0, "90", 4.0),
                gs("2024-2025-1", "B", 2.0, "80", 3.25),
            ),
            ranks = emptyList(),
        )
        // overall = (90×4 + 80×2)/(4+2) = (360+160)/6 = 86.667
        assertEquals(86.6667, book.overallAvgScore!!, 0.001)
        assertEquals(86.6667, book.terms[0].avgScore!!, 0.001)
    }

    @Test fun `avg score is null when no numeric scores`() {
        // g() uses score="x" → toScore null → excluded → null avg
        val book = useCase.invoke(listOf(g("t", "A", 3.0, 3.0)), emptyList())
        assertEquals(null, book.overallAvgScore)
        assertEquals(null, book.terms[0].avgScore)
    }

    private fun rankEntry(major: String?, credit: Double = 3.0) = GradeEntryEntity(
        termCode = "2024", termName = "2024", courseName = "c", courseCode = "c$major$credit",
        credit = credit, score = "85", gradePoint = 3.0, gradeLetter = null, category = null,
        attemptType = "正常", isPass = true, fetchedAt = 0L, majorRankText = major, majorSize = 30,
    )

    @Test fun `book carries major rank estimate when percentiles present`() {
        val entries = listOf(rankEntry("10%"), rankEntry("12%"), rankEntry("15%"))
        val book = useCase.invoke(entries, emptyList())
        assertNotNull(book.overallMajorRankEst)
        assertTrue(book.overallMajorRankEst!!.pointPercent in 1.0..99.0)
    }

    @Test fun `no major rank estimate when percentiles absent`() {
        val entries = listOf(rankEntry(null), rankEntry(null))
        val book = useCase.invoke(entries, emptyList())
        assertNull(book.overallMajorRankEst)
    }
}
