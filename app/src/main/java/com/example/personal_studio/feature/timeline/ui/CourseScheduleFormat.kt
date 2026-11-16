package com.example.personal_studio.feature.timeline.ui

import com.example.personal_studio.domain.model.formatCredits

/** 周N:1→周一 … 7→周日。越界裁剪到 [0,6]。 */
fun weekdayCn(dow: Int): String =
    "周" + listOf("一", "二", "三", "四", "五", "六", "日")[(dow - 1).coerceIn(0, 6)]

/**
 * 课程列表行第 2 行的课表元信息,形如
 * `周一/周三 第1-2节 · 第1-16周 · 共32节 · 4学分`。
 * 各段以 ` · ` 连接;星期/节次缺失则省略首段,学分为 null 则省略学分段。
 */
fun formatCourseSchedule(
    weekdays: List<Int>,
    periodStart: Int?,
    periodEnd: Int?,
    minWeek: Int,
    maxWeek: Int,
    occurrenceCount: Int,
    credits: Float?,
): String {
    val parts = mutableListOf<String>()

    val weekdayStr = weekdays.distinct().sorted().joinToString("/") { weekdayCn(it) }
    val periodStr = when {
        periodStart == null || periodEnd == null -> ""
        periodStart == periodEnd -> "第${periodStart}节"
        else -> "第${periodStart}-${periodEnd}节"
    }
    val whenSeg = listOf(weekdayStr, periodStr).filter { it.isNotEmpty() }.joinToString(" ")
    if (whenSeg.isNotEmpty()) parts += whenSeg

    parts += if (minWeek == maxWeek) "第${minWeek}周" else "第${minWeek}-${maxWeek}周"
    parts += "共${occurrenceCount}节"
    credits?.let { parts += "${formatCredits(it)}学分" }

    return parts.joinToString(" · ")
}

/**
 * What a new course will produce, shown under the form: "周一、周三 第 3–5 节 · 第 1–16 周 · 共 32 次课".
 * Null until at least one weekday is picked.
 */
fun courseDraftSummary(weekdays: Set<Int>, periodStart: Int, periodEnd: Int, weekStart: Int, weekEnd: Int): String? {
    if (weekdays.isEmpty()) return null
    val days = weekdays.sorted().joinToString("、") { weekdayCn(it) }
    val periods = if (periodStart == periodEnd) "第 $periodStart 节" else "第 $periodStart–$periodEnd 节"
    val weeks = if (weekStart == weekEnd) "第 $weekStart 周" else "第 $weekStart–$weekEnd 周"
    val count = weekdays.size * (weekEnd - weekStart + 1).coerceAtLeast(0)
    return "$days $periods · $weeks · 共 $count 次课"
}
