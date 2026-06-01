package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import javax.inject.Inject

/**
 * 登出:清除已存凭据 + 取消两个后台轮询 + 关闭两个自动同步开关。
 * 与原 SettingsViewModel.onLogout 行为一致,抽出供 profile / settings 复用。
 */
class LogoutUseCase @Inject constructor(
    private val credPrefs: ImportCredentialPrefs,
    private val gradesSyncPrefs: GradesSyncPrefs,
    private val ddlSyncPrefs: DdlSyncPrefs,
    private val gradesPollScheduler: GradesPollScheduler,
    private val ddlPollScheduler: DdlPollScheduler,
) {
    suspend operator fun invoke() {
        credPrefs.clear()
        gradesPollScheduler.cancel()
        ddlPollScheduler.cancel()
        gradesSyncPrefs.setEnabled(false)
        ddlSyncPrefs.setEnabled(false)
    }
}
