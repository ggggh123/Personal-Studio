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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.NotifSettingsViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Hull
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

@Composable
fun NotifSettingsScreen(
    onBack: () -> Unit,
    vm: NotifSettingsViewModel = hiltViewModel(),
) {
    val s by vm.switches.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    // Diagnostic statuses are recomputed on every recomposition — these are
    // cheap synchronous calls. Returning to this screen after the user edits
    // a system setting picks up the new value automatically.
    val notifPermissionGranted = if (Build.VERSION.SDK_INT >= 33)
        ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    else true
    val alarmManager = remember { ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val canScheduleExactAlarms =
        if (Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true
    val powerManager = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val isIgnoringBatteryOptimizations =
        powerManager.isIgnoringBatteryOptimizations(ctx.packageName)

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "notif", subtitle = "# notifications", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 3 toggles ──────────────────────────────
            ToggleRow("课程提醒（上课前 10 分钟）", s.course, vm::toggleCourse)
            ToggleRow("DDL 提醒（24h / 2h / 30min + 已过期）", s.task, vm::toggleTask)
            ToggleRow("自定义事件提醒（前 30 分钟）", s.custom, vm::toggleCustom)

            Divider(color = FoamDim, modifier = Modifier.padding(vertical = 12.dp))

            // ── Existing notification-permission row ───────────────────
            Text(
                text = "通知权限：${if (notifPermissionGranted) "已授权" else "未授权"}",
                color = Phosphor,
            )
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                ctx.startActivity(intent)
            }) { Text("去系统设置") }

            Divider(color = FoamDim, modifier = Modifier.padding(vertical = 12.dp))

            // ── Diagnostic section ─────────────────────
            SectionHeader("## diagnostic")

            DiagnosticRow(
                label = "通知权限",
                granted = notifPermissionGranted,
                onFix = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                    )
                },
            )
            DiagnosticRow(
                label = "精确闹钟权限",
                granted = canScheduleExactAlarms,
                onFix = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${ctx.packageName}")),
                        )
                    }
                },
            )
            DiagnosticRow(
                label = "电池优化豁免",
                granted = isIgnoringBatteryOptimizations,
                onFix = {
                    @Suppress("BatteryLife") // system setting — we only request, user decides
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${ctx.packageName}")),
                    )
                },
            )

            Spacer(Modifier.height(8.dp))

            ManufacturerGuidanceCard(
                onOpenAppDetails = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${ctx.packageName}")),
                    )
                },
            )
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Phosphor,
    )
}

/**
 * One status row with a label, a coloured ✓ or ✗, and (only when the status
 * is "not granted") an outlined "[去 X 设置]" button to navigate the user
 * straight to the relevant system page.
 */
@Composable
private fun DiagnosticRow(
    label: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = Foam, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "✓" else "✗",
            color = if (granted) Phosphor else Carmine,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (!granted) {
            OutlinedButton(onClick = onFix) { Text("去设置") }
        }
    }
}

/**
 * Static manufacturer-specific guidance for the most common Chinese ROMs +
 * a generic [Settings.ACTION_APPLICATION_DETAILS_SETTINGS] fallback button.
 *
 * The card is collapsed by default — most users won't hit ROM-level
 * background restrictions, and the 4-row instructions are noisy.
 */
@Composable
private fun ManufacturerGuidanceCard(onOpenAppDetails: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Rule, RoundedCornerShape(4.dp))
            .background(Hull)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp)),
        ) {
            Text(
                text = "厂商通知问题",
                color = Phosphor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                                  else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) "collapse" else "expand",
                    tint = FoamMute,
                )
            }
        }

        if (expanded) {
            Text(
                text = "若开启了上面 3 项权限后通知仍延迟或被截，多半是厂商 ROM 在杀后台。" +
                    "请按以下路径手动放行 Personal-Studio：",
                color = FoamMute,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "※ 建议在最近任务中锁定本应用，以确保后台提醒工作正常",
                color = Amber,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            ManufacturerLine("小米 / 红米", "设置 → 应用管理 → Personal-Studio → 自启动 / 省电策略 → 无限制")
            ManufacturerLine("华为 / 荣耀", "手机管家 → 应用启动管理 → Personal-Studio → 手动管理 → 全部开启")
            ManufacturerLine("OPPO / 一加", "设置 → 电池 → 应用电池管理 → Personal-Studio → 后台不冻结 / 不休眠")
            ManufacturerLine("vivo / iQOO", "i 管家 → 应用管理 → 自启动管理 → Personal-Studio → 开启")

            Spacer(Modifier.height(4.dp))

            OutlinedButton(onClick = onOpenAppDetails) {
                Text("打开应用详情")
            }
        }
    }
}

@Composable
private fun ManufacturerLine(brand: String, path: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(brand, color = Phosphor, style = MaterialTheme.typography.labelMedium)
        Text(path, color = Foam, style = MaterialTheme.typography.bodySmall)
    }
}

@Suppress("unused")
private val PreviewIgnore: Color = Color.Unspecified
