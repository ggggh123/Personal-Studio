package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberUpdatedState
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

/** "第 1–3、7–9 节 · 08:00–09:35、14:10–15:55" for any set of periods; null when empty. */
internal fun periodsText(periods: Set<Int>, clock: PeriodClock): String? {
    val blocks = EmptyRoomQuery.blocks(periods)
    if (blocks.isEmpty()) return null
    val names = blocks.joinToString("、") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" }
    val times = blocks.joinToString("、") { "${EmptyRoomQuery.hhmm(clock.startMinute(it.first))}–${EmptyRoomQuery.hhmm(clock.endMinute(it.last))}" }
    return "第 $names 节 · $times"
}

/**
 * 自选: the 13 periods in one row; any number of blocks (e.g. 1–3 and 7–9). Tap a period to
 * select it, tap again to clear it. Long-press (or drag sideways) and move to paint a stretch:
 * starting on an empty period selects, starting on a selected one clears; other blocks stay.
 * A vertical swipe is left to the page. Periods already over today are dimmed and not counted.
 */
@Composable
internal fun PeriodPicker(
    selected: Set<Int>,
    clock: PeriodClock,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    passed: Set<Int> = emptySet(),
) {
    val c = Tg.colors
    val latest by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    fun cellAt(x: Float): Int = ((x / size.width) * PERIODS).toInt().coerceIn(0, PERIODS - 1) + 1
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = cellAt(down.position.x)
                        // TAP, DRAG (sideways past the slop), SCROLL (vertical, left to the list),
                        // or null when the finger rests long enough: a long press.
                        val decided: Int? = withTimeoutOrNull(longPressMs) {
                            var result = 0
                            while (result == 0) {
                                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                result = when {
                                    ch == null -> SCROLL
                                    !ch.pressed -> TAP
                                    abs(ch.position.x - down.position.x) > slop &&
                                        abs(ch.position.x - down.position.x) >= abs(ch.position.y - down.position.y) -> DRAG
                                    abs(ch.position.y - down.position.y) > slop -> SCROLL
                                    else -> 0
                                }
                            }
                            result
                        }
                        when (decided) {
                            TAP -> change(EmptyRoomQuery.toggle(latest, start))
                            SCROLL -> Unit
                            else -> {
                                if (decided == null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val base = latest
                                var last = start
                                change(EmptyRoomQuery.paint(base, start, start))
                                while (true) {
                                    val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    ch.consume()
                                    val cell = cellAt(ch.position.x)
                                    if (cell != last) {
                                        last = cell
                                        change(EmptyRoomQuery.paint(base, start, cell))
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..PERIODS).forEach { p ->
                val on = p in selected
                // Neighbouring selected periods join into one block: their facing corners square off.
                val joinLeft = on && (p - 1) in selected
                val joinRight = on && (p + 1) in selected
                val shape = RoundedCornerShape(
                    topStart = if (joinLeft) 2.dp else 8.dp, bottomStart = if (joinLeft) 2.dp else 8.dp,
                    topEnd = if (joinRight) 2.dp else 8.dp, bottomEnd = if (joinRight) 2.dp else 8.dp,
                )
                val bg by animateColorAsState(if (on) c.text else c.bg, tween(150), label = "periodBg")
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .alpha(if (p in passed) 0.4f else 1f)
                        .clip(shape)
                        .background(bg)
                        .border(1.dp, if (on) c.text else c.line, shape)
                        .semantics {
                            role = Role.Checkbox
                            contentDescription = "第 $p 节"
                            stateDescription = if (on) "已选" else "未选"
                            onClick(label = if (on) "取消" else "选择") { change(EmptyRoomQuery.toggle(latest, p)); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$p", style = MaterialTheme.typography.labelSmall, color = if (on) c.bg else c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val summary = periodsText(selected, clock)
            Text(
                summary?.let { if (selected.any { it in passed }) "$it · 已过的不计" else it }
                    ?: "点格子选节次，长按拖动可以连选，再点一下取消",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.weight(1f).padding(start = 2.dp),
            )
            if (selected.isNotEmpty()) {
                Text(
                    "清除", style = MaterialTheme.typography.bodySmall, color = c.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "清除自选节次") { change(emptySet()) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private const val TAP = 1
private const val DRAG = 2
private const val SCROLL = 3
