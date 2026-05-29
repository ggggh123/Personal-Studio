package com.example.personal_studio.feature.bitexam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitexam.ExamRow
import com.example.personal_studio.feature.bitexam.ExamsEvent
import com.example.personal_studio.feature.bitexam.ExamsViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExamsScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: ExamsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showPast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is ExamsEvent.NeedLogin) onNeedLogin() }
    }

    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ exams", color = Cyan, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::onRefresh, enabled = !st.syncing) {
                Text(if (st.syncing) "同步中…" else "↻ 刷新", color = if (st.syncing) FoamDim else Phosphor)
            }
        }
        st.error?.let { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(8.dp))

        if (st.upcoming.isEmpty() && st.past.isEmpty()) {
            Text("还没有考试安排 —— 下拉刷新,或学校尚未发布", color = FoamDim, style = MaterialTheme.typography.labelMedium)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(st.upcoming, key = { it.id }) { row -> ExamRowView(row) { d -> vm.onToggleDone(row.id, d) } }
            if (st.past.isNotEmpty()) {
                item {
                    TextButton(onClick = { showPast = !showPast }) {
                        Text((if (showPast) "▾ 收起" else "▸ 已考") + " (${st.past.size})", color = FoamMute)
                    }
                }
                if (showPast) items(st.past, key = { it.id }) { row -> ExamRowView(row) { d -> vm.onToggleDone(row.id, d) } }
            }
        }
    }
}

@Composable
private fun ExamRowView(row: ExamRow, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!row.isDone) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = row.isDone, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(row.course, color = if (row.isDone) FoamDim else Foam)
            Text(
                timeRange(row.startAt, row.endAt) +
                    (row.location?.let { "  ·  $it" } ?: "") + (row.seat?.let { "  ·  座位$it" } ?: ""),
                color = FoamMute, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun timeRange(start: Long, end: Long?): String {
    val d = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(start))
    val e = end?.let { SimpleDateFormat("HH:mm", Locale.US).format(Date(it)) }
    val rel = relative(start)
    return if (e != null) "$d~$e · $rel" else "$d · $rel"
}

private fun relative(t: Long): String {
    val diff = t - System.currentTimeMillis()
    if (diff < 0) return "已结束"
    val min = diff / 60_000
    return when {
        min < 60 -> "${min}分钟后"
        min < 1440 -> "${min / 60}小时后"
        else -> "${min / 1440}天后"
    }
}
