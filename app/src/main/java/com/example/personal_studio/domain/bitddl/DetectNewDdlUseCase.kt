package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import javax.inject.Inject

/** 当前 DDL 与上次 UID 集求差,产出“本次新增”。首次只建基线不通知。 */
class DetectNewDdlUseCase @Inject constructor(
    private val prefs: DdlSyncPrefs,
) {
    suspend fun invoke(current: List<DdlEvent>): DdlDiffResult {
        val currentUids = current.map { it.uid }.toSet()
        val lastSeen = prefs.snapshot().lastSeenUids
        val isFirstRun = lastSeen.isEmpty()
        val newEvents = if (isFirstRun) emptyList() else {
            val newUids = currentUids - lastSeen
            current.filter { it.uid in newUids }
        }
        return DdlDiffResult(newEvents, currentUids, isFirstRun)
    }
}

data class DdlDiffResult(
    val newEvents: List<DdlEvent>,
    val fullUids: Set<String>,
    val isFirstRun: Boolean,
)
