package com.example.personal_studio.feature.timeline.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.TimetablePeriod
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.daylight.CoursePalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val ROW = 32.dp
private val HEAD = 30.dp
private val LABELS = 24.dp

/**
 * The week as a period grid: courses by their periods in the course colours, exams as
 * accent-outlined blocks placed by time, today's column tinted with the now line across it.
 */
@Composable
fun WeekView(
    weekStart: LocalDate,
    today: LocalDate,
    now: Long,
    zone: ZoneId,
    periods: List<TimetablePeriod>,
    items: List<TimelineItem>,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onOpen: (TimelineItem, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val sunday = weekStart.plusDays(6)
    val isThisWeek = !today.isBefore(weekStart) && !today.isAfter(sunday)
    val range = "${weekStart.monthValue}.${"%02d".format(weekStart.dayOfMonth)} – ${sunday.monthValue}.${"%02d".format(sunday.dayOfMonth)}" +
        if (isThisWeek) " · 本周" else " · 回到本周"
    val grid = remember(periods) { PeriodGrid(periods) }

    Column(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            ChevronButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上一周", onPrevWeek)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    range,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.textSecondary,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isThisWeek, onClickLabel = "回到本周", onClick = onThisWeek)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            ChevronButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一周", onNextWeek)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(HEAD + ROW * grid.count + 4.dp)) {
            val colW = (maxWidth - LABELS) / 7
            val todayCol = if (isThisWeek) (today.toEpochDay() - weekStart.toEpochDay()).toInt() else -1
            if (todayCol >= 0) {
                Box(
                    Modifier
                        .offset(x = LABELS + colW * todayCol)
                        .width(colW)
                        .height(HEAD + ROW * grid.count + 4.dp)
                        .background(c.accentSoft, RoundedCornerShape(12.dp)),
                )
            }
            for (k in 0 until 7) {
                val day = weekStart.plusDays(k.toLong())
                Text(
                    TodayText.weekdayShort(day) + "\n" + day.dayOfMonth,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.2f),
                    color = if (k == todayCol) c.accent else c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x = LABELS + colW * k, y = 2.dp).width(colW),
                )
            }
            for (i in 0..grid.count) {
                Box(Modifier.offset(x = LABELS, y = HEAD + ROW * i).fillMaxWidth().height(1.dp).background(c.line))
            }
            for (i in 1..grid.count) {
                Box(Modifier.offset(y = HEAD + ROW * (i - 1)).width(22.dp).height(ROW), contentAlignment = Alignment.Center) {
                    Text("$i", style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.textSecondary)
                }
            }
            items.filter { it.type == TimelineType.COURSE || it.type == TimelineType.EXAM }.forEach { item ->
                val local = Instant.ofEpochMilli(item.startAt).atZone(zone)
                val col = (local.toLocalDate().toEpochDay() - weekStart.toEpochDay()).toInt()
                if (col !in 0..6) return@forEach
                val (top, height) = grid.span(item, zone)
                if (height <= 0.dp) return@forEach
                androidx.compose.runtime.key(item.id) {
                    Block(
                        item = item,
                        height = height - 3.dp,
                        modifier = Modifier
                            .offset(x = LABELS + colW * col + 1.5.dp, y = HEAD + top + 1.5.dp)
                            .width(colW - 3.dp)
                            .height(height - 3.dp),
                        onOpen = onOpen,
                    )
                }
            }
            if (todayCol >= 0) {
                val y = grid.yOf(minutesOf(now, zone))
                if (y != null) {
                    Box(
                        Modifier
                            .offset(x = LABELS + colW * todayCol, y = HEAD + y)
                            .width(colW)
                            .height(2.dp)
                            .background(c.accent, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
        if (items.none { it.type == TimelineType.COURSE || it.type == TimelineType.EXAM }) {
            Text(
                "这一周没有课程和考试",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Block(item: TimelineItem, height: Dp, modifier: Modifier, onOpen: (TimelineItem, Rect?) -> Unit) {
    val c = Tg.colors
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val isExam = item.type == TimelineType.EXAM
    val swatch = CoursePalette.forKey(item.title, c.family)
    val shape = RoundedCornerShape(9.dp)
    val fg = if (isExam) c.accent else swatch.content
    Column(
        modifier
            .trackBounds(bounds)
            .pressScale(interaction, 0.96f)
            .clip(shape)
            .background(if (isExam) c.surface else swatch.container)
            .then(if (isExam) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
            .clickable(interaction, indication = null, role = Role.Button) { onOpen(item, bounds.bounds) }
            .semantics { contentDescription = listOfNotNull(item.title, item.location).joinToString("，") }
            .padding(horizontal = 3.dp, vertical = 5.dp),
    ) {
        val label = MaterialTheme.typography.labelSmall
        // Whole lines only: the name gets up to two, the room what is left (never half a line).
        val lines = with(LocalDensity.current) { ((height - 12.dp).toPx() / label.lineHeight.toPx()).toInt() }
        val nameLines = lines.coerceIn(1, 2)
        val roomLines = (lines - nameLines).coerceAtLeast(0)
        Text(if (isExam) "考试" else item.title, style = label, color = fg, maxLines = nameLines, overflow = TextOverflow.Ellipsis)
        item.location?.takeIf { it.isNotBlank() && roomLines > 0 }?.let {
            Text(
                it.replace(" ", ""),
                style = label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = fg,
                maxLines = roomLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ChevronButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Tg.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
    }
}

private fun minutesOf(epoch: Long, zone: ZoneId): Int {
    val t = Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime()
    return t.hour * 60 + t.minute
}

/** Maps times of day onto the period rows (gaps between periods collapse onto a line). */
internal class PeriodGrid(periods: List<TimetablePeriod>) {
    private val spans: List<Pair<Int, Int>> = periods.sortedBy { it.index }.map { minutes(it.startHHmm) to minutes(it.endHHmm) }
    val count: Int get() = spans.size

    /** Offset in rows (0..count) of a minute of the day, or null before the first period / after the last. */
    fun rowsOf(minute: Int): Float? {
        if (spans.isEmpty()) return null
        if (minute < spans.first().first || minute > spans.last().second) return null
        spans.forEachIndexed { i, (s, e) ->
            if (minute <= e) return i + ((minute - s).coerceAtLeast(0).toFloat() / (e - s).coerceAtLeast(1))
            if (i < spans.lastIndex && minute < spans[i + 1].first) return (i + 1).toFloat()
        }
        return count.toFloat()
    }

    fun yOf(minute: Int): Dp? = rowsOf(minute)?.let { ROW * it }

    /** Top and height of an item: courses by their periods, anything else by its times. */
    fun span(item: TimelineItem, zone: ZoneId): Pair<Dp, Dp> {
        val a = item.periodIndex
        val z = item.periodEndIndex ?: a
        if (item.type == TimelineType.COURSE && a != null && z != null && a in 1..count) {
            val last = z.coerceIn(a, count)
            return ROW * (a - 1) to ROW * (last - a + 1)
        }
        val start = clampRows(minutesOf(item.startAt, zone))
        val end = clampRows(minutesOf(item.endAt ?: (item.startAt + 45 * 60_000L), zone))
        return ROW * start to ROW * (end - start).coerceAtLeast(1f)
    }

    private fun clampRows(minute: Int): Float = when {
        spans.isEmpty() -> 0f
        minute <= spans.first().first -> 0f
        minute >= spans.last().second -> count.toFloat()
        else -> rowsOf(minute) ?: 0f
    }

    private fun minutes(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.trim().toInt() }
        return h * 60 + m
    }
}
