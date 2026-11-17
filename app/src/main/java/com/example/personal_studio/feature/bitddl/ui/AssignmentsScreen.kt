package com.example.personal_studio.feature.bitddl.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitddl.AssignmentsEvent
import com.example.personal_studio.feature.bitddl.AssignmentsViewModel
import com.example.personal_studio.feature.bitddl.DdlRow
import com.example.personal_studio.feature.profile.ui.SchoolHeader
import com.example.personal_studio.feature.profile.ui.SchoolText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.theme.Tg

private const val DAY_MS = 86_400_000L

/**
 * 作业 (乐学 + 新乐学): open assignments grouped by 今天 / 明天 / 7 天内 / 更晚, each with a circle to
 * mark it done; past-due and finished ones are folded at the end. Only the circle toggles,
 * so scrolling past a row never marks it by accident.
 */
@Composable
fun AssignmentsScreen(
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    vm: AssignmentsViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    var showFolded by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { if (it is AssignmentsEvent.NeedLogin) onNeedLogin() }
    }
    val groups = remember(st.upcoming, now) { st.upcoming.groupBy { SchoolText.dueGroup(it.dueAt, now) }.toList() }
    val overdue = st.doneOrOverdue.count { !it.isDone }

    Column(Modifier.fillMaxSize()) {
        SchoolHeader(
            title = "作业",
            caption = if (st.upcoming.isEmpty()) "没有未完成的作业" else "${st.upcoming.size} 项未完成 · 来自${st.sources.joinToString("、").ifEmpty { "乐学" }}",
            syncing = st.syncing, onSync = vm::onRefresh, onBack = onBack,
            step = st.syncSteps.lastOrNull(), error = st.error,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        ) {
            if (st.upcoming.isEmpty()) {
                item(key = "empty") {
                    TgEmptyState(
                        if (st.doneOrOverdue.isEmpty()) "还没有作业" else "作业都处理完了",
                        "点右上角从乐学和新乐学同步；在“校园”打开作业自动同步后，有新作业会通知你，截止前 1 天、2 小时和 30 分钟会提醒。",
                        Modifier.padding(top = 20.dp).enterUp(true, 0),
                    )
                }
            }
            groups.forEachIndexed { gi, (label, rows) ->
                item(key = "g-$label") { TgGroupLabel(label, Modifier.enterUp(true, gi * 40)) }
                item(key = "c-$label") {
                    TgCard(Modifier.enterUp(true, gi * 40)) {
                        rows.forEachIndexed { i, row ->
                            DdlRowView(row, now, first = i == 0, onToggle = { vm.onToggleDone(row.id, it) })
                        }
                    }
                }
            }
            if (st.doneOrOverdue.isNotEmpty()) {
                item(key = "foldToggle") {
                    val turn by animateFloatAsState(if (showFolded) 180f else 0f, tween(220), label = "foldChevron")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { showFolded = !showFolded }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            listOfNotNull(
                                (st.doneOrOverdue.size - overdue).takeIf { it > 0 }?.let { "已完成 $it 项" },
                                overdue.takeIf { it > 0 }?.let { "已过截止 $it 项" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = if (showFolded) "收起" else "展开",
                            tint = c.textSecondary, modifier = Modifier.size(20.dp).rotate(turn))
                    }
                }
                if (showFolded) {
                    item(key = "folded") {
                        TgCard {
                            st.doneOrOverdue.forEachIndexed { i, row ->
                                DdlRowView(row, now, first = i == 0, onToggle = { vm.onToggleDone(row.id, it) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DdlRowView(row: DdlRow, now: Long, first: Boolean, onToggle: (Boolean) -> Unit) {
    val c = Tg.colors
    val left = row.dueAt - now
    val overdue = !row.isDone && left < 0
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { if (!first) drawLine(c.line, Offset(16.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx()) }
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(row.isDone, onToggle)
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Text(
                row.title, style = MaterialTheme.typography.bodyLarge,
                color = if (row.isDone) c.textSecondary else c.text,
                textDecoration = if (row.isDone) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(row.source, row.courseName?.takeIf { it.isNotBlank() }, SchoolText.dayTime(row.dueAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
            )
        }
        when {
            row.isDone -> Unit
            overdue -> Tag("已过截止", inverse = true)
            left <= DAY_MS -> Tag(SchoolText.remaining(left).removePrefix("还有 "), inverse = false)
        }
    }
}

/** Small status pill: inverse (text-coloured) for past due, accent-soft for due within a day. */
@Composable
private fun Tag(text: String, inverse: Boolean) {
    val c = Tg.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (inverse) c.bg else c.accent,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(if (inverse) c.text else c.accentSoft, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun CheckCircle(done: Boolean, onToggle: (Boolean) -> Unit) {
    val c = Tg.colors
    val haptic = LocalHapticFeedback.current
    val fill by animateColorAsState(if (done) c.accent else Color.Transparent, tween(200), label = "ddlFill")
    val tick by animateFloatAsState(if (done) 1f else 0f, tween(220), label = "ddlTick")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = if (done) "标为未完成" else "标为完成") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!done)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(fill).border(1.5.dp, if (done) c.accent else c.textSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent,
                modifier = Modifier.size(14.dp).graphicsLayer { alpha = tick; scaleX = 0.6f + 0.4f * tick; scaleY = 0.6f + 0.4f * tick })
        }
    }
}
