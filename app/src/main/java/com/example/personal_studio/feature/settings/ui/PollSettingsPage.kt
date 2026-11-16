package com.example.personal_studio.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.data.local.datastore.PollResult
import com.example.personal_studio.feature.profile.ui.describeTime
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgSettingRow
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.theme.Tg

/** Interval choices for both background checks; the 校园 page offers the same three. */
internal val PollIntervals = listOf(6, 12, 24)

/** "拉到 12 条 · 新增 2 条" / "失败：…" for the last background run. */
internal fun pollResultText(r: PollResult): String = when {
    !r.ok -> "失败：${r.message}"
    r.message.isNotEmpty() -> "${r.message}（${r.total} 条）"
    r.newCount > 0 -> "查到 ${r.total} 条，新增 ${r.newCount} 条"
    else -> "查到 ${r.total} 条，没有新的"
}

/**
 * Shared page for the two background checks (出分提醒, 作业自动同步), reached from the
 * "已停止" notification: the switch, how often, what the last run found, and what stops it.
 */
@Composable
internal fun PollSettingsPage(
    title: String,
    what: String,
    credsSaved: Boolean,
    enabled: Boolean,
    intervalHours: Int,
    lastResult: PollResult?,
    onToggle: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val c = Tg.colors
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize()) {
        TgTopBar(route = title, subtitle = "$title\n后台检查", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            TgCard(Modifier.padding(top = 16.dp).enterUp(true, 0)) {
                TgSettingRow(
                    title, first = true, enabled = credsSaved,
                    summary = when {
                        !credsSaved -> "需要先登录学校账户"
                        enabled -> "每 $intervalHours 小时$what"
                        else -> "关闭"
                    },
                    trailing = { TgSwitch(enabled && credsSaved, onToggle, enabled = credsSaved) },
                )
                AnimatedVisibility(
                    visible = enabled && credsSaved,
                    enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PollIntervals.forEach { h -> TgChip("$h 小时", h == intervalHours, onClick = { onInterval(h) }) }
                    }
                }
            }

            TgGroupLabel("上次检查", Modifier.enterUp(true, 60))
            TgCard(Modifier.enterUp(true, 60)) {
                Column(Modifier.padding(16.dp)) {
                    if (lastResult == null) {
                        Text("还没有运行过", style = MaterialTheme.typography.bodyLarge, color = c.text)
                    } else {
                        Text(describeTime(lastResult.at, now), style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text(
                            pollResultText(lastResult), style = MaterialTheme.typography.bodyMedium,
                            color = if (lastResult.ok) c.textSecondary else c.error, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Text(
                "后台检查会用保存的账户登录学校系统。密码错误、账户锁定或学校要求验证时会自动停止并通知你，处理好后在这里重新打开。" +
                    "系统可能推迟后台任务；在最近任务里锁定本应用能让检查更准时。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp).enterUp(true, 100),
            )
        }
    }
}
