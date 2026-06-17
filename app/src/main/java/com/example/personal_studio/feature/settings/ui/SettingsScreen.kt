package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(
            route = "settings",
            subtitle = "# 偏好设置",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GroupHeader("教务 / 通用")
            NavRow("学期起始日") { onNavigate(NavRoutes.SETTINGS_SEMESTER) }
            NavRow("作息表(13 节)") { onNavigate(NavRoutes.SETTINGS_TIMETABLE) }
            NavRow("通知与后台提醒") { onNavigate(NavRoutes.SETTINGS_NOTIF) }
            NavRow("课程列表") { onNavigate(NavRoutes.TIMELINE_COURSE_LIST) }

            Spacer(Modifier.height(14.dp))
            GroupHeader("AI")
            NavRow("AI 模型", hint = "切换内置模型 · 高级自定义") { onNavigate(NavRoutes.SETTINGS_AI_MODEL) }

            Spacer(Modifier.height(14.dp))
            GroupHeader("关于")
            Text(
                "版本 ${BuildConfig.VERSION_NAME} · 终端风主题",
                color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = FoamDim)) { append("── ") }
            withStyle(SpanStyle(color = Phosphor)) { append(title) }
            withStyle(SpanStyle(color = FoamDim)) { append(" ──") }
        },
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun NavRow(label: String, hint: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("▸ $label", color = Foam, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("→", color = FoamMute, style = MaterialTheme.typography.bodyMedium)
        }
        if (hint != null) {
            Text(hint, color = FoamDim, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 14.dp, top = 1.dp))
        }
    }
}
