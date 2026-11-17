package com.example.personal_studio.domain.bitddl.model

import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.model.TimelineSource

/** 作业来自哪个平台。新乐学的 uid 以 [ECLASS_UID_PREFIX] 开头,其余是乐学 iCal UID。 */
enum class DdlSource(val label: String, val timeline: TimelineSource) {
    LEXUE("乐学", TimelineSource.IMPORTED_LEXUE),
    ECLASS("新乐学", TimelineSource.IMPORTED_ECLASS);

    companion object {
        const val ECLASS_UID_PREFIX = "eclass-homework-"
        fun ofUid(uid: String): DdlSource = if (uid.startsWith(ECLASS_UID_PREFIX)) ECLASS else LEXUE
        fun of(source: TimelineSource): DdlSource? = entries.firstOrNull { it.timeline == source }
    }
}

/** 一条作业/活动 DDL:乐学来自 iCal,新乐学来自畅课作业接口。 */
data class DdlEvent(
    val uid: String,          // 去重键:乐学 iCal UID / 新乐学 "eclass-homework-<id>"
    val title: String,
    val description: String,
    val course: String?,      // 课程名
    val dueAt: Long,          // 截止 → epoch millis
    val source: DdlSource = DdlSource.LEXUE,
    /** 新乐学告知本人是否已提交;乐学拿不到,恒为 false。 */
    val submitted: Boolean = false,
)

data class DdlSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

sealed interface DdlSyncStep {
    /** 正在拉乐学(订阅地址,必要时登录)。 */
    object FetchingCalendar : DdlSyncStep
    /** 正在拉新乐学。 */
    object FetchingEclass : DdlSyncStep
    /** 至少一个平台成功。[counts] 是成功平台各自的条数,[failed] 是没同步上的平台及原因。 */
    data class Done(
        val total: Int,
        val newCount: Int,
        val counts: Map<DdlSource, Int> = emptyMap(),
        val failed: Map<DdlSource, DdlSyncError> = emptyMap(),
    ) : DdlSyncStep
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

/** 单个平台一次拉取的结果(对照 M4 BackgroundSyncResult)。 */
sealed interface BackgroundDdlResult {
    data class Ok(val events: List<DdlEvent>) : BackgroundDdlResult
    data class Stop(val reason: DdlSyncError) : BackgroundDdlResult
    object Transient : BackgroundDdlResult
}

/**
 * 一次同步两个平台的结果。各平台独立:一边失败不影响另一边落库,也不会删掉失败那边已有的作业。
 */
data class DdlSyncOutcome(val bySource: Map<DdlSource, BackgroundDdlResult>) {
    val succeeded: Set<DdlSource> get() = bySource.filterValues { it is BackgroundDdlResult.Ok }.keys

    val events: List<DdlEvent>
        get() = bySource.values.filterIsInstance<BackgroundDdlResult.Ok>().flatMap { it.events }

    /** 没同步上的平台;暂时性失败按网络错误算。 */
    val failures: Map<DdlSource, DdlSyncError>
        get() = bySource.mapNotNull { (source, r) ->
            when (r) {
                is BackgroundDdlResult.Ok -> null
                is BackgroundDdlResult.Stop -> source to r.reason
                BackgroundDdlResult.Transient -> source to DdlSyncError.NetworkFail("网络错误")
            }
        }.toMap()

    /** 账号本身的问题(密码错、被锁),哪个平台发现都算。 */
    val accountStop: DdlSyncError?
        get() = bySource.values.filterIsInstance<BackgroundDdlResult.Stop>().map { it.reason }
            .firstOrNull { it == DdlSyncError.WrongCredentials || it == DdlSyncError.AccountLocked }

    /** 全部失败时展示的原因:账号问题优先,其次要验证,再按乐学、新乐学的顺序。 */
    val primaryError: DdlSyncError
        get() {
            val stops = bySource.values.filterIsInstance<BackgroundDdlResult.Stop>().map { it.reason }
            return accountStop
                ?: stops.firstOrNull { it == DdlSyncError.CaptchaRequired }
                ?: stops.firstOrNull()
                ?: DdlSyncError.NetworkFail("网络错误")
        }
}

/** GenerateLexueIcalUrlUseCase 的返回。 */
sealed interface LexueUrlResult {
    data class Ok(val url: String) : LexueUrlResult
    data class Failed(val error: DdlSyncError) : LexueUrlResult
}
