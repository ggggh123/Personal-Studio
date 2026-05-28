package com.example.personal_studio.domain.bitgrades.model

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity

/** 后台同步分支结果。Worker 据此决定:Ok→落库通知, Stop→停轮通知, Transient→retry。 */
sealed interface BackgroundSyncResult {
    /** 拉取并解析成功;entries 可能为空(无成绩也算 Ok)。 */
    data class Ok(val entries: List<GradeEntryEntity>) : BackgroundSyncResult

    /** 需用户介入(WrongCreds/Captcha/Locked/NeedReview/ParseFail) → cancel + notify。 */
    data class Stop(val reason: GradesSyncError) : BackgroundSyncResult

    /** 瞬时错(NetworkFail / Unexpected) → 让 Worker 走 Result.retry()。 */
    object Transient : BackgroundSyncResult
}
