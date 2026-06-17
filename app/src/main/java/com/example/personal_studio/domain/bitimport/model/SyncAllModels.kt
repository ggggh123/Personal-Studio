package com.example.personal_studio.domain.bitimport.model

/** 一键批量同步:四个源 + 各自状态 + 进度快照。 */
enum class SyncSource { COURSES, DDL, EXAMS, GRADES }

enum class SyncSourceStatus { PENDING, RUNNING, OK, FAILED }

data class SyncSourceState(val status: SyncSourceStatus, val detail: String? = null)

data class SyncAllProgress(
    val states: Map<SyncSource, SyncSourceState>,
    val done: Boolean,
    val noCredentials: Boolean = false,
)
