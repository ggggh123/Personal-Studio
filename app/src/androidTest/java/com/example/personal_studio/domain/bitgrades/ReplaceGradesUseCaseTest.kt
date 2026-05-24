package com.example.personal_studio.domain.bitgrades

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplaceGradesUseCaseTest {
    private lateinit var db: AppDatabase
    private lateinit var useCase: ReplaceGradesUseCase

    private fun grade(term: String, code: String, attempt: String = "正常") = GradeEntryEntity(
        termCode = term, termName = term, courseName = code, courseCode = code,
        credit = 3.0, score = "90", gradePoint = 4.0, gradeLetter = "A",
        category = "必修", attemptType = attempt, isPass = true, fetchedAt = 1L,
    )
    private fun rank(term: String) = TermRankEntity(term, term, 3.8, 1, 30, 5, 100, 1L)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        useCase = ReplaceGradesUseCase(db.gradesDao())
    }
    @After fun tearDown() = db.close()

    @Test fun replace_clears_then_writes() = runBlocking {
        useCase.invoke(listOf(grade("2023-2024-1", "OLD")), listOf(rank("2023-2024-1")))
        useCase.invoke(
            listOf(grade("2024-2025-1", "NEW1"), grade("2024-2025-2", "NEW2")),
            listOf(rank("2024-2025-1"), rank("OVERALL")),
        )
        val all = db.gradesDao().listAll()
        assertEquals(2, all.size)
        assertEquals(setOf("NEW1", "NEW2"), all.map { it.courseCode }.toSet())
        assertEquals(2, db.gradesDao().listRanks().size)
    }
}
