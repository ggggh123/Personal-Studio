package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.DebugMoment
import com.example.personal_studio.feature.settings.vm.AppearanceViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.daylight.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(
            route = "settings",
            subtitle = "偏好设置",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            GroupHeader("外观")
            AppearanceSection()

            Spacer(Modifier.height(14.dp))
            GroupHeader("教务与通用")
            NavRow("学期起始日") { onNavigate(NavRoutes.SETTINGS_SEMESTER) }
            NavRow("作息表（13 节）") { onNavigate(NavRoutes.SETTINGS_TIMETABLE) }
            NavRow("通知与后台提醒") { onNavigate(NavRoutes.SETTINGS_NOTIF) }
            NavRow("课程列表") { onNavigate(NavRoutes.TIMELINE_COURSE_LIST) }

            Spacer(Modifier.height(14.dp))
            GroupHeader("AI")
            NavRow("AI 模型", hint = "切换内置模型，或高级自定义") { onNavigate(NavRoutes.SETTINGS_AI_MODEL) }

            Spacer(Modifier.height(14.dp))
            GroupHeader("关于")
            Text(
                "版本 ${BuildConfig.VERSION_NAME} · 天光主题",
                color = Tg.colors.textSecondary, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(vm: AppearanceViewModel = hiltViewModel()) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val moment by vm.debugMoment.collectAsStateWithLifecycle()

    Text("主题", color = Tg.colors.text, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ThemeMode.DAYLIGHT to "天光", ThemeMode.SYSTEM to "跟随系统",
            ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色",
        ).forEach { (value, label) ->
            Chip(label, selected = mode == value) { vm.setMode(value) }
        }
    }
    Text(
        when (mode) {
            ThemeMode.DAYLIGHT -> "配色跟随北京的真实日照：白天纸本，夜里灯下。深浅只在拂晓和日落后切换。"
            ThemeMode.SYSTEM -> "深浅由系统决定，天光只调整同一色系内的冷暖。"
            ThemeMode.LIGHT -> "始终使用正午配色。"
            ThemeMode.DARK -> "始终使用深夜配色。"
        },
        color = Tg.colors.textSecondary, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )

    if (BuildConfig.DEBUG) {
        Text("固定时刻（仅调试版）", color = Tg.colors.text, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("实时", selected = moment == null) { vm.setDebugMoment(null) }
            DebugMoment.entries.forEach { m ->
                Chip(m.label, selected = moment == m) { vm.setDebugMoment(m) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Tg.colors
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) c.text else c.bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) c.text else c.textTertiary, RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) c.bg else c.text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        color = Tg.colors.textSecondary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun NavRow(label: String, hint: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Tg.colors.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Tg.colors.textSecondary)
        }
        if (hint != null) {
            Text(hint, color = Tg.colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}
