package com.example.personal_studio.domain.bitimport.model

/** 一键批量同步:四个源 + 各自状态 + 进度快照。 */
enum class SyncSource { COURSES, DDL, EXAMS, GRADES }

enum class SyncSourceStatus { PENDING, RUNNING, OK, FAILED }

/** Why a source failed, so the sync panel can say what to do next. */
enum class SyncFailure {
    /** The school asks for interactive (web / SMS) verification. */
    VERIFY,
    /** Saved password rejected, e.g. changed on the school side. */
    PASSWORD,
    LOCKED,
    /** The academic system blocks queries until course evaluation is done. */
    REVIEW,
    NETWORK,
    /** The school returned nothing; local data was left untouched. */
    EMPTY,
    OTHER,
}

data class SyncSourceState(
    val status: SyncSourceStatus,
    val detail: String? = null,
    val failure: SyncFailure? = null,
)

data class SyncAllProgress(
    val states: Map<SyncSource, SyncSourceState>,
    val done: Boolean,
    val noCredentials: Boolean = false,
) {
    val failed: Set<SyncSource> get() = states.filterValues { it.status == SyncSourceStatus.FAILED }.keys
    val anySucceeded: Boolean get() = states.values.any { it.status == SyncSourceStatus.OK }

    /** The source running now, for "第 n 项，共 m 项". */
    val running: SyncSource? get() = states.entries.firstOrNull { it.value.status == SyncSourceStatus.RUNNING }?.key
}
