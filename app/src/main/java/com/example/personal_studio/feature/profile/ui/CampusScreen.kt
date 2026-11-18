package com.example.personal_studio.feature.profile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.profile.CampusUiState
import com.example.personal_studio.feature.profile.CampusViewModel
import com.example.personal_studio.feature.profile.PollUi
import com.example.personal_studio.feature.profile.Upcoming
import com.example.personal_studio.feature.profile.maskAccount
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.today.pressScale
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.theme.Tg
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 校园: the school account, 一键同步 with what is on this phone, entries to grades, exams,
 * assignments and empty rooms, the two background checks, and logout.
 */
@Composable
fun CampusScreen(
    onOpenGrades: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenEmptyRoom: () -> Unit,
    onLogin: () -> Unit,
    onOpenSync: (start: Boolean) -> Unit,
    vm: CampusViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val sync by vm.syncProgress.collectAsStateWithLifecycle()
    val syncing = sync?.let { !it.done } == true
    val c = Tg.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        AccountCard(st, onLogin, Modifier.enterUp(true, 0))
        if (st.loggedIn) {
            SyncCard(st, syncing, onSync = { onOpenSync(true) }, onOpen = { onOpenSync(false) }, modifier = Modifier.padding(top = 12.dp).enterUp(true, 60))
        }
        Column(Modifier.padding(top = 12.dp).enterUp(true, 120), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryTile(
                    "成绩", Icons.Outlined.BarChart, Modifier.weight(1f),
                    big = st.gpa?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                    note = when {
                        st.gradeCount > 0 -> "${st.gradeCount} 门 · 最近 ${shortTerm(st.latestTerm.orEmpty())}"
                        st.loggedIn -> "还没有成绩，同步后显示"
                        else -> "登录后查看"
                    },
                    accentNote = st.gradeCount > 0,
                    onClick = onOpenGrades,
                )
                EntryTile(
                    "考试", Icons.Outlined.Description, Modifier.weight(1f),
                    big = "${st.examsUpcoming} 场",
                    note = st.nextExam?.let { describe(it, st.now) } ?: "没有待考的考试",
                    onClick = onOpenExams,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryTile(
                    "作业", Icons.Outlined.AssignmentTurnedIn, Modifier.weight(1f),
                    big = "${st.ddlOpen} 条",
                    note = st.nextDdl?.let { "最近 · " + describeTime(it.at, st.now) } ?: "没有未完成的作业",
                    onClick = onOpenAssignments,
                )
                EntryTile(
                    "空教室", Icons.Outlined.MeetingRoom, Modifier.weight(1f),
                    big = null, title = "查空教室",
                    note = "按教学楼和节次查询",
                    onClick = onOpenEmptyRoom,
                )
            }
        }

        Text("后台提醒", style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 8.dp))
        Card {
            PollRow(
                title = "出分提醒", what = "检查一次，有新成绩时通知", poll = st.gradesPoll, enabled = st.loggedIn,
                onToggle = vm::setGradesPoll, onInterval = vm::setGradesInterval, first = true,
            )
            PollRow(
                title = "作业自动同步", what = "同步一次，有新作业时通知", poll = st.ddlPoll, enabled = st.loggedIn,
                onToggle = vm::setDdlPoll, onInterval = vm::setDdlInterval, first = false,
            )
        }
        Text(
            if (st.loggedIn) "后台检查需要联网，系统可能会推迟执行时间。" else "登录学校账户后才能开启后台检查。",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )

        if (st.loggedIn) {
            Text(
                "退出登录在 设置 → 学校账户。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape), content = content)
}

@Composable
private fun AccountCard(st: CampusUiState, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Card(modifier) {
        Row(
            Modifier.padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                if (st.loggedIn) {
                    Text("学", style = MaterialTheme.typography.headlineSmall, color = c.accent)
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                if (st.loggedIn) {
                    Text(maskAccount(st.username.orEmpty()), style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.07f), color = c.text)
                    Text("已保存 · 同步时自动验证", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                    st.networkMode?.let { mode ->
                        Text(
                            "上次连接：" + if (mode == NetworkMode.LOCAL) "学校直连" else "学校 WebVPN",
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                } else {
                    Text("登录学校账户", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("成绩、课表、作业和考试免再输密码", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .then(if (st.loggedIn) Modifier.border(1.dp, c.textTertiary, shape) else Modifier.background(c.text))
                    .clickable(role = Role.Button, onClick = onLogin)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (st.loggedIn) "重新验证" else "登录",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = if (st.loggedIn) c.text else c.bg,
                )
            }
        }
    }
}

@Composable
private fun SyncCard(st: CampusUiState, syncing: Boolean, onSync: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    Card(modifier) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).clickable(onClickLabel = "查看同步详情", onClick = onOpen)) {
                    Text("一键同步", style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.07f), color = c.text)
                    Text(
                        "课表 → 作业 → 考试 → 成绩\n" + when {
                            syncing -> "正在同步…"
                            st.lastSyncAt != null -> "上次 " + TodayText.ago(st.lastSyncAt, st.now)
                            else -> "还没有同步过"
                        },
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    Modifier
                        .height(44.dp)
                        .pressScale(interaction, 0.96f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.accent)
                        .clickable(interaction, indication = null, role = Role.Button, onClick = if (syncing) onOpen else onSync)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    com.example.personal_studio.ui.components.SpinningSyncIcon(syncing, c.onAccent, Modifier.size(16.dp))
                    Text(if (syncing) "同步中" else "同步全部", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = c.onAccent)
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("课表", "${st.courseCount} 节", Modifier.weight(1f))
                Stat("作业", "${st.ddlOpen} 条", Modifier.weight(1f))
                Stat("考试", "${st.examsUpcoming} 场", Modifier.weight(1f))
                Stat("成绩", "${st.gradeCount} 门", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Tg.colors
    Column(modifier.background(c.bg, RoundedCornerShape(14.dp)).padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun EntryTile(
    name: String,
    icon: ImageVector,
    modifier: Modifier,
    big: String?,
    note: String,
    onClick: () -> Unit,
    title: String? = null,
    accentNote: Boolean = false,
) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 132.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        if (big != null) {
            Text(big, style = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = MaterialTheme.typography.displaySmall.fontSize * 1.07f),
                color = c.text, modifier = Modifier.padding(top = 10.dp))
        } else if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(note, style = MaterialTheme.typography.bodySmall, color = if (accentNote) c.accent else c.textSecondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PollRow(
    title: String,
    what: String,
    poll: PollUi,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    first: Boolean,
) {
    val c = Tg.colors
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { if (!first) drawLine(c.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text(
                    if (poll.enabled && enabled) "每 ${poll.intervalHours} 小时$what" else "关闭",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
                )
            }
            TgSwitch(checked = poll.enabled && enabled, onCheckedChange = onToggle, enabled = enabled)
        }
        AnimatedVisibility(
            visible = poll.enabled && enabled,
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
        ) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 12, 24).forEach { h -> IntervalChip("$h 小时", h == poll.intervalHours) { onInterval(h) } }
            }
        }
    }
}

@Composable
private fun IntervalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(18.dp)
    val bg by androidx.compose.animation.animateColorAsState(if (selected) c.text else androidx.compose.ui.graphics.Color.Transparent, tween(200), label = "chipBg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) c.bg else c.text, tween(200), label = "chipFg")
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) c.text else c.textTertiary, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = fg)
    }
}

