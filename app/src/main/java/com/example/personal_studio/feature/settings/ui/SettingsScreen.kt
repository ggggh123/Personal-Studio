package com.example.personal_studio.feature.settings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.DebugMoment
import com.example.personal_studio.feature.settings.vm.AppearanceViewModel
import com.example.personal_studio.feature.settings.vm.SettingsHomeViewModel
import com.example.personal_studio.feature.timeline.ui.components.SemesterStartModal
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.today.semesterWeek
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgRowValue
import com.example.personal_studio.ui.components.TgSettingRow
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.daylight.ThemeMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 设置 (from the gear): appearance first, then semester and timetable, reminders, the AI model,
 * the school account (sign in, or sign out) and the version. Each row shows its current value;
 * the semester start opens a date picker in place instead of a page of its own.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: SettingsHomeViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val toast = LocalShellToast.current
    var pickStart by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Shanghai")) }

    Column(Modifier.fillMaxSize()) {
        TgTopBar(route = "设置", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            TgGroupLabel("外观", Modifier.enterUp(true, 0))
            AppearanceCard(Modifier.enterUp(true, 0))

            TgGroupLabel("学期与课表", Modifier.enterUp(true, 60))
            TgCard(Modifier.enterUp(true, 60)) {
                TgSettingRow(
                    "学期第一周", icon = Icons.Outlined.CalendarMonth, first = true,
                    summary = st.semesterStart?.let { start ->
                        semesterWeek(start, today)?.let { "现在是第 $it 周" } ?: if (today < start) "学期还没开始" else "本学期已结束"
                    } ?: "导入课表时会自动设置",
                    onClick = { pickStart = true },
                    trailing = { TgRowValue(st.semesterStart?.let { "${it.monthValue} 月 ${it.dayOfMonth} 日" } ?: "未设置") },
                )
                TgSettingRow(
                    "作息表", icon = Icons.Outlined.Schedule, summary = "每节课的上下课时间",
                    onClick = { onNavigate(NavRoutes.SETTINGS_TIMETABLE) },
                    trailing = { TgRowValue(if (st.periodCount > 0) "${st.periodCount} 节" else "") },
                )
                TgSettingRow(
                    "课程管理", icon = Icons.AutoMirrored.Outlined.MenuBook, summary = "查看、修改或删除整门课",
                    onClick = { onNavigate(NavRoutes.TIMELINE_COURSE_LIST) },
                    trailing = { TgRowValue(if (st.courseCount > 0) "${st.courseCount} 门" else "无") },
                )
            }

            TgGroupLabel("提醒", Modifier.enterUp(true, 100))
            TgCard(Modifier.enterUp(true, 100)) {
                TgSettingRow(
                    "通知与提醒", icon = Icons.Outlined.NotificationsNone, first = true,
                    summary = "课程、作业与考试、日程的提醒和系统权限",
                    onClick = { onNavigate(NavRoutes.SETTINGS_NOTIF) },
                )
            }

            TgGroupLabel("AI", Modifier.enterUp(true, 140))
            TgCard(Modifier.enterUp(true, 140)) {
                TgSettingRow(
                    "AI 模型", icon = Icons.Outlined.AutoAwesome, first = true,
                    summary = if (st.customModel) "自定义接口" else "问答、整理知识和学情报告使用",
                    onClick = { onNavigate(NavRoutes.SETTINGS_AI_MODEL) },
                    trailing = { TgRowValue(st.modelName, accent = st.customModel) },
                )
            }

            TgGroupLabel("学校账户", Modifier.enterUp(true, 160))
            TgCard(Modifier.enterUp(true, 160)) {
                val signedIn = account
                if (signedIn != null) {
                    TgSettingRow(
                        "统一身份认证", icon = Icons.Outlined.AccountCircle, first = true,
                        summary = "学号 $signedIn · 已登录，课表、作业、考试和成绩用它同步",
                        onClick = { onNavigate(NavRoutes.login(null)) },
                        trailing = { TgRowValue("重新验证") },
                    )
                    TgSettingRow(
                        "退出登录", icon = Icons.AutoMirrored.Outlined.Logout,
                        summary = "清除保存的账户和学校网页登录状态，可选择同时删除本机的学校数据",
                        onClick = { confirmLogout = true },
                    )
                } else {
                    TgSettingRow(
                        "登录学校账户", icon = Icons.Outlined.AccountCircle, first = true,
                        summary = "成绩、课表、作业和考试免再输密码",
                        onClick = { onNavigate(NavRoutes.login(null)) },
                        trailing = { TgRowValue("未登录") },
                    )
                }
            }

            TgGroupLabel("关于", Modifier.enterUp(true, 200))
            TgCard(Modifier.enterUp(true, 200)) {
                TgSettingRow(
                    "课间", icon = Icons.Outlined.Info, first = true,
                    summary = "版本 ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · 调试版" else "",
                )
            }
        }
    }

    if (confirmLogout) {
        LogoutDialog(
            onConfirm = { clear ->
                confirmLogout = false
                vm.logout(clear)
                toast?.show(if (clear) "已退出，并删除了本机的学校数据" else "已退出学校账户")
            },
            onDismiss = { confirmLogout = false },
        )
    }

    if (pickStart) {
        SemesterStartModal(
            initial = st.semesterStart ?: today,
            onPicked = { vm.setSemesterStart(it); pickStart = false },
            onDismiss = { pickStart = false },
        )
    }
}

