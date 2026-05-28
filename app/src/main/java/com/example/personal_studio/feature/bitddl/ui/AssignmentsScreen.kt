package com.example.personal_studio.feature.bitddl.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitddl.AssignmentsViewModel
import com.example.personal_studio.feature.bitddl.DdlRow
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssignmentsScreen(onBack: () -> Unit, vm: AssignmentsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showFolded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ assignments", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::onRefresh, enabled = !st.syncing) {
                Text(if (st.syncing) "同步中…" else "↻ 刷新", color = if (st.syncing) FoamDim else Phosphor)
            }
        }
        st.error?.let { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(8.dp))

        if (st.upcoming.isEmpty() && st.doneOrOverdue.isEmpty()) {
            Text(
                "还没有作业 —— 在「作业自动同步」开启,或点右上角刷新一次",
                color = FoamDim, style = MaterialTheme.typography.labelMedium,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(st.upcoming, key = { it.id }) { row -> DdlRowView(row, onToggle = { vm.onToggleDone(row.id, it) }) }
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
                        DdlRowView(row, onToggle = { vm.onToggleDone(row.id, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DdlRowView(row: DdlRow, onToggle: (Boolean) -> Unit) {
    val overdue = !row.isDone && row.dueAt < System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!row.isDone) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.isDone, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(row.title, color = if (row.isDone) FoamDim else Foam)
            Text(
                (row.courseName?.let { "[$it] " } ?: "") + relativeDue(row.dueAt),
                color = if (overdue) Amber else FoamMute,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun relativeDue(t: Long): String {
    val diff = t - System.currentTimeMillis()
    val absMin = kotlin.math.abs(diff) / 60_000
    val sign = if (diff < 0) "已逾期 " else ""
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(t))
    return when {
        absMin < 60 -> "$sign${absMin}分钟 · $fmt"
        absMin < 1440 -> "$sign${absMin / 60}小时 · $fmt"
        else -> "$sign${absMin / 1440}天 · $fmt"
    }
}
