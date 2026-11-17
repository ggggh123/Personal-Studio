package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.bitddl.model.DdlSyncOutcome
import javax.inject.Inject

/**
 * 当前 DDL 与上次 UID 集求差,产出“本次新增”。按平台建基线:某平台第一次同步只建基线不通知
 * (所以接入新乐学后,第一次不会把它已有的作业都当成新作业)。没同步上的平台保留上次的 UID,
 * 下次成功时不会被误报为新增。
 */
class DetectNewDdlUseCase @Inject constructor(
    private val prefs: DdlSyncPrefs,
) {
    suspend fun invoke(outcome: DdlSyncOutcome): DdlDiffResult {
        val state = prefs.snapshot()
        val lastSeen = state.lastSeenUids
        // Before per-platform baselines, any stored UID meant 乐学 had one.
        val baselined = state.baselinedSources.mapNotNull { name -> DdlSource.entries.firstOrNull { it.name == name } }
            .toSet() + (if (lastSeen.any { DdlSource.ofUid(it) == DdlSource.LEXUE }) setOf(DdlSource.LEXUE) else emptySet())
        val succeeded = outcome.succeeded
        val newEvents = outcome.events.filter { it.source in baselined && it.uid !in lastSeen }
        val kept = lastSeen.filter { DdlSource.ofUid(it) !in succeeded }
        return DdlDiffResult(
            newEvents = newEvents,
            fullUids = kept.toSet() + outcome.events.map { it.uid },
            isFirstRun = succeeded.none { it in baselined },
            baselined = baselined + succeeded,
        )
    }

    /** One platform's events only (乐学, as before 新乐学 joined). */
    suspend fun invoke(current: List<DdlEvent>): DdlDiffResult =
        invoke(DdlSyncOutcome(mapOf(DdlSource.LEXUE to BackgroundDdlResult.Ok(current))))
}

data class DdlDiffResult(
    val newEvents: List<DdlEvent>,
    val fullUids: Set<String>,
    /** No platform that synced this time had a baseline yet. */
    val isFirstRun: Boolean,
    /** Platforms with a baseline after this sync; store with [DdlSyncPrefs.setBaselinedSources]. */
    val baselined: Set<DdlSource> = emptySet(),
)
