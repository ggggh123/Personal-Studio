package com.example.personal_studio.feature.timeline.today

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineSource
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.model.formatCredits
import com.example.personal_studio.domain.timeline.ScheduleRemindersUseCase
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import com.example.personal_studio.ui.theme.daylight.CoursePalette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** A button at the bottom of the detail; [primary] is filled with the text colour. */
data class DetailAction(val label: String, val primary: Boolean, val onClick: () -> Unit)

/** What the detail says about an item (pure, so the wording is tested). */
data class DetailSpec(
    val kind: String,
    val subtitle: String,
    val rows: List<Pair<String, String>>,
    val reminders: List<Pair<String, Boolean>>,
    val remindersOff: Boolean,
    val note: String?,
)

fun detailSpec(item: TimelineItem, now: Long, zone: ZoneId): DetailSpec {
    val start = Instant.ofEpochMilli(item.startAt).atZone(zone)
    val date = "${start.monthValue} 月 ${start.dayOfMonth} 日 ${TodayText.weekday(start.toLocalDate())}"
    val span = "${TodayText.hhmm(item.startAt, zone)}–${TodayText.hhmm(item.endTime, zone)}"
    val reminders = ScheduleRemindersUseCase.slotsFor(item.type).map { slot ->
        val fireAt = if (slot.isOverdue) item.startAt else item.startAt - slot.minBefore * 60_000L
        reminderLabel(item.type, slot.minBefore, slot.isOverdue) to (fireAt <= now)
    }
    fun MutableList<Pair<String, String>>.add(k: String, v: String?) {
        v?.trim()?.takeIf { it.isNotEmpty() }?.let { add(k to it) }
    }
    return when (item.type) {
        TimelineType.COURSE -> DetailSpec(
            kind = "课程",
            subtitle = listOfNotNull(date, TodayText.periods(item), span).joinToString(" · "),
            rows = buildList {
                add("地点", item.location)
                add("教师", item.instructor)
                add("周次", item.weekIndexInSemester?.let { "第 $it 周" })
                add("学分", item.credits?.let { "${formatCredits(it)} 学分" })
            },
            reminders = reminders,
            remindersOff = false,
            note = if (item.sourceType == TimelineSource.IMPORTED_PORTAL) "来自教务课表导入。重新导入会覆盖本学期内导入的课程。" else null,
        )
        TimelineType.TASK -> DetailSpec(
            kind = if (item.sourceType == TimelineSource.IMPORTED_LEXUE) "乐学作业" else "待办",
            subtitle = "$date ${TodayText.hhmm(item.startAt, zone)} 截止",
            rows = buildList {
                add("来源", TodayText.source(item))
                add("状态", when {
                    item.isDone -> "已完成"
                    item.startAt <= now -> "已逾期"
                    else -> "未完成"
                })
            },
            reminders = reminders,
            remindersOff = item.isDone,
            note = if (item.sourceType == TimelineSource.IMPORTED_LEXUE) "完成状态只记录在本机，不会同步到乐学。" else null,
        )
        TimelineType.EXAM -> DetailSpec(
            kind = "考试",
            subtitle = "$date · $span",
            rows = buildList {
                add("地点", item.location)
                add("座位号", TodayText.seat(item))
                add("来源", TodayText.source(item))
                add("状态", if (item.isDone) "已考" else null)
            },
            reminders = reminders,
            remindersOff = item.isDone,
            note = if (item.sourceType == TimelineSource.IMPORTED_EXAM) "考试时间以教务系统为准，变动会在下次同步时更新。" else null,
        )
        TimelineType.CUSTOM -> DetailSpec(
            kind = "日程",
            subtitle = "$date · $span",
            rows = buildList {
                add("地点", item.location)
                add("状态", if (item.isDone) "已完成" else null)
            },
            reminders = reminders,
            remindersOff = item.isDone,
            note = null,
        )
    }
}

private fun reminderLabel(type: TimelineType, minutes: Int, overdue: Boolean): String {
    if (overdue) return "到期提醒"
    val amount = when {
        minutes % 1440 == 0 -> "${minutes / 1440} 天"
        minutes % 60 == 0 -> "${minutes / 60} 小时"
        else -> "$minutes 分钟"
    }
    return when (type) {
        TimelineType.COURSE -> "上课前 $amount"
        TimelineType.TASK -> "截止前 $amount"
        TimelineType.EXAM -> "考前 $amount"
        TimelineType.CUSTOM -> "开始前 $amount"
    }
}

