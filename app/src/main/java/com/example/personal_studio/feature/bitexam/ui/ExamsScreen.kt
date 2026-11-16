package com.example.personal_studio.feature.bitexam.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitexam.ExamRow
import com.example.personal_studio.feature.bitexam.ExamsEvent
import com.example.personal_studio.feature.bitexam.ExamsViewModel
import com.example.personal_studio.feature.profile.ui.SchoolHeader
import com.example.personal_studio.feature.profile.ui.SchoolText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.theme.Tg

private const val DAY_MS = 86_400_000L

/**
 * 考试: the next exam large with its countdown, the rest as cards (time, room, seat,
 * invigilator), and finished exams folded at the end. Sync pulls the list from the school.
 */
@Composable
fun ExamsScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: ExamsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showPast by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { if (it is ExamsEvent.NeedLogin) onNeedLogin() }
    }

    Column(Modifier.fillMaxSize()) {
        SchoolHeader(
            title = "考试",
            caption = if (st.upcoming.isEmpty()) "没有待考的考试" else "${st.upcoming.size} 场待考",
            syncing = st.syncing, onSync = vm::onRefresh, onBack = onBack,
            step = st.syncSteps.lastOrNull(), error = st.error,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (st.upcoming.isEmpty()) {
                item(key = "empty") {
                    TgEmptyState(
                        "没有待考的考试",
                        "学校发布考试安排后，点右上角同步就会出现在这里和今日里；考前 1 天、2 小时和 30 分钟会提醒你。",
                        Modifier.padding(top = 12.dp).enterUp(true, 0),
                    )
                }
            } else {
                val next = st.upcoming.first()
                item(key = "next-${next.id}") { NextExamCard(next, now, Modifier.enterUp(true, 0)) }
                if (st.upcoming.size > 1) {
                    item(key = "later") { TgGroupLabel("之后", Modifier.padding(top = 0.dp)) }
                    items(st.upcoming.drop(1), key = { it.id }) { row -> ExamCard(row, now, past = false, modifier = Modifier.animateItem()) }
                }
            }
            if (st.past.isNotEmpty()) {
                item(key = "pastToggle") {
                    val turn by animateFloatAsState(if (showPast) 180f else 0f, tween(220), label = "pastChevron")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { showPast = !showPast }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已考 ${st.past.size} 场", style = MaterialTheme.typography.bodyMedium, color = Tg.colors.textSecondary, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = if (showPast) "收起" else "展开",
                            tint = Tg.colors.textSecondary, modifier = Modifier.size(20.dp).rotate(turn))
                    }
                }
                if (showPast) items(st.past, key = { it.id }) { row -> ExamCard(row, now, past = true, modifier = Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun NextExamCard(row: ExamRow, now: Long, modifier: Modifier) {
    val c = Tg.colors
    val left = row.startAt - now
    val soon = left in 0..DAY_MS
    TgCard(modifier) {
        Column(Modifier.padding(18.dp)) {
            Text(if (soon) "下一场 · 24 小时内" else "下一场", style = MaterialTheme.typography.bodySmall, color = if (soon) c.accent else c.textSecondary)
            Text(row.course, style = MaterialTheme.typography.headlineLarge, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp))
            Text(SchoolText.remaining(left), style = MaterialTheme.typography.displaySmall, color = if (soon) c.accent else c.text,
                modifier = Modifier.padding(top = 10.dp))
            Text(SchoolText.span(row.startAt, row.endAt), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                modifier = Modifier.padding(top = 6.dp))
            Details(row, past = false, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ExamCard(row: ExamRow, now: Long, past: Boolean, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val left = row.startAt - now
    TgCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.course, style = MaterialTheme.typography.titleMedium, color = if (past) c.textSecondary else c.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    if (past) "已结束" else SchoolText.remaining(left),
                    style = MaterialTheme.typography.bodySmall,
                    color = when { past -> c.textSecondary; left in 0..DAY_MS -> c.accent; else -> c.textSecondary },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(SchoolText.span(row.startAt, row.endAt), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp))
            Details(row, past, Modifier.padding(top = 10.dp))
        }
    }
}

/** Room, seat and invigilator as small labelled pills; the seat is the one to find quickly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(row: ExamRow, past: Boolean, modifier: Modifier) {
    val items = listOfNotNull(
        row.location?.takeIf { it.isNotBlank() }?.let { "地点" to it },
        row.seat?.takeIf { it.isNotBlank() }?.let { "座位" to it },
        row.invigilator?.takeIf { it.isNotBlank() }?.let { "监考" to it },
    )
    if (items.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) -> InfoPill(label, value, highlight = label == "座位" && !past) }
    }
}

@Composable
private fun InfoPill(label: String, value: String, highlight: Boolean) {
    val c = Tg.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) c.accentSoft else c.bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (highlight) c.accent else c.text,
            modifier = Modifier.padding(start = 6.dp))
    }
}
