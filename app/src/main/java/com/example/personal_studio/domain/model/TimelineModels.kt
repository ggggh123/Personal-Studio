package com.example.personal_studio.domain.model

/** Source of a timeline item. P4 only writes MANUAL; rest are reserved for P5. */
enum class TimelineSource { MANUAL, IMPORTED_ICS, IMPORTED_PORTAL, FROM_CHAT }

enum class TimelineType { COURSE, TASK, CUSTOM }

/** Pure domain shape — Compose / VM never see Room entities. */
data class TimelineItem(
    val id: Long,
    val type: TimelineType,
    val title: String,
    val description: String?,
    val startAt: Long,
    val endAt: Long?,
    val isDone: Boolean,
    val doneAt: Long?,
    val location: String?,
    val instructor: String?,
    val notes: String?,
    /** Optional course credit hours; non-null only for COURSE rows. */
    val credits: Float? = null,
    val seriesId: Long?,
    val periodIndex: Int?,
    val periodEndIndex: Int?,
    val weekdayCode: Int?,
    val weekIndexInSemester: Int?,
    val colorOverride: Int?,
    val sourceType: TimelineSource,
    val sourceExternalId: String?,
    val kbEntryIds: List<Long>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Aggregate summary of a course series (Settings → 课程列表). */
data class CourseSeriesSummary(
    val seriesId: Long,
    val title: String,
    val instructor: String?,
    val location: String?,
    val credits: Float? = null,
    val occurrenceCount: Int,
    val minWeek: Int,
    val maxWeek: Int,
)

/** Pre-save form payload for a course series (used by AddCourseScreen). */
data class CourseSeriesDraft(
    val title: String,
    val instructor: String?,
    val location: String?,
    val notes: String?,
    val credits: Float? = null,
    val weekdays: List<Int>,        // 1..7
    val periodStart: Int,
    val periodEnd: Int,             // inclusive
    val weekStart: Int,
    val weekEnd: Int,               // inclusive
)

/** Render-friendly formatter that drops a trailing ".0" from whole-number credits
 *  ("3" instead of "3.0", "2.5" stays "2.5"). Shared by VMs and screens so the
 *  detail/list/edit forms agree on display. */
fun formatCredits(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

/** 13-state finite state machine for bubble visuals. */
sealed class BubbleState {
    object CourseUpcoming   : BubbleState()
    object CourseImminent   : BubbleState()
    object CourseInProgress : BubbleState()
    object CoursePast       : BubbleState()
    object TaskUpcoming     : BubbleState()
    object TaskImminent     : BubbleState()
    object TaskOverdue      : BubbleState()
    object TaskDone         : BubbleState()
    object CustomUpcoming   : BubbleState()
    object CustomImminent   : BubbleState()
    object CustomInProgress : BubbleState()
    object CustomOverdue    : BubbleState()
    object CustomDone       : BubbleState()
}

/** A single reminder: minBefore=0 + isOverdue=true means "DDL just hit". */
data class ReminderSlot(val minBefore: Int, val isOverdue: Boolean) {
    val workName: (Long) -> String get() = { itemId ->
        "reminder_${itemId}_${minBefore}_${isOverdue}"
    }
}

/** Per-day activity counts split by type. CUSTOM is excluded from the strip
 *  per spec; only courses (left badge) and DDLs (right badge) are surfaced. */
data class DayActivityCount(val courses: Int, val tasks: Int) {
    val isEmpty: Boolean get() = courses == 0 && tasks == 0
}