/** Theme mode as four small palette swatches, then a sentence on what the selected one does. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(modifier: Modifier, vm: AppearanceViewModel = hiltViewModel()) {
    val c = Tg.colors
    val mode by vm.mode.collectAsStateWithLifecycle()
    val moment by vm.debugMoment.collectAsStateWithLifecycle()
    TgCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("主题", style = MaterialTheme.typography.bodyLarge, color = c.text)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeModes.forEach { (value, label) ->
                    ThemeSwatch(value, label, selected = mode == value, onClick = { vm.setMode(value) }, modifier = Modifier.weight(1f))
                }
            }
            AnimatedContent(mode, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "modeText") { m ->
                Text(
                    when (m) {
                        ThemeMode.DAYLIGHT -> "配色跟随北京的真实日照：白天是纸面，夜里是灯下，强调色随时间变化。深浅只在日出前和日落后各换一次。"
                        ThemeMode.SYSTEM -> "深浅由系统决定，时间只调整同一色系内的冷暖。"
                        ThemeMode.LIGHT -> "始终使用正午的浅色配色。"
                        ThemeMode.DARK -> "始终使用深夜的深色配色。"
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (BuildConfig.DEBUG) {
                Text("固定时刻（仅调试版）", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.padding(top = 18.dp))
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TgChip("实时", selected = moment == null, onClick = { vm.setDebugMoment(null) })
                    DebugMoment.entries.forEach { m ->
                        TgChip(m.label, selected = moment == m, onClick = { vm.setDebugMoment(m) })
                    }
                }
            }
        }
    }
}

private val ThemeModes = listOf(
    ThemeMode.DAYLIGHT to "天光",
    ThemeMode.SYSTEM to "跟随系统",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.DARK to "深色",
)

// Fixed sample colours (正午, 日落, 深夜 keyframes): the swatches show the palettes themselves,
// so they do not follow the current theme.
private val Noon = Color(0xFFF8F7F3)
private val NoonInk = Color(0xFF141311)
private val NoonAccent = Color(0xFFB03A22)
private val Sunset = Color(0xFFF2E5D6)
private val Night = Color(0xFF13100C)
private val NightInk = Color(0xFFEFE6D8)
private val NightAccent = Color(0xFFE3A54E)

@Composable
private fun ThemeSwatch(mode: ThemeMode, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Tg.colors
    val border by animateColorAsState(if (selected) c.accent else c.line, tween(200), label = "swatchBorder")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, border, shape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                when (mode) {
                    ThemeMode.DAYLIGHT -> drawRect(Brush.horizontalGradient(0f to Noon, 0.3f to Sunset, 0.52f to Night, 1f to Night))
                    ThemeMode.SYSTEM -> {
                        drawRect(Noon, size = size.copy(width = w / 2))
                        drawRect(Night, topLeft = Offset(w / 2, 0f), size = size.copy(width = w / 2))
                    }
                    ThemeMode.LIGHT -> drawRect(Noon)
                    ThemeMode.DARK -> drawRect(Night)
                }
                val dot = 5.dp.toPx()
                val bar = 3.dp.toPx()
                fun lines(x: Float, ink: Color, accent: Color) {
                    drawCircle(accent, dot, Offset(x + dot, h * 0.32f))
                    drawRoundRect(ink.copy(alpha = 0.75f), Offset(x, h * 0.56f), androidx.compose.ui.geometry.Size(w * 0.3f, bar),
                        androidx.compose.ui.geometry.CornerRadius(bar / 2))
                    drawRoundRect(ink.copy(alpha = 0.4f), Offset(x, h * 0.7f), androidx.compose.ui.geometry.Size(w * 0.2f, bar),
                        androidx.compose.ui.geometry.CornerRadius(bar / 2))
                }
                val pad = 9.dp.toPx()
                when (mode) {
                    ThemeMode.DAYLIGHT, ThemeMode.SYSTEM -> {
                        lines(pad, NoonInk, NoonAccent)
                        lines(w / 2 + pad, NightInk, NightAccent)
                    }
                    ThemeMode.LIGHT -> lines(pad, NoonInk, NoonAccent)
                    ThemeMode.DARK -> lines(pad, NightInk, NightAccent)
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(c.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            label, style = MaterialTheme.typography.bodySmall, color = if (selected) c.text else c.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
