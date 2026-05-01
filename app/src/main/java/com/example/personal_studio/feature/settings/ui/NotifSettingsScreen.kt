package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.NotifSettingsViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun NotifSettingsScreen(
    onBack: () -> Unit,
    vm: NotifSettingsViewModel = hiltViewModel(),
) {
    val s by vm.switches.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "notif", subtitle = "# notifications", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleRow("课程提醒（上课前 10 分钟）", s.course, vm::toggleCourse)
            ToggleRow("DDL 提醒（24h / 2h / 30min + 已过期）", s.task, vm::toggleTask)
            ToggleRow("自定义事件提醒（前 30 分钟）", s.custom, vm::toggleCustom)
            Divider(color = FoamDim, modifier = Modifier.padding(vertical = 12.dp))
            val granted = if (android.os.Build.VERSION.SDK_INT >= 33)
                androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
            Text("通知权限：${if (granted) "已授权" else "未授权"}", color = Phosphor)
            OutlinedButton(onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                ctx.startActivity(intent)
            }) { Text("去系统设置") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Phosphor, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