/** Logout asks whether this phone should also forget the school data (e.g. handing it over). */
private fun describe(u: Upcoming, now: Long): String = describeTime(u.at, now) + " · " + u.title

/** "今天 22:00", "明天 08:00", "后天 19:00", "周五 19:00", "12 月 3 日". */
internal fun describeTime(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val diff = day.toEpochDay() - today.toEpochDay()
    val time = TodayText.hhmm(at, zone)
    return when (diff) {
        0L -> "今天 $time"
        1L -> "明天 $time"
        2L -> "后天 $time"
        in 3L..6L -> "${TodayText.weekday(day)} $time"
        else -> "${day.monthValue} 月 ${day.dayOfMonth} 日"
    }
}

/** "2025-2026-1" → "2025 秋"; other names are shortened. */
internal fun shortTerm(name: String): String {
    Regex("""(\d{4})-(\d{4})-(\d)""").find(name)?.destructured?.let { (y1, y2, sem) ->
        return when (sem) {
            "1" -> "$y1 秋"
            "2" -> "$y2 春"
            "3" -> "$y2 夏"
            else -> "$y1–${y2.takeLast(2)}"
        }
    }
    Regex("""(\d{4})[–-](\d{4})\s*学年\s*第([一二三])学期""").find(name)?.destructured?.let { (y1, y2, n) ->
        return when (n) {
            "一" -> "$y1 秋"
            "二" -> "$y2 春"
            else -> "$y2 夏"
        }
    }
    return name.take(8)
}
