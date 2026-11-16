package com.example.personal_studio.feature.bitimport.ui

import androidx.lifecycle.ViewModel
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitimport.SyncCenter
import com.example.personal_studio.domain.bitimport.model.SyncAllProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Sync progress for the sync panel and the first-sync page; the work itself runs in
 * [SyncCenter] so it continues when the panel closes.
 */
@HiltViewModel
class SyncAllViewModel @Inject constructor(
    private val center: SyncCenter,
    loginPrefs: LoginPrefs,
) : ViewModel() {

    val lastSyncAt: kotlinx.coroutines.flow.Flow<Long?> = loginPrefs.observeLastSyncAt

    val progress: StateFlow<SyncAllProgress?> = center.progress

    val isRunning: Boolean get() = center.isRunning

    fun startAll() = center.startAll()

    fun retryFailed() = center.retryFailed()

    /** 跳过:停止余下来源,标记首次同步已完成。 */
    fun skip() = center.cancel()
}
