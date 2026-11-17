package com.example.personal_studio.feature.profile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.ui.components.SpinningSyncIcon
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.theme.Tg
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Wording shared by the 考试 and 作业 pages; pure so it can be unit tested. */
object SchoolText {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    /** "还有 25 分钟" / "还有 5 小时" / "还有 3 天"; nothing left reads "已开始". */
    fun remaining(ms: Long): String = when {
        ms <= 0 -> "已开始"
        ms < HOUR -> "还有 ${(ms / MIN).coerceAtLeast(1)} 分钟"
        ms < DAY -> "还有 ${ms / HOUR} 小时"
        else -> "还有 ${ms / DAY} 天"
    }

    /** "11 月 18 日 周三 19:00". */
    fun dayTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(at).atZone(zone)
        return "${d.monthValue} 月 ${d.dayOfMonth} 日 ${TodayText.weekday(d.toLocalDate())} ${TodayText.hhmm(at, zone)}"
    }

    /** "11 月 18 日 周三 19:00–21:00" (end only when on the same day). */
    fun span(start: Long, end: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        val base = dayTime(start, zone)
        if (end == null || end <= start) return base
        val sameDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate() == Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        return if (sameDay) "$base–${TodayText.hhmm(end, zone)}" else "$base – ${dayTime(end, zone)}"
    }

    /** Group label for an upcoming deadline: 今天 / 明天 / 7 天内 / 更晚. */
    fun dueGroup(due: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day: LocalDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when (day.toEpochDay() - today.toEpochDay()) {
            0L -> "今天"
            1L -> "明天"
            in 2L..6L -> "7 天内"
            else -> "更晚"
        }
    }
}

/**
 * Title bar for the school pages: back, title and caption, a sync button whose icon turns
 * while syncing, and under it the latest sync step or the error.
 */
@Composable
fun SchoolHeader(
    title: String,
    caption: String,
    syncing: Boolean,
    onSync: () -> Unit,
    onBack: () -> Unit,
    step: String?,
    error: String?,
) {
    val c = Tg.colors
    Column(Modifier.fillMaxWidth()) {
        TgTopBar(
            route = title, subtitle = "$title\n$caption", onBack = onBack,
            trailing = {
                IconButton(onClick = onSync, enabled = !syncing) {
                    SpinningSyncIcon(syncing, if (syncing) c.accent else c.text, contentDescription = if (syncing) "正在同步" else "从学校同步")
                }
            },
        )
        val line = error ?: if (syncing) step else null
        AnimatedContent(line, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "syncLine") { text ->
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (text == error) c.error else c.textSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
        }
    }
}
