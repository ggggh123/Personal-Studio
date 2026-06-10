package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.local.datastore.PollResult
import com.example.personal_studio.feature.settings.vm.DdlPollSettingsViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdlPollSettingsScreen(onBack: () -> Unit, vm: DdlPollSettingsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ ddl-poll", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("后台自动查作业", color = Foam)
                Text(
                    if (st.credsSaved) "每 ${st.intervalHours} 小时静默拉取一次乐学作业 DDL"
                    else "请先在「成绩查询」时勾选'记住密码'",
                    color = FoamDim, style = MaterialTheme.typography.labelMedium,
                )
            }
            Switch(checked = st.enabled, onCheckedChange = vm::onEnableToggle, enabled = st.credsSaved)
        }
        Spacer(Modifier.height(20.dp))

        Text("查询间隔", color = FoamMute, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 12, 24).forEach { h ->
                FilterChip(
                    selected = st.intervalHours == h,
                    onClick = { vm.onIntervalSelect(h) },
                    enabled = st.credsSaved,
                    label = { Text("${h}h") },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // 上次同步(详细状态:成功/失败 + 拉到几条 + 新增几条 + 多久前)
        val r = st.lastResult
        Text(
            "上次同步: " + (r?.let { fmtDdl(it.at) } ?: "—"),
            color = FoamMute, style = MaterialTheme.typography.labelMedium,
        )
        if (r != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                (if (r.ok) "✅ " else "⚠ ") + pollDetail(r),
                color = if (r.ok) Phosphor else Amber,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(20.dp))

        Text(
            """⚠ 后台将以你保存的凭据定期拉取乐学作业 DDL,
            |  同步进时间线并对新作业通知。失败(密码错/锁号)
            |  会立即停轮,需手动重启。""".trimMargin(),
            color = Amber, style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 上次结果详情文案:失败给原因;成功给「拉到 N 条 · 新增 M 条 / 暂无新增」;首次给「已建立基线」。 */
private fun pollDetail(r: PollResult): String = when {
    !r.ok -> "失败：${r.message}"
    r.message.isNotEmpty() -> "${r.message}（${r.total} 条）"
    r.newCount > 0 -> "拉到 ${r.total} 条 · 新增 ${r.newCount} 条"
    else -> "拉到 ${r.total} 条 · 暂无新增"
}

private fun fmtDdl(t: Long): String {
    val diff = System.currentTimeMillis() - t
    val min = diff / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "${min}分钟前"
        min < 1440 -> "${min / 60}小时前"
        min < 10_080 -> "${min / 1440}天前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))
    }
}
