package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import javax.inject.Inject

/** 当前 entries 与上次签名集求差,产出"本次新增"条目。
 *  签名:`"$termCode|$courseCode|$attemptType|$score"`。
 *  分数变更/重修出分都会产生新签名 → 触发通知。 */
class DetectNewGradesUseCase @Inject constructor(
    private val prefs: GradesSyncPrefs,
) {
    suspend fun invoke(currentEntries: List<GradeEntryEntity>): DiffResult {
        val currentSig = currentEntries.map(::signatureOf).toSet()
        val lastSig = prefs.snapshot().lastSeenSignature
        val isFirstRun = lastSig.isEmpty()
        // 首次运行只建立基线,不把全部历史成绩当作"新增"去通知。
        val newEntries = if (isFirstRun) emptyList() else {
            val newSigs = currentSig - lastSig
            currentEntries.filter { signatureOf(it) in newSigs }
        }
        return DiffResult(
            newEntries = newEntries,
            fullSignature = currentSig,
            isFirstRun = isFirstRun,
        )
    }
    companion object {
        fun signatureOf(e: GradeEntryEntity): String =
            "${e.termCode}|${e.courseCode}|${e.attemptType}|${e.score}"
    }
}

data class DiffResult(
    val newEntries: List<GradeEntryEntity>,
    val fullSignature: Set<String>,
    val isFirstRun: Boolean,
)
