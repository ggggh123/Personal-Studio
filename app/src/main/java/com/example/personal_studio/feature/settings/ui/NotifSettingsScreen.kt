package com.example.personal_studio.feature.settings.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.NotifSettingsViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSettingRow
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.theme.Tg

/**
 * 通知与提醒: which reminders fire (the timings match ScheduleRemindersUseCase.slotsFor),
 * whether the system lets them through, and per-brand advice when a ROM still kills them.
 * System states are re-read every time the page resumes, so they update on return from
 * system settings.
 */
@Composable
fun NotifSettingsScreen(
    onBack: () -> Unit,
    vm: NotifSettingsViewModel = hiltViewModel(),
) {
    val s by vm.switches.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = Tg.colors

    val alarmManager = remember { ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val powerManager = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshTick++
        onPauseOrDispose { }
    }
    val notifGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exactAlarms = remember(refreshTick) { Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms() }
    val unrestricted = remember(refreshTick) { powerManager.isIgnoringBatteryOptimizations(ctx.packageName) }

    fun open(intent: Intent) = runCatching { ctx.startActivity(intent) }
    val appNotifSettings = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }

    Column(Modifier.fillMaxSize()) {
        TgTopBar(route = "通知与提醒", subtitle = "通知与提醒\n提醒什么、什么时候提醒", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            if (!notifGranted) {
                TgCard(Modifier.padding(top = 16.dp).enterUp(true, 0)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("通知没有打开", style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Text("系统不允许本应用发通知，下面的提醒都不会出现。", style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        TgPillButton("去开启通知", onClick = { appNotifSettings() }, style = PillStyle.ACCENT, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }

            TgGroupLabel("提醒", Modifier.enterUp(true, 40))
            TgCard(Modifier.enterUp(true, 40)) {
                TgSettingRow("课程", summary = "上课前 10 分钟", first = true, trailing = { TgSwitch(s.course, vm::toggleCourse) })
                TgSettingRow("作业与考试", summary = "提前 1 天、2 小时、30 分钟；作业到截止时间再提醒一次",
                    trailing = { TgSwitch(s.task, vm::toggleTask) })
                TgSettingRow("日程", summary = "开始前 30 分钟", trailing = { TgSwitch(s.custom, vm::toggleCustom) })
            }
            Text(
                "出分提醒和作业自动同步在“校园”页设置。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )

            TgGroupLabel("系统权限", Modifier.enterUp(true, 80))
            TgCard(Modifier.enterUp(true, 80)) {
                PermissionRow("通知", "允许本应用发出提醒", notifGranted, first = true, onFix = { appNotifSettings() })
                PermissionRow(
                    "准时提醒", if (exactAlarms) "提醒按设定时刻发出" else "未允许时提醒可能晚几分钟", exactAlarms,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${ctx.packageName}")))
                        }
                    },
                )
                PermissionRow(
                    "后台不受限制", if (unrestricted) "系统省电不会拦下提醒" else "省电策略可能推迟或拦下提醒", unrestricted,
                    onFix = {
                        @Suppress("BatteryLife") // a system prompt: the user decides
                        open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${ctx.packageName}")))
                    },
                )
            }

            ManufacturerGuidance(
                onOpenAppDetails = {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${ctx.packageName}")))
                },
                modifier = Modifier.padding(top = 12.dp).enterUp(true, 120),
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, summary: String, granted: Boolean, first: Boolean = false, onFix: () -> Unit) {
    val c = Tg.colors
    TgSettingRow(
        title, summary = summary, first = first,
        onClick = if (granted) null else onFix,
        trailing = {
            Text(
                if (granted) "已允许" else "去开启",
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) c.textSecondary else c.accent,
            )
        },
    )
}

/** Collapsed by default: most phones never need it, and the four paths are noisy. */
@Composable
private fun ManufacturerGuidance(onOpenAppDetails: () -> Unit, modifier: Modifier) {
    val c = Tg.colors
    var expanded by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "chevron")
    TgCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "收起" else "展开") { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("提醒仍然不准时？", style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp).rotate(turn))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "上面三项都已允许后仍然延迟，通常是手机系统在清理后台。按下面的路径放行 Personal Studio，并在最近任务里锁定它。",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                Brand("小米 / 红米", "设置 → 应用管理 → Personal Studio → 自启动开启，省电策略选“无限制”")
                Brand("华为 / 荣耀", "手机管家 → 应用启动管理 → Personal Studio → 手动管理，全部开启")
                Brand("OPPO / 一加", "设置 → 电池 → 应用电池管理 → Personal Studio → 允许后台运行")
                Brand("vivo / iQOO", "i 管家 → 应用管理 → 自启动管理 → Personal Studio → 开启")
                TgPillButton("打开应用详情", onClick = onOpenAppDetails, style = PillStyle.SECONDARY, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Brand(brand: String, path: String) {
    val c = Tg.colors
    Column {
        Text(brand, style = MaterialTheme.typography.bodyMedium, color = c.text)
        Text(path, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 1.dp))
    }
}
