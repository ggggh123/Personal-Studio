package com.example.personal_studio.feature.timeline.today

import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.ui.theme.daylight.SunDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * Pure rules behind the 今日 page: time-of-day periods, taglines, the "next item" card and
 * the rows of a day. No Android or Compose types, so all of it is unit-tested.
 */

/** Periods of the day; the boundaries move with today's sunrise, noon and sunset. */
enum class DayPeriod(val label: String) {
    LATE_NIGHT("深夜"), BEFORE_DAWN("黎明前"), DAWN("拂晓"), MORNING("上午"),
    NOON("正午"), AFTERNOON("午后"), SUNSET("日落"), EVENING("入夜"),
}

fun periodOf(hour: Double, sun: SunDay): DayPeriod = when {
    hour >= 22.5 || hour < sun.sunrise - 1.4 -> DayPeriod.LATE_NIGHT
    hour < sun.sunrise - 0.2 -> DayPeriod.BEFORE_DAWN
    hour < sun.sunrise + 1 -> DayPeriod.DAWN
    hour < sun.solarNoon - 1.5 -> DayPeriod.MORNING
    hour < sun.solarNoon + 1.5 -> DayPeriod.NOON
    hour < sun.sunset - 1 -> DayPeriod.AFTERNOON
    hour < sun.sunset + 0.45 -> DayPeriod.SUNSET
    else -> DayPeriod.EVENING
}

/** Data the tagline can speak about; computed from today's and tomorrow's items. */
data class TaglineFacts(
    /** An exam that is still ahead "tomorrow" from the student's point of view. */
    val examTomorrow: Boolean = false,
    /** Unfinished tasks due later today. */
    val tasksDueTonight: Int = 0,
    val coursesToday: Int = 0,
    val coursesAllOver: Boolean = false,
)

object Taglines {
    val PERIOD: Map<DayPeriod, List<String>> = mapOf(
        DayPeriod.LATE_NIGHT to listOf("夜深了，早点休息", "灯还亮着，别熬太晚", "今天到这里就很好"),
        DayPeriod.BEFORE_DAWN to listOf("天快亮了", "先醒的人，拥有清晨", "再睡一会儿也没关系"),
        DayPeriod.DAWN to listOf("新的一天，从第一缕光开始", "早安，今天也慢慢来", "晨光正好，适合背单词"),
        DayPeriod.MORNING to listOf("上午的脑子最清醒", "趁着好光，读几页书", "一件一件来"),
        DayPeriod.NOON to listOf("日头正高，歇一会儿", "午饭别凑合", "下午见"),
        DayPeriod.AFTERNOON to listOf("午后的光，适合慢慢想", "再坚持一节课", "喝口水，接着来"),
        DayPeriod.SUNSET to listOf("晚霞很好，抬头看看", "今天的太阳要下班了", "天边正在变色"),
        DayPeriod.EVENING to listOf("天黑了，把今天收个尾", "灯下的时间，留给自己", "晚饭吃了吗"),
    )

    /** Data lines win over period lines; the period line rotates by day of year. */
    fun pick(period: DayPeriod, hour: Double, facts: TaglineFacts, dayOfYear: Int): String {
        val night = period == DayPeriod.EVENING || period == DayPeriod.LATE_NIGHT
        return when {
            facts.examTomorrow && night -> "明天有考试，今晚早点睡"
            facts.tasksDueTonight > 0 && hour > 17 &&
                (night || period == DayPeriod.SUNSET) -> "今晚还有 ${facts.tasksDueTonight} 项截止"
            facts.coursesToday > 0 && facts.coursesAllOver &&
                (period == DayPeriod.AFTERNOON || period == DayPeriod.SUNSET || period == DayPeriod.EVENING) ->
                "今天的课都上完了"
            else -> PERIOD.getValue(period).let { it[Math.floorMod(dayOfYear, it.size)] }
        }
    }

    fun facts(today: List<TimelineItem>, tomorrow: List<TimelineItem>, now: Long, hour: Double): TaglineFacts {
        // After midnight "明天" still means the coming morning, i.e. later today.
        val examTomorrow = if (hour < 6) {
            today.any { it.type == TimelineType.EXAM && !it.isDone && it.startAt > now }
        } else {
            tomorrow.any { it.type == TimelineType.EXAM && !it.isDone }
        }
        val courses = today.filter { it.type == TimelineType.COURSE }
        return TaglineFacts(
            examTomorrow = examTomorrow,
            tasksDueTonight = today.count { it.type == TimelineType.TASK && !it.isDone && it.startAt > now },
            coursesToday = courses.size,
            coursesAllOver = courses.isNotEmpty() && courses.all { it.endTime <= now },
        )
    }
}

