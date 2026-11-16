package com.example.personal_studio.domain.auth

import com.example.personal_studio.data.local.datastore.DdlSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitddl.DdlPollScheduler
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import javax.inject.Inject
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.withExclusiveSession

/**
 * 登出:清除已存凭据与乐学日历订阅地址 + 取消两个后台轮询 + 关闭两个自动同步开关 + 清学校网页登录状态。
 * [invoke] 的 `clearSchoolData` 为 true 时同时删除本机的成绩、导入课表、作业与考试(见 [ClearSchoolDataUseCase]),
 * 换人登录时不会看到上一位的数据。
 */
class LogoutUseCase @Inject constructor(
    private val credPrefs: ImportCredentialPrefs,
    private val gradesSyncPrefs: GradesSyncPrefs,
    private val ddlSyncPrefs: DdlSyncPrefs,
    private val gradesPollScheduler: GradesPollScheduler,
    private val ddlPollScheduler: DdlPollScheduler,
    private val apiClient: BitApiClient,
    private val clearSchoolData: ClearSchoolDataUseCase,
) {
    suspend operator fun invoke(clearSchoolData: Boolean = false) {
        credPrefs.clear()
        // The calendar export address belongs to this account; it is fetched again after login.
        ddlSyncPrefs.clearIcalUrl()
        gradesPollScheduler.cancel()
        ddlPollScheduler.cancel()
        gradesSyncPrefs.setEnabled(false)
        ddlSyncPrefs.setEnabled(false)
        apiClient.withExclusiveSession { apiClient.clearBrowserSession() }
        if (clearSchoolData) this.clearSchoolData()
    }
}
