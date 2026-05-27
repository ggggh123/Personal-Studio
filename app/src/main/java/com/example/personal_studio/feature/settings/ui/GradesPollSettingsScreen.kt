package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import com.example.personal_studio.feature.settings.vm.GradesPollSettingsViewModel
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
fun GradesPollSettingsScreen(onBack: () -> Unit, vm: GradesPollSettingsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ grades-poll", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        // 总开关
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("后台自动查分", color = Foam)
                Text(
                    if (st.credsSaved) "每 ${st.intervalHours} 小时静默登录一次教务系统"
                    else "请先在「从教务系统查询成绩」时勾选'记住密码'",
                    color = FoamDim, style = MaterialTheme.typography.labelMedium,
                )
            }
            Switch(checked = st.enabled, onCheckedChange = vm::onEnableToggle, enabled = st.credsSaved)
        }
        Spacer(Modifier.height(20.dp))

        // 间隔 3 档
        Text("查询间隔", color = FoamMute, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 6, 12).forEach { h ->
                FilterChip(
                    selected = st.intervalHours == h,
                    onClick = { vm.onIntervalSelect(h) },
                    enabled = st.credsSaved,
                    label = { Text("${h}h") },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // 上次同步
        Text(
            "上次同步: " + (st.lastSyncAt?.let { fmt(it) } ?: "—"),
            color = FoamMute, style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(20.dp))

        // 警告 / 说明
        Text(
            """⚠ 后台将以你保存的凭据每 N 小时静默登录教务,
            |  比对发现新成绩后通知。失败(密码错/锁号/验证码)
            |  会立即停轮,需手动重启。""".trimMargin(),
            color = Amber, style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun fmt(t: Long): String {
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
