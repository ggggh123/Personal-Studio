package com.example.personal_studio.domain.bitexam.model

import com.example.personal_studio.data.network.bit.NetworkMode

/** 解析自 ehall studentWdksapApp(cxxsksap)的一条考试。 */
data class ExamItem(
    val uid: String,          // 去重键:"$term|$courseCode|$startAt"
    val course: String,
    val startAt: Long,        // 考试开始 epoch millis(本地)
    val endAt: Long?,         // 结束;只有日期无时间时为 null
    val location: String?,    // 考点
    val seat: String?,        // 座位号
    val invigilator: String?, // 监考/任课教师
)

data class ExamSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

sealed interface ExamSyncStep {
    object LoggingIn : ExamSyncStep
    object FetchingExams : ExamSyncStep
    data class Done(val total: Int) : ExamSyncStep
    data class Failed(val error: ExamSyncError) : ExamSyncStep
    /** Auto 回退:首选网络不可达,正改用 [to] 重试。 */
    data class SwitchingMode(val to: NetworkMode) : ExamSyncStep
}

sealed interface ExamSyncError {
    object WrongCredentials : ExamSyncError
    object AccountLocked : ExamSyncError
    object CaptchaRequired : ExamSyncError
    data class ParseFail(val message: String) : ExamSyncError
    data class NetworkFail(val cause: String) : ExamSyncError
    data class Unexpected(val cause: String) : ExamSyncError
}
