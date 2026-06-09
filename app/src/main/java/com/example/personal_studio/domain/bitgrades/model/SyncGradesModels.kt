package com.example.personal_studio.domain.bitgrades.model

import com.example.personal_studio.data.network.bit.NetworkMode

data class GradesSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

/** 同步进度事件（无 Preview 确认环节——成绩不覆盖手输数据，直接落库）。 */
sealed class SyncGradesStep {
    object LoggingIn : SyncGradesStep()
    object FetchingGrades : SyncGradesStep()
    object FetchingRanks : SyncGradesStep()
    object Persisting : SyncGradesStep()
    data class Done(val termCount: Int, val courseCount: Int) : SyncGradesStep()
    data class Failed(val err: GradesSyncError) : SyncGradesStep()
    /** Auto 回退:首选网络模式不可达,正改用 [to] 重试。 */
    data class SwitchingMode(val to: NetworkMode) : SyncGradesStep()
}

/** 用户可见失败。排名不可用是非致命的(降级为 null)，不在此列。 */
sealed class GradesSyncError {
    object WrongCredentials : GradesSyncError()
    object AccountLocked : GradesSyncError()
    object CaptchaRequired : GradesSyncError()
    data class NetworkFail(val cause: Throwable) : GradesSyncError()
    data class ParseFail(val message: String) : GradesSyncError()
    object EmptyGrades : GradesSyncError()
    /** 正方教务把成绩藏在评教后面——需先在教务系统完成评教才能查分。 */
    object NeedReview : GradesSyncError()
    data class Unexpected(val cause: Throwable) : GradesSyncError()
}