/** Body of an item's detail, shared by the 今日 sheet and the full-page detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailContent(
    item: TimelineItem,
    now: Long,
    zone: ZoneId,
    actions: List<DetailAction>,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val spec = detailSpec(item, now, zone)
    val chip = when (item.type) {
        TimelineType.COURSE -> CoursePalette.forKey(item.title, c.family).content
        TimelineType.TASK, TimelineType.CUSTOM -> c.textSecondary
        TimelineType.EXAM -> c.accent
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(chip, CircleShape))
            Text(spec.kind, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Text(item.title, style = MaterialTheme.typography.headlineLarge, color = c.text, modifier = Modifier.padding(top = 8.dp))
        Text(
            spec.subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = c.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        spec.rows.forEach { (k, v) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .drawBehind { drawLine(c.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Text(v, style = MaterialTheme.typography.bodyMedium, color = c.text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        item.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.padding(top = 12.dp))
        }
        item.notes?.takeIf { it.isNotBlank() && item.type != TimelineType.EXAM }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        Text("提醒", style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = c.textSecondary, modifier = Modifier.padding(top = 14.dp))
        if (spec.remindersOff) {
            Text("已完成，不再提醒", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        } else {
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                spec.reminders.forEach { (label, passed) ->
                    val shape = RoundedCornerShape(15.dp)
                    Text(
                        if (passed) "$label · 已过" else label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (passed) c.textSecondary else c.accent,
                        modifier = Modifier
                            .background(if (passed) Color.Transparent else c.accentSoft, shape)
                            .border(1.dp, if (passed) c.line else c.accentSoft, shape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { a -> PillButton(a, Modifier.weight(1f)) }
            }
        }
        spec.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
fun PillButton(action: DetailAction, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(48.dp)
            .pressScale(interaction, 0.97f)
            .clip(shape)
            .background(if (action.primary) c.text else Color.Transparent)
            .border(1.dp, if (action.primary) c.text else c.textTertiary, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            action.label,
            style = if (action.primary) MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            else MaterialTheme.typography.bodyMedium,
            color = if (action.primary) c.bg else c.text,
        )
    }
}

/** Controls a [MorphSheet] from its content: [close] plays the reverse transform first. */
class MorphSheetState internal constructor(internal val closeRequest: () -> Unit) {
    fun close() = closeRequest()
}

/**
 * A bottom sheet that grows out of [origin] (the tapped card or row, in root coordinates):
 * the rounded rectangle travels and widens to the sheet's place with the standard spring
 * while the content fades up once the sheet is mostly open. Closing reverses it back into
 * the origin. Without an origin it rises from the bottom edge.
 */
@Composable
fun MorphSheet(
    origin: Rect?,
    title: String,
    onDismissed: () -> Unit,
    content: @Composable (MorphSheetState) -> Unit,
) {
    val c = Tg.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, TgMotion.standard()) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, TgMotion.standard())
                onDismissed()
            }
        }
    }
    val state = remember { MorphSheetState(close) }
    BackHandler(enabled = !closing, onBack = close)

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootOffset = it.positionInRoot() }) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val margin = with(density) { 20.dp.toPx() }
        val from = origin?.translate(-rootOffset) ?: Rect(margin, h, w - margin, h)
        val to = Rect(0f, h - sheetHeight + dragY, w, h + dragY)
        val fromRadius = with(density) { 18.dp.toPx() }
        val toRadius = with(density) { 28.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(c.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "关闭详情", onClick = close),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    val rect = lerp(from, to, p.coerceIn(0f, 1.05f))
                    shape = MorphShape(rect, lerp(fromRadius, toRadius, p), lerp(fromRadius, 0f, p.coerceIn(0f, 1f)))
                    clip = true
                    shadowElevation = with(density) { 18.dp.toPx() } * p.coerceIn(0f, 1f)
                }
                .drawBehind { drawRect(c.surfaceRaised) },
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = with(density) { (h * 0.9f).toDp() })
                    .onSizeChanged { sheetHeight = it.height }
                    .graphicsLayer {
                        val cp = ((progress.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        alpha = cp
                        translationY = (1f - cp) * 14.dp.toPx() + dragY
                    }
                    .semantics { paneTitle = title }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                // Handle: drag down to dismiss.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) },
                            onDragStopped = { velocity ->
                                if (dragY > sheetHeight * 0.25f || velocity > 1500f) {
                                    close()
                                } else {
                                    val startY = dragY
                                    scope.launch {
                                        Animatable(startY).animateTo(0f, TgMotion.fast()) { dragY = value }
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(c.line, RoundedCornerShape(2.dp)))
                }
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
                ) {
                    content(state)
                }
            }
        }
    }
}

private class MorphShape(val rect: Rect, val top: Float, val bottom: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                rect,
                topLeft = CornerRadius(top), topRight = CornerRadius(top),
                bottomLeft = CornerRadius(bottom), bottomRight = CornerRadius(bottom),
            ),
        )
}
