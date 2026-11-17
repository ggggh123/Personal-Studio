package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.emptyroom.EmptyRoomQuery
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.ui.theme.Tg

private const val PERIODS = 13

/** "良乡" from "良乡校区": the segmented control and summaries have little room. */
internal fun shortCampus(name: String): String = name.removeSuffix("校区").ifBlank { name }

/**
 * The place in one line: "良乡 · 全部 14 栋", "良乡 · 理教、综教", "良乡 · 理教、综教等 4 栋";
 * null when no campus is chosen yet.
 */
internal fun placeText(campus: Campus?, campusBuildings: List<Building>, codes: Set<String>): String? {
    campus ?: return null
    val name = shortCampus(campus.name)
    if (codes.isEmpty()) return "$name · 全部 ${campusBuildings.size} 栋"
    val chosen = campusBuildings.filter { it.code in codes }.map { it.name }
    return when {
        chosen.isEmpty() -> "$name · 全部 ${campusBuildings.size} 栋"
        chosen.size <= 3 -> "$name · " + chosen.joinToString("、")
        else -> "$name · " + chosen.take(2).joinToString("、") + "等 ${chosen.size} 栋"
    }
}

/** "第 3–5 节 · 09:55–12:20" for a period range. */
internal fun rangeText(range: IntRange, clock: PeriodClock): String {
    val periods = if (range.first == range.last) "第 ${range.first} 节" else "第 ${range.first}–${range.last} 节"
    return "$periods · ${EmptyRoomQuery.hhmm(clock.startMinute(range.first))}–${EmptyRoomQuery.hhmm(clock.endMinute(range.last))}"
}

/** A room's 13 periods: free in the accent (what people look for), busy in a faint ink; the period under way is outlined. */
@Composable
internal fun OccupancyStrip(busy: Set<Int>, current: Int?, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..PERIODS).forEach { p ->
            val shape = RoundedCornerShape(3.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(shape)
                    .background(if (p in busy) c.text.copy(alpha = 0.10f) else c.accent.copy(alpha = 0.6f))
                    .then(if (p == current) Modifier.border(1.5.dp, c.text, shape) else Modifier),
            )
        }
    }
}

/**
 * 自选: the 13 periods in one row. Tap a period to start, tap another to stretch the range to
 * it; or drag across. The line below gives the periods and their clock times.
 */
@Composable
internal fun PeriodRangeBar(range: IntRange, clock: PeriodClock, onChange: (IntRange) -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val latest by rememberUpdatedState(range)
    val change by rememberUpdatedState(onChange)
    var width by remember { mutableIntStateOf(1) }
    var dragStart by remember { mutableIntStateOf(0) }
    fun cellAt(x: Float): Int = ((x / width) * PERIODS).toInt().coerceIn(0, PERIODS - 1) + 1
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics { contentDescription = "自选节次，" + rangeText(range, clock) }
                .pointerInput(Unit) {
                    width = size.width
                    detectTapGestures { pos ->
                        val p = cellAt(pos.x)
                        val r = latest
                        change(if (r.first == r.last && p != r.first) minOf(p, r.first)..maxOf(p, r.first) else p..p)
                    }
                }
                .pointerInput(Unit) {
                    width = size.width
                    detectHorizontalDragGestures(
                        onDragStart = { pos -> dragStart = cellAt(pos.x); change(dragStart..dragStart) },
                        onHorizontalDrag = { ch, _ ->
                            val p = cellAt(ch.position.x)
                            change(minOf(p, dragStart)..maxOf(p, dragStart))
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..PERIODS).forEach { p ->
                val on = p in range
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(shape)
                        .background(if (on) c.text else c.bg)
                        .border(1.dp, if (on) c.text else c.line, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$p", style = MaterialTheme.typography.labelSmall, color = if (on) c.bg else c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        Text(rangeText(range, clock), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}
