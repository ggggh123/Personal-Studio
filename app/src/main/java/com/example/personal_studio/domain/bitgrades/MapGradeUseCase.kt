package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.dto.GradeRowDto
import javax.inject.Inject

/** GradeRowDto → GradeEntryEntity。课程名或学期缺失则丢弃(返回 null)。 */
class MapGradeUseCase @Inject constructor() {

    fun invoke(dto: GradeRowDto, fetchedAt: Long): GradeEntryEntity? {
        val name = dto.courseName?.takeIf { it.isNotBlank() } ?: return null
        val term = dto.termCode?.takeIf { it.isNotBlank() } ?: return null
        val score = dto.score?.trim().orEmpty()
        return GradeEntryEntity(
            termCode = term,
            termName = dto.termName?.takeIf { it.isNotBlank() } ?: term,
            courseName = name,
            courseCode = dto.courseCode.orEmpty(),
            credit = dto.credit ?: 0.0,
            score = score,
            gradePoint = dto.gradePoint,
            gradeLetter = dto.gradeLetter,
            category = dto.category,
            attemptType = dto.attemptType?.takeIf { it.isNotBlank() } ?: "正常",
            isPass = computePass(dto.gradePoint, score, dto.gradeLetter),
            fetchedAt = fetchedAt,
        )
    }

    private fun computePass(point: Double?, score: String, letter: String?): Boolean {
        if (point != null) return point > 0.0
        val passWords = listOf("优", "良", "中", "及格", "合格", "通过")
        if (letter != null && passWords.any { it in letter }) return true
        if (passWords.any { it in score }) return true
        if ("不及格" in score || "不合格" in score || "缺考" in score) return false
        score.toDoubleOrNull()?.let { return it >= 60.0 }
        return true // 未知 → 不武断判挂科
    }
}
