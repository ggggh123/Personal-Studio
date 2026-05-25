package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.GpaCalculator
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.bitgrades.model.TermRank
import javax.inject.Inject

/** 库内成绩 + 排名 → GradeBook。GPA 一律由本类从成绩重算（term_ranks.weightedGpa
 *  仅作冗余存储，不作权威来源），避免漂移。 */
class ComputeGpaUseCase @Inject constructor() {

    fun invoke(entries: List<GradeEntryEntity>, ranks: List<TermRankEntity>): GradeBook {
        val rankByTerm = ranks.associateBy { it.termCode }
        val terms = entries.groupBy { it.termCode }
            .map { (code, rows) ->
                val items = rows.map { it.toItem() }
                TermGrades(
                    termCode = code,
                    termName = rows.first().termName,
                    courses = items.sortedByDescending { it.credit },
                    weightedGpa = GpaCalculator.weightedGpa(items.map { it.credit to it.gradePoint }),
                    avgScore = weightedAvgScore(rows),
                    rank = rankByTerm[code]?.toTermRank(),
                )
            }
            .sortedByDescending { it.termCode }
        val overallGpa = GpaCalculator.weightedGpa(entries.map { it.credit to it.gradePoint })
        val totalCredits = entries.filter { it.gradePoint != null }.sumOf { it.credit }
        return GradeBook(
            terms = terms,
            overallGpa = overallGpa,
            totalCredits = totalCredits,
            overallAvgScore = weightedAvgScore(entries),
            overallRank = rankByTerm["OVERALL"]?.toTermRank(),
        )
    }

    private fun weightedAvgScore(rows: List<GradeEntryEntity>): Double? {
        var sumCredit = 0.0; var sumWeighted = 0.0
        for (r in rows) {
            val sc = com.example.personal_studio.core.util.BitGpaConverter.toScore(r.score) ?: continue
            sumCredit += r.credit; sumWeighted += r.credit * sc
        }
        return if (sumCredit == 0.0) null else sumWeighted / sumCredit
    }

    private fun GradeEntryEntity.toItem() = GradeItem(
        courseName, courseCode, credit, score, gradePoint, gradeLetter, category, attemptType, isPass,
    )
    private fun TermRankEntity.toTermRank() = TermRank(classRank, classTotal, majorRank, majorTotal)
}