/** When an item ends: a task's due time, otherwise its end (or start for point events). */
val TimelineItem.endTime: Long get() = if (type == TimelineType.TASK) startAt else (endAt ?: startAt)

/** Items with a duration (courses, exams, events) can be "in progress"; tasks cannot. */
val TimelineItem.hasSpan: Boolean get() = type != TimelineType.TASK

/** What the "next item" card shows. */
sealed interface NextUp {
    data class InProgress(val item: TimelineItem, val remainingMs: Long, val progress: Float) : NextUp
    data class Upcoming(val item: TimelineItem, val inMs: Long) : NextUp
    /** Nothing left today; [item] is tomorrow's first item, if any. */
    data class DayOver(val item: TimelineItem?) : NextUp
}

fun nextUp(today: List<TimelineItem>, tomorrow: List<TimelineItem>, now: Long): NextUp {
    val open = today.filter { !it.isDone }.sortedBy { it.startAt }
    open.firstOrNull { it.hasSpan && it.startAt <= now && now < it.endTime }?.let { item ->
        val total = (item.endTime - item.startAt).coerceAtLeast(1)
        return NextUp.InProgress(item, item.endTime - now, ((now - item.startAt).toFloat() / total).coerceIn(0f, 1f))
    }
    open.firstOrNull { it.startAt > now }?.let { return NextUp.Upcoming(it, it.startAt - now) }
    return NextUp.DayOver(tomorrow.filter { !it.isDone }.minByOrNull { it.startAt })
}

/** Ready-to-show text of the next-item card. */
data class NextCardText(
    val label: String,
    val title: String,
    val meta: String,
    val progress: Float?,
    val itemId: Long?,
)

fun nextCardText(next: NextUp, zone: ZoneId): NextCardText = when (next) {
    is NextUp.InProgress -> NextCardText(
        label = "进行中 · 还剩 ${TodayText.duration(next.remainingMs)}",
        title = next.item.title,
        meta = TodayText.cardMeta(next.item, zone),
        progress = next.progress,
        itemId = next.item.id,
    )
    is NextUp.Upcoming -> {
        val item = next.item
        val label = if (item.type == TimelineType.TASK) {
            if (next.inMs < 6 * HOUR_MS) "截止 · 还有 ${TodayText.duration(next.inMs)}"
            else "今天 ${TodayText.hhmm(item.startAt, zone)} 截止"
        } else {
            if (next.inMs <= 3 * HOUR_MS) "下一项 · ${TodayText.duration(next.inMs)}后"
            else "下一项 · ${TodayText.hhmm(item.startAt, zone)}"
        }
        NextCardText(label, item.title, TodayText.cardMeta(item, zone), null, item.id)
    }
    is NextUp.DayOver -> {
        val item = next.item
        if (item == null) {
            NextCardText("今天的安排都结束了", "明天没有安排", "好好休息", null, null)
        } else {
            val due = if (item.type == TimelineType.TASK) " 截止" else ""
            val place = item.location?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            NextCardText(
                "今天的安排都结束了", item.title,
                "明天 ${TodayText.hhmm(item.startAt, zone)}$due$place", null, item.id,
            )
        }
    }
}

/** How a row of the day timeline looks. */
enum class RowStatus { UPCOMING, IN_PROGRESS, DUE_SOON, PAST, OVERDUE }

sealed interface DayRow {
    val key: String

    data class Now(val at: Long) : DayRow {
        override val key: String get() = "now"
    }

    data class Entry(val item: TimelineItem, val status: RowStatus) : DayRow {
        override val key: String get() = "item-${item.id}"
    }
}

data class DayRows(val rows: List<DayRow>, val done: List<TimelineItem>) {
    val activeCount: Int get() = rows.count { it is DayRow.Entry }
}

/**
 * Rows of [day]: finished tasks move to [DayRows.done] (unless their check animation is still
 * playing, see [holding]); on today a "now" marker sits before the first item still ahead.
 */
fun buildDayRows(
    items: List<TimelineItem>,
    day: LocalDate,
    today: LocalDate,
    now: Long,
    holding: Set<Long> = emptySet(),
): DayRows {
    val sorted = items.sortedWith(compareBy({ it.startAt }, { it.id }))
    val done = sorted.filter { it.type == TimelineType.TASK && it.isDone && it.id !in holding }
    val active = sorted - done.toSet()
    val isToday = day == today
    val rows = mutableListOf<DayRow>()
    var nowPlaced = !isToday
    for (item in active) {
        val inProgress = isToday && item.hasSpan && item.startAt <= now && now < item.endTime
        if (!nowPlaced && item.startAt > now && !inProgress) {
            rows += DayRow.Now(now)
            nowPlaced = true
        }
        rows += DayRow.Entry(item, statusOf(item, day, today, now, inProgress))
    }
    if (!nowPlaced && active.isNotEmpty()) rows += DayRow.Now(now)
    return DayRows(rows, done)
}

