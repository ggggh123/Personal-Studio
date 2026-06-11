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
        val (peerScore, peerGpa) = peerStats(entries)
        return GradeBook(
            terms = terms,
            overallGpa = overallGpa,
            totalCredits = totalCredits,
            overallAvgScore = weightedAvgScore(entries),
            overallPeerAvgScore = peerScore,
            overallPeerAvgGpa = peerGpa,
            overallRank = rankByTerm["OVERALL"]?.toTermRank(),
        )
    }

    /** 估计平均绩（peer benchmark）：用各课的 courseAvg (cjfx 平均分) 做学分加权。
     *  返回 (平均分版, 绩点版)；两者都基于有 courseAvg 的课程子集。 */
    private fun peerStats(rows: List<GradeEntryEntity>): Pair<Double?, Double?> {
        var c = 0.0; var sumScore = 0.0; var sumGpa = 0.0
        rows.forEach { r ->
            val avg = r.courseAvg ?: return@forEach
            val sigma = com.example.personal_studio.core.util.PeerGpaEstimator.estimateSigma(
                mean = avg, max = r.courseMaxScore, n = r.courseStudyCount,
            )
            val gpa = com.example.personal_studio.core.util.PeerGpaEstimator.correctedGradePoint(avg, sigma)
            c += r.credit
            sumScore += r.credit * avg
            sumGpa += r.credit * gpa
        }
        return if (c == 0.0) (null to null) else (sumScore / c) to (sumGpa / c)
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
        courseAvg = courseAvg, classRankText = classRankText, majorRankText = majorRankText,
        id = id,
        courseMaxScore = courseMaxScore, courseStudyCount = courseStudyCount,
        classSize = classSize, majorSize = majorSize, schoolRankText = schoolRankText,
    )
    private fun TermRankEntity.toTermRank() = TermRank(classRank, classTotal, majorRank, majorTotal)
}
