package com.example.personal_studio.feature.bitimport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.domain.bitimport.SyncAllUseCase
import com.example.personal_studio.domain.bitimport.model.SyncAllProgress
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.domain.bitimport.model.SyncSourceState
import com.example.personal_studio.domain.bitimport.model.SyncSourceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncAllViewModel @Inject constructor(
    private val syncAll: SyncAllUseCase,
    private val loginPrefs: LoginPrefs,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        SyncAllProgress(
            states = SyncSource.values().associateWith { SyncSourceState(SyncSourceStatus.PENDING) },
            done = false,
        )
    )
    val ui: StateFlow<SyncAllProgress> = _ui.asStateFlow()

    private var job: Job? = null

    init { start() }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            syncAll.run().collect { _ui.value = it }
            loginPrefs.setFirstSyncDone(true)
        }
    }

    /** 重试:整体重跑(M1 简单起见,已 OK 的也会再拉)。仅在已 done 时可点。 */
    fun retry() { if (_ui.value.done) start() }

    /** 跳过:取消余下拉取,标记完成,置 firstSyncDone。 */
    fun skip() {
        job?.cancel()
        viewModelScope.launch { loginPrefs.setFirstSyncDone(true) }
        _ui.update { it.copy(done = true) }
    }
}
