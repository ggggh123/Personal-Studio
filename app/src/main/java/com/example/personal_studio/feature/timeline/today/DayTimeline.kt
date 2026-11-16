package com.example.personal_studio.feature.timeline.today

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.timeline.vm.CheckPhase
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.daylight.CoursePalette
import java.time.ZoneId

/**
 * One day as a vertical timeline: time on the left, a node on the rail (course colour dot,
 * task ring, exam diamond, event dashed ring), title and place on the right. Tasks have a
 * check button; the tick and strike-through draw, then the row folds into "已完成".
 */
@Composable
fun DayTimeline(
    rows: DayRows,
    now: Long,
    zone: ZoneId,
    checkPhases: Map<Long, CheckPhase>,
    flashId: Long?,
    showDone: Boolean,
    play: Boolean,
    onOpen: (TimelineItem, Rect?) -> Unit,
    onCheck: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp)) {
        if (rows.rows.isEmpty() && rows.done.isEmpty()) {
            Column(Modifier.padding(start = 68.dp, top = 28.dp, bottom = 12.dp)) {
                Text("这一天还没有安排", style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = c.text)
                Text(
                    "点下方 ＋ 新建待办，或在校园页同步课表。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        var index = 0
        rows.rows.forEach { row ->
            androidx.compose.runtime.key(row.key) {
                when (row) {
                    is DayRow.Now -> NowLine(TodayText.hhmm(row.at, zone))
                    is DayRow.Entry -> {
                        val phase = checkPhases[row.item.id]
                        AnimatedVisibility(
                            visible = phase != CheckPhase.COLLAPSING,
                            enter = expandVertically(tween(300)) + fadeIn(tween(220)),
                            exit = shrinkVertically(tween(340)) + fadeOut(tween(220)),
                        ) {
                            EntryRow(
                                entry = row,
                                now = now,
                                zone = zone,
                                checked = phase != null,
                                flash = flashId == row.item.id,
                                onOpen = onOpen,
                                onCheck = onCheck,
                                modifier = Modifier.enterUp(play, 300 + index * 40),
                            )
                        }
                        index++
                    }
                }
            }
        }
        if (rows.done.isNotEmpty()) {
            DoneGroup(rows.done, showDone, onToggleDone, onUndo)
        }
    }
}

@Composable
private fun NowLine(time: String) {
    val c = Tg.colors
    val pulse = rememberInfiniteTransition(label = "now")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "nowPulse")
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.bodySmall, color = c.accent, modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Box(Modifier.width(24.dp).height(28.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) {
                val r = 4.dp.toPx()
                drawCircle(c.accent, r * (1f + 1.8f * t), alpha = 0.55f * (1f - t))
                drawCircle(c.accent, r)
            }
        }
        Box(Modifier.weight(1f).height(1.dp).background(c.accent))
    }
}

