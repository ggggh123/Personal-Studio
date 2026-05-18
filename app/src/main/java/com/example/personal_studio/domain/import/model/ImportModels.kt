package com.example.personal_studio.domain.import.model

import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.TermDto

/** User-entered credentials for the wizard. */
data class ImportCredentials(
    val username: String,
    val password: String,
)

/** All inputs needed to drive one import run. */
data class ImportRequest(
    val credentials: ImportCredentials,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
    /** null = use BIT's reported current term; non-null = override (Screen 2). */
    val termCodeOverride: String? = null,
)

/** Final outcome of a successful import. */
data class ImportResult(
    val successCount: Int,
    val replacedCount: Int,
    val termCode: String,
)

/** Streamed by the orchestrator so the UI can render progress + the preview
 *  gate. The flow suspends at [Preview] until the VM resolves the confirm
 *  channel; subsequent emissions either proceed to [Writing]/[Done] or to
 *  [Cancelled]. */
sealed class ImportStep {
    object LoggingIn : ImportStep()
    object FetchingTerm : ImportStep()
    object FetchingWeekDate : ImportStep()
    data class FetchingSchedule(val termCode: String) : ImportStep()
    object Mapping : ImportStep()
    data class Preview(
        val items: List<TimelineItemEntity>,
        val term: TermDto,
        val countToReplace: Int,
    ) : ImportStep()
    object Writing : ImportStep()
    data class Done(val result: ImportResult) : ImportStep()
    object Cancelled : ImportStep()
    data class Failed(val err: ImportError) : ImportStep()
}

/** All user-facing failure modes. UI maps these to error banners. */
sealed class ImportError {
    object WrongCredentials : ImportError()
    object AccountLocked : ImportError()
    object CaptchaRequired : ImportError()
    data class NetworkFail(val cause: Throwable) : ImportError()
    data class ParseFail(val message: String) : ImportError()
    object NoCurrentTerm : ImportError()
    object EmptySchedule : ImportError()
    data class Unexpected(val cause: Throwable) : ImportError()
}
