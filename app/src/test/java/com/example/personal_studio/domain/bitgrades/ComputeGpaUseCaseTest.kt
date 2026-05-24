package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeGpaUseCaseTest {
    private val useCase = ComputeGpaUseCase()
    private fun g(term: String, code: String, credit: Double, point: Double?) =
        GradeEntryEntity(0, term, term, code, code, credit, "x", point, null, null, "正常", true, 1L)

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
}