@Composable
private fun EntryRow(
    entry: DayRow.Entry,
    now: Long,
    zone: ZoneId,
    checked: Boolean,
    flash: Boolean,
    onOpen: (TimelineItem, Rect?) -> Unit,
    onCheck: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val item = entry.item
    val isTask = item.type == TimelineType.TASK
    val muted = entry.status == RowStatus.PAST
    val bounds = remember { BoundsHolder() }
    val interaction = remember { MutableInteractionSource() }

    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash) {
            flashAlpha.snapTo(0.14f)
            flashAlpha.animateTo(0f, keyframes { durationMillis = 1600; 0.14f at 560; 0f at 1600 })
        } else if (flashAlpha.value > 0f) {
            // The highlight ended while still fading (this effect was restarted): finish it.
            flashAlpha.animateTo(0f, tween(300))
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .trackBounds(bounds)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind { if (flashAlpha.value > 0f) drawRect(c.accent, alpha = flashAlpha.value) },
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            Modifier
                .weight(1f)
                .pressScale(interaction)
                .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "查看详情") {
                    onOpen(item, bounds.bounds)
                },
        ) {
            // Time column.
            Column(Modifier.width(44.dp).padding(top = 13.dp), horizontalAlignment = Alignment.End) {
                Text(
                    TodayText.hhmm(item.startAt, zone),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = if (muted) c.textSecondary else c.text,
                )
                Text(
                    if (isTask) "截止" else TodayText.hhmm(item.endTime, zone),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Rail and node.
            Box(
                Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(c.line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1.dp.toPx())
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Node(item, Modifier.padding(top = 17.dp))
            }
            // Title, place, status.
            Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 11.dp)) {
                val strike by animateFloatAsState(if (checked) 1f else 0f, tween(360), label = "strike")
                Box {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (muted || checked) c.textSecondary else c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (strike > 0f) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .drawBehind {
                                    val y = size.height * 0.52f
                                    drawLine(c.textSecondary, Offset(0f, y), Offset(size.width * strike, y), strokeWidth = 1.dp.toPx())
                                },
                        )
                    }
                }
                val meta = TodayText.rowMeta(item)
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 1.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StatusLabel(entry, now)
            }
        }
        when {
            isTask -> CheckButton(
                checked = checked || item.isDone,
                title = item.title,
                onCheck = { onCheck(item.id) },
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            item.type == TimelineType.EXAM -> Text(
                "考试",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 4.dp)
                    .border(1.dp, c.accent, RoundedCornerShape(11.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun StatusLabel(entry: DayRow.Entry, now: Long) {
    val c = Tg.colors
    val item = entry.item
    val (text, bg, fg) = when (entry.status) {
        RowStatus.OVERDUE -> Triple("已逾期 ${TodayText.duration(now - item.startAt)}", c.text, c.bg)
        RowStatus.DUE_SOON -> Triple("还有 ${TodayText.duration(item.startAt - now)}", c.accentSoft, c.accent)
        RowStatus.IN_PROGRESS -> Triple("进行中 · 还剩 ${TodayText.duration(item.endTime - now)}", c.accentSoft, c.accent)
        RowStatus.PAST -> when {
            item.isDone && item.type == TimelineType.EXAM -> Triple("已考", Color.Transparent, c.textSecondary)
            item.isDone && item.type == TimelineType.CUSTOM -> Triple("已完成", Color.Transparent, c.textSecondary)
            else -> return
        }
        RowStatus.UPCOMING -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        color = fg,
        modifier = Modifier
            .padding(top = 6.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .then(if (bg == Color.Transparent) Modifier.border(1.dp, c.line, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Node(item: TimelineItem, modifier: Modifier = Modifier) {
    val c = Tg.colors
    when (item.type) {
        TimelineType.COURSE -> {
            val swatch = CoursePalette.forKey(item.title, c.family)
            Box(modifier.size(10.dp).background(swatch.content, CircleShape))
        }
        TimelineType.TASK -> Box(
            modifier
                .size(10.dp)
                .background(c.bg, CircleShape)
                .border(1.5.dp, c.textSecondary, CircleShape),
        )
        TimelineType.EXAM -> Canvas(modifier.size(10.dp)) {
            rotate(45f) {
                val s = 9.dp.toPx()
                drawRoundRect(
                    c.accent,
                    topLeft = Offset((size.width - s) / 2, (size.height - s) / 2),
                    size = androidx.compose.ui.geometry.Size(s, s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
        }
        TimelineType.CUSTOM -> Canvas(modifier.size(10.dp)) {
            drawCircle(c.bg)
            drawCircle(
                c.textSecondary, radius = size.minDimension / 2 - 0.75.dp.toPx(),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.6.dp.toPx()))),
            )
        }
    }
}

/** 22 dp ring; checking fills it with the text colour and draws the tick. */
@Composable
private fun CheckButton(checked: Boolean, title: String, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val view = LocalView.current
    val fill by animateColorAsState(if (checked) c.text else Color.Transparent, tween(200), label = "checkFill")
    val ring by animateColorAsState(if (checked) c.text else c.textSecondary, tween(200), label = "checkRing")
    val tick by animateFloatAsState(if (checked) 1f else 0f, tween(300, delayMillis = 50), label = "tick")
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !checked, role = Role.Checkbox) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onCheck()
            }
            .semantics {
                contentDescription = "标记完成：$title"
                stateDescription = if (checked) "已完成" else "未完成"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.5.dp.toPx()
            drawCircle(fill)
            drawCircle(ring, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            if (tick > 0f) {
                val s = size.width / 24f * 0.64f
                val ox = size.width / 2 - 12 * s
                val oy = size.height / 2 - 12 * s
                val full = Path().apply {
                    moveTo(ox + 5 * s, oy + 12.5f * s)
                    lineTo(ox + 9.5f * s, oy + 17 * s)
                    lineTo(ox + 19 * s, oy + 7.5f * s)
                }
                val measure = PathMeasure().apply { setPath(full, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * tick, part, true)
                drawPath(part, c.bg, style = Stroke(3 * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun DoneGroup(done: List<TimelineItem>, expanded: Boolean, onToggle: () -> Unit, onUndo: (Long) -> Unit) {
    val c = Tg.colors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "doneChevron")
    Row(
        Modifier
            .padding(start = 68.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (expanded) "收起已完成" else "展开已完成", onClick = onToggle)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("已完成 ${done.size} 项", style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = c.textSecondary)
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp).rotate(rotation))
    }
    AnimatedVisibility(visible = expanded, enter = expandVertically(tween(300)) + fadeIn(tween(300)), exit = shrinkVertically(tween(260)) + fadeOut(tween(200))) {
        Column {
            done.forEach { item ->
                androidx.compose.runtime.key(item.id) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 68.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Checkbox) { onUndo(item.id) }
                                .semantics {
                                    contentDescription = "标记未完成：${item.title}"
                                    stateDescription = "已完成"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(22.dp).background(c.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.bg, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
