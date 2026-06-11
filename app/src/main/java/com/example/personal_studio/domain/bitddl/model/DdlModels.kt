package com.example.personal_studio.domain.bitddl.model

import com.example.personal_studio.data.network.bit.NetworkMode

/** 解析自乐学 iCal 的一条作业/活动 DDL。 */
data class DdlEvent(
    val uid: String,          // iCal UID,去重键
    val title: String,        // SUMMARY
    val description: String,  // DESCRIPTION
    val course: String?,      // CATEGORIES = 课程名
    val dueAt: Long,          // DTSTART → epoch millis(本地)
)

data class DdlSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

sealed interface DdlSyncStep {
    object FetchingCalendar : DdlSyncStep
    data class Done(val total: Int, val newCount: Int) : DdlSyncStep
    data class Failed(val error: DdlSyncError) : DdlSyncStep
    /** Auto 回退:首选网络不可达,正改用 [to] 重试。 */
    data class SwitchingMode(val to: NetworkMode) : DdlSyncStep
}

sealed interface DdlSyncError {
    object WrongCredentials : DdlSyncError
    object AccountLocked : DdlSyncError
    object CaptchaRequired : DdlSyncError
    object NeedReview : DdlSyncError
    data class ParseFail(val message: String) : DdlSyncError
    data class NetworkFail(val cause: String) : DdlSyncError
    data class Unexpected(val cause: String) : DdlSyncError
}

/** 后台同步分支结果(对照 M4 BackgroundSyncResult)。 */
sealed interface BackgroundDdlResult {
    data class Ok(val events: List<DdlEvent>) : BackgroundDdlResult
    data class Stop(val reason: DdlSyncError) : BackgroundDdlResult
    object Transient : BackgroundDdlResult
}

/** GenerateLexueIcalUrlUseCase 的返回。 */
sealed interface LexueUrlResult {
    data class Ok(val url: String) : LexueUrlResult
    data class Failed(val error: DdlSyncError) : LexueUrlResult
}