private fun statusOf(item: TimelineItem, day: LocalDate, today: LocalDate, now: Long, inProgress: Boolean): RowStatus {
    val past = day.isBefore(today) || (day == today && item.endTime <= now)
    val isTask = item.type == TimelineType.TASK
    return when {
        isTask && !item.isDone && past -> RowStatus.OVERDUE
        past || item.isDone -> RowStatus.PAST
        inProgress -> RowStatus.IN_PROGRESS
        isTask && day == today && item.startAt - now < 3 * HOUR_MS -> RowStatus.DUE_SOON
        else -> RowStatus.UPCOMING
    }
}

/** 1-based teaching week of [date], or null outside a plausible semester. */
fun semesterWeek(semesterStart: LocalDate?, date: LocalDate): Int? {
    semesterStart ?: return null
    val days = ChronoUnit.DAYS.between(semesterStart, date)
    if (days < 0) return null
    return (days / 7 + 1).toInt().takeIf { it <= 30 }
}

object TodayText {
    private val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")

    fun weekday(date: LocalDate): String = "周" + WEEKDAYS[date.dayOfWeek.value - 1]

    fun weekdayShort(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    fun hhmm(epoch: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime()
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun hhmm(hour: Double): String {
        var m = ((((hour % 24) + 24) % 24) * 60).roundToLong()
        if (m >= 1440) m -= 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** "45 分钟", "2 小时", "1 小时 20 分", "3 天". */
    fun duration(ms: Long): String {
        val minutes = max(1L, (ms / 60_000.0).roundToLong())
        if (minutes >= 2 * 1440) return "${minutes / 1440} 天"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m 分钟"
            m == 0L -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }

    fun hours(hours: Double): String = duration((hours * HOUR_MS).roundToLong())

    /** "刚刚", "12 分钟前", "2 小时前", "3 天前". */
    fun ago(then: Long, now: Long): String {
        val minutes = (now - then) / 60_000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            else -> "${minutes / (24 * 60)} 天前"
        }
    }

    fun seat(item: TimelineItem): String? =
        item.notes?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith(SEAT_PREFIX) }
            ?.removePrefix(SEAT_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }

    /** Where a task comes from: "乐学 · 课程名" or "手动添加". */
    fun source(item: TimelineItem): String = when (item.sourceType) {
        TimelineSource.IMPORTED_LEXUE -> listOfNotNull("乐学", item.courseName?.takeIf { it.isNotBlank() }).joinToString(" · ")
        TimelineSource.IMPORTED_EXAM -> "教务考试安排"
        TimelineSource.IMPORTED_PORTAL -> "教务课表"
        TimelineSource.IMPORTED_ICS -> "日历导入"
        TimelineSource.FROM_CHAT -> "问答中添加"
        TimelineSource.MANUAL -> "手动添加"
    }

    private fun span(item: TimelineItem, zone: ZoneId) =
        "${hhmm(item.startAt, zone)}–${hhmm(item.endTime, zone)}"

    /** Second line of the next-item card. */
    fun cardMeta(item: TimelineItem, zone: ZoneId): String = when (item.type) {
        TimelineType.COURSE -> listOfNotNull(span(item, zone), item.location.clean(), item.instructor.clean()).joinToString(" · ")
        TimelineType.TASK -> source(item)
        TimelineType.EXAM -> listOfNotNull(span(item, zone), item.location.clean(), seat(item)?.let { "座位 $it" }).joinToString(" · ")
        TimelineType.CUSTOM -> listOfNotNull(span(item, zone), item.location.clean()).joinToString(" · ")
    }

    /** Second line of a timeline row (the time already sits in the left column). */
    fun rowMeta(item: TimelineItem): String = when (item.type) {
        TimelineType.COURSE -> listOfNotNull(item.location.clean(), periods(item)).joinToString(" · ")
        TimelineType.TASK -> source(item)
        TimelineType.EXAM -> listOfNotNull(item.location.clean(), seat(item)?.let { "座位 $it" }).joinToString(" · ")
        TimelineType.CUSTOM -> item.location.clean().orEmpty()
    }

    fun periods(item: TimelineItem): String? {
        val a = item.periodIndex ?: return null
        val z = item.periodEndIndex ?: a
        return if (z == a) "第 $a 节" else "第 $a–$z 节"
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private const val SEAT_PREFIX = "座位:"
}

internal const val HOUR_MS = 3_600_000L
