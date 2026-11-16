package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitimport.model.SyncAllProgress
import com.example.personal_studio.domain.bitimport.model.SyncSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place a 一键同步 runs, shared by the sync panel, the 今日 pill and the first-sync
 * page. It outlives the screens (closing the panel does not stop a sync) and remembers the
 * last result so "只重试失败项" re-runs just the failed sources. Records the time of every
 * sync in which at least one source succeeded.
 */
@Singleton
class SyncCenter internal constructor(
    private val syncAll: SyncAllUseCase,
    private val loginPrefs: LoginPrefs,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    @Inject
    constructor(syncAll: SyncAllUseCase, loginPrefs: LoginPrefs) :
        this(syncAll, loginPrefs, CoroutineScope(SupervisorJob() + Dispatchers.Default), System::currentTimeMillis)

    private val _progress = MutableStateFlow<SyncAllProgress?>(null)

    /** Latest snapshot; null until the first sync of this process. */
    val progress: StateFlow<SyncAllProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts a full sync unless one is already running. */
    fun startAll() = launch(SyncSource.values().toSet())

    /** Re-runs only the sources that failed last time, keeping the others' results. */
    fun retryFailed() {
        val failed = _progress.value?.failed.orEmpty()
        if (failed.isNotEmpty()) launch(failed)
    }

    /** Stops a running sync (first-sync "跳过"); what finished stays saved. */
    fun cancel() {
        job?.cancel()
        _progress.value = _progress.value?.copy(done = true)
        scope.launch { loginPrefs.setFirstSyncDone(true) }
    }

    private fun launch(only: Set<SyncSource>) {
        if (isRunning) return
        val previous = _progress.value?.states.orEmpty()
        job = scope.launch {
            syncAll.run(only, previous).collect { _progress.value = it }
            val last = _progress.value ?: return@launch
            if (last.noCredentials) return@launch
            loginPrefs.setFirstSyncDone(true)
            if (last.anySucceeded) loginPrefs.setLastSyncAt(clock())
        }
    }
}
