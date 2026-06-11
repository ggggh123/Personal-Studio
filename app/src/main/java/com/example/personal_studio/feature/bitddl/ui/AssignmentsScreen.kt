package com.example.personal_studio.feature.bitddl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitddl.AssignmentsEvent
import com.example.personal_studio.feature.bitddl.AssignmentsViewModel
import com.example.personal_studio.feature.bitddl.DdlRow
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Dim
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssignmentsScreen(
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    vm: AssignmentsViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showFolded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is AssignmentsEvent.NeedLogin) onNeedLogin() }
    }

    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(cornerDim = 0.42f, centerGlow = 0.03f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("←", color = FoamMute) }
                Text("$ assignments", color = Phosphor, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::onRefresh, enabled = !st.syncing) {
                    Text(if (st.syncing) "同步中…" else "↻ 刷新", color = if (st.syncing) FoamDim else Phosphor)
                }
            }
            st.syncSteps.forEach { Text("> $it", color = Phosphor, style = MaterialTheme.typography.labelMedium) }
            st.error?.let { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(10.dp))

            if (st.upcoming.isEmpty() && st.doneOrOverdue.isEmpty()) {
                Text(
                    "还没有作业 —— 在「作业自动同步」开启,或点右上角刷新一次",
                    color = FoamDim, style = MaterialTheme.typography.labelMedium,
                )
                return@Column
            }

            val upMin = st.upcoming.minOfOrNull { it.dueAt }
            val upMax = st.upcoming.maxOfOrNull { it.dueAt }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(st.upcoming, key = { it.id }) { row ->
                    DdlCard(row, seasonFraction(row.dueAt, upMin, upMax)) { vm.onToggleDone(row.id, it) }
                }
                if (st.doneOrOverdue.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showFolded = !showFolded }) {
                            Text(
                                (if (showFolded) "▾ 收起" else "▸ 已完成 / 已过期") + " (${st.doneOrOverdue.size})",
                                color = FoamMute,
                            )
                        }
                    }
                    if (showFolded) {
                        items(st.doneOrOverdue, key = { it.id }) { row ->
                            DdlCard(row, 0f) { vm.onToggleDone(row.id, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DdlCard(row: DdlRow, fraction: Float, onToggle: (Boolean) -> Unit) {
    val remaining = row.dueAt - System.currentTimeMillis()
    val overdue = !row.isDone && remaining < 0
    val accent = when {
        row.isDone -> FoamDim
        overdue -> Amber
        remaining in 0..DAY_MS -> Amber // 24 小时内截止:琥珀色提醒
        else -> Cyan
    }
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, if (row.isDone) Dim else Rule)
            .background(if (row.isDone) Void else Deep)
            .clickable { onToggle(!row.isDone) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (row.isDone) "▣" else "▢",
                color = if (row.isDone) Phosphor else FoamMute,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                row.title,
                color = if (row.isDone) FoamDim else Foam,
                style = MaterialTheme.typography.headlineSmall,
                textDecoration = if (row.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            when {
                row.isDone -> Text("✓ 完成", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                overdue -> Text("已逾期", color = Amber, style = MaterialTheme.typography.labelLarge)
                else -> {
                    Text(countdown(remaining), color = accent, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    CountdownBar(fraction, accent)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            (row.courseName?.let { "$it · " } ?: "") + dueText(row.dueAt),
            color = when {
                row.isDone -> FoamDim
                overdue -> Amber
                else -> FoamMute
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 26.dp),
        )
    }
}

/** 倒计时进度条:fraction 越大填充越满。 */
@Composable
private fun CountdownBar(fraction: Float, color: Color) {
    Box(Modifier.width(44.dp).height(4.dp).background(Rule)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

private const val DAY_MS = 86_400_000L

/** 相对「DDL 季」的紧迫度:最近一个=满,最远一个≈15%,线性渐变,单个居中。 */
private fun seasonFraction(due: Long, min: Long?, max: Long?): Float {
    if (min == null || max == null || max <= min) return 0.55f
    val rel = 1f - (due - min).toFloat() / (max - min)
    return (0.15f + rel * 0.85f).coerceIn(0.15f, 1f)
}

private fun countdown(remaining: Long): String {
    if (remaining <= 0) return "即将"
    val min = remaining / 60_000
    return when {
        min < 60 -> "${min}分"
        min < 1440 -> "${min / 60}小时"
        else -> "${min / 1440}天"
    }
}

private fun dueText(due: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(due))
