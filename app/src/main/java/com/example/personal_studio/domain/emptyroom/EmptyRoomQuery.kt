package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots

/** "要空着的时间" on the 空教室 page. [span] is the fixed period range, where there is one. */
enum class TimeFilter(val label: String, val span: IntRange?) {
    NOW("现在", null),
    MORNING("上午", 1..5),
    AFTERNOON("下午", 6..10),
    EVENING("晚上", 11..13),
    ANY("不限", null),
    CUSTOM("自选", null),
}

/**
 * The periods a room must have free, and whether the chosen span is already over today (then
 * nothing is required and the page says so instead of listing rooms for the past).
 */
data class RequiredPeriods(val periods: Set<Int>, val passed: Boolean = false)

/** Pure rules for filtering and ordering the rooms; the screen and view model only call these. */
object EmptyRoomQuery {
    private const val LAST_PERIOD = 13

    /**
     * 现在 = the period under way, or the next one during a break; on another day it means no
     * constraint. 上午 / 下午 / 晚上 = their periods, 自选 = the chosen periods (any blocks, e.g.
     * 1–3 and 7–9; none chosen = no constraint), minus those already over when the date is today.
     */
    fun required(filter: TimeFilter, custom: Set<Int>, isToday: Boolean, nowMinute: Int, clock: PeriodClock): RequiredPeriods {
        val span: Collection<Int> = when (filter) {
            TimeFilter.ANY -> return RequiredPeriods(emptySet())
            TimeFilter.NOW -> {
                if (!isToday) return RequiredPeriods(emptySet())
                val p = clock.periodAt(nowMinute) ?: (1..LAST_PERIOD).firstOrNull { clock.startMinute(it) > nowMinute }
                return if (p == null) RequiredPeriods(emptySet(), passed = true) else RequiredPeriods(setOf(p))
            }
            TimeFilter.CUSTOM -> if (custom.isEmpty()) return RequiredPeriods(emptySet()) else custom
            else -> filter.span!!.toList()
        }
        if (!isToday) return RequiredPeriods(span.toSet())
        val left = span.filter { clock.endMinute(it) > nowMinute }.toSet()
        return if (left.isEmpty()) RequiredPeriods(emptySet(), passed = true) else RequiredPeriods(left)
    }

    /** Consecutive periods merged into blocks: {1, 2, 3, 7, 8} → [1..3, 7..8]. */
    fun blocks(periods: Set<Int>): List<IntRange> {
        val sorted = periods.sorted()
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var start = sorted.first(); var prev = start
        for (p in sorted.drop(1)) {
            if (p == prev + 1) prev = p else { out += start..prev; start = p; prev = p }
        }
        out += start..prev
        return out
    }

    /** A tap on period [p] in 自选: selects it, or clears it when it was selected. */
    fun toggle(selected: Set<Int>, p: Int): Set<Int> = if (p in selected) selected - p else selected + p

    /**
     * Dragging across 自选 from period [from] to [to] paints that stretch onto [base] (the
     * selection when the drag began): selecting when the drag started on an empty period,
     * clearing when it started on a selected one. Other blocks are kept.
     */
    fun paint(base: Set<Int>, from: Int, to: Int): Set<Int> {
        val stretch = (minOf(from, to)..maxOf(from, to)).toSet()
        return if (from in base) base - stretch else base + stretch
    }

    /** Rooms with every required period free. */
    fun filter(rooms: List<RoomFreeSlots>, required: Set<Int>): List<RoomFreeSlots> =
        if (required.isEmpty()) rooms else rooms.filter { r -> required.none { it in r.busyPeriods } }

    /**
     * Rooms grouped by building in [buildingOrder] (the catalog's order). Inside a building, for
     * today: rooms free now first, the longest-free first; then busy rooms, the soonest free
     * first. Then rooms with more free periods, then room number in natural order ("2" < "10").
     */
    fun group(rooms: List<RoomFreeSlots>, buildingOrder: List<String>, today: Boolean): List<Pair<String, List<RoomFreeSlots>>> {
        val order = buildingOrder.withIndex().associate { (i, code) -> code to i }
        val byName = Comparator<RoomFreeSlots> { a, b -> naturalCompare(a.roomName, b.roomName) }
        val within = if (today) {
            compareByDescending<RoomFreeSlots> { it.status.freeNow }
                .thenByDescending { it.status.freeUntilMinuteOfDay ?: -1 }
                .thenBy { it.status.nextFreeMinuteOfDay ?: Int.MAX_VALUE }
                .thenBy { it.busyPeriods.size }
                .then(byName)
        } else {
            compareBy<RoomFreeSlots> { it.busyPeriods.size }.then(byName)
        }
        return rooms.groupBy { it.buildingCode }
            .entries
            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
            .map { (_, list) -> list.first().buildingName to list.sortedWith(within) }
    }

    /** "305" < "1001", "B2" < "B10": digit runs compare by value. */
    fun naturalCompare(a: String, b: String): Int {
        val ra = Regex("""\d+|\D+""").findAll(a).map { it.value }.toList()
        val rb = Regex("""\d+|\D+""").findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]; val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.compareTo(y)
            } else x.compareTo(y)
            if (c != 0) return c
        }
        return ra.size.compareTo(rb.size)
    }

    /** "空到 15:15" / "13:20 后空" / "今天不再空" for today; the free periods for another day. */
    fun statusText(room: RoomFreeSlots, today: Boolean): String {
        if (!today) return freeRangesText(room.freeRanges)
        val s = room.status
        return when {
            s.freeNow -> s.freeUntilMinuteOfDay?.let { "空到 ${hhmm(it)}" } ?: "现在空着"
            s.nextFreeMinuteOfDay != null -> "${hhmm(s.nextFreeMinuteOfDay)} 后空"
            else -> "今天不再空"
        }
    }

    /** "空闲 第 1–2、6–9 节"; nothing free reads "全天有课". */
    fun freeRangesText(ranges: List<IntRange>): String =
        if (ranges.isEmpty()) "全天有课"
        else "空闲 第 " + ranges.joinToString("、") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" } + " 节"

    fun hhmm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
