package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.feature.emptyroom.EmptyRoomEvent
import com.example.personal_studio.feature.emptyroom.EmptyRoomViewModel
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.theme.Tg
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Quick picks for the periods that must be free. */
private val PeriodPresets = listOf("上午" to 1..5, "下午" to 6..10, "晚上" to 11..13)

/** "第 1–2、6–9 节"; nothing free reads "全天有课". */
internal fun freeRangesText(ranges: List<IntRange>): String =
    if (ranges.isEmpty()) "全天有课"
    else "第 " + ranges.joinToString("、") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" } + " 节"

/**
 * 空教室: pick a day, campuses and buildings (several at once), optionally the periods that
 * must be free, then query. On today's date rooms free right now come first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyRoomScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: EmptyRoomViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    LaunchedEffect(Unit) {
        vm.events.collect { if (it is EmptyRoomEvent.NeedLogin) onNeedLogin() }
    }
    val zone = remember { ZoneId.systemDefault() }
    val clock = remember { PeriodClock(DefaultTimetable.PERIODS) }
    val nowMin = remember { java.time.LocalTime.now(zone).let { it.hour * 60 + it.minute } }
    val today = remember { LocalDate.now(zone) }
    val date = remember(st.date) { runCatching { LocalDate.parse(st.date) }.getOrNull() ?: today }
    val isToday = date == today
    val current = if (isToday) clock.periodAt(nowMin) else null
    val rooms = remember(st.rooms, current) {
        if (current == null) st.rooms else st.rooms.sortedBy { if (current in it.busyPeriods) 1 else 0 }
    }
    val freeNow = if (current == null) null else rooms.count { current !in it.busyPeriods }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "空教室", subtitle = "空教室\n按校区、教学楼和节次查询", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        ) {
            item(key = "date") {
                DateRow(date, today, onShift = vm::onShiftDate, onToday = vm::onResetToday, modifier = Modifier.enterUp(true, 0))
            }
            item(key = "campusLabel") { TgGroupLabel("校区") }
            item(key = "campuses") {
                if (st.campuses.isEmpty()) {
                    Text(if (st.error != null) "校区没有加载出来" else "正在加载校区…", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        modifier = Modifier.padding(start = 4.dp))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.campuses.forEach { campus ->
                            TgChip(campus.name, campus.code in st.selectedCampusCodes, onClick = { vm.onToggleCampus(campus) })
                        }
                    }
                }
            }
            if (st.selectedCampusCodes.isNotEmpty()) {
                item(key = "buildingLabel") { TgGroupLabel("教学楼") }
                item(key = "buildings") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TgChip("全部", st.selectedBuildingCodes.isEmpty(), onClick = vm::onSelectAllBuildings)
                        st.buildings.forEach { b -> TgChip(b.name, b.code in st.selectedBuildingCodes, onClick = { vm.onToggleBuilding(b) }) }
                    }
                }
                item(key = "periodLabel") { TgGroupLabel("这些节次要空着（不选则不限）") }
                item(key = "periods") {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PeriodPresets.forEach { (label, range) ->
                                val all = range.all { it in st.requiredFreePeriods }
                                TgChip("$label ${range.first}–${range.last}", all, onClick = {
                                    range.filter { (it in st.requiredFreePeriods) == all }.forEach(vm::onTogglePeriod)
                                })
                            }
                        }
                        PeriodPicker(st.requiredFreePeriods, vm::onTogglePeriod, Modifier.padding(top = 12.dp))
                    }
                }
                item(key = "query") {
                    TgPillButton(
                        if (st.loading) "正在查询…" else "查询空教室",
                        onClick = vm::onQuery, enabled = !st.loading, style = PillStyle.ACCENT,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    )
                }
            }
            st.error?.let { err ->
                item(key = "error") {
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                }
            }
            if (st.queried && !st.loading) {
                item(key = "count") {
                    TgGroupLabel(
                        when {
                            rooms.isEmpty() -> "没有符合条件的教室"
                            freeNow != null -> "共 ${rooms.size} 间 · 此刻空着 $freeNow 间"
                            else -> "共 ${rooms.size} 间"
                        },
                    )
                }
                items(rooms, key = { it.buildingCode + "|" + it.roomName }) { room ->
                    RoomCard(room, current, modifier = Modifier.padding(bottom = 10.dp).animateItem())
                }
            } else if (st.selectedCampusCodes.isEmpty() && st.campuses.isNotEmpty()) {
                item(key = "hint") {
                    Text("先选一个或几个校区。", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun DateRow(date: LocalDate, today: LocalDate, onShift: (Int) -> Unit, onToday: () -> Unit, modifier: Modifier) {
    val c = Tg.colors
    val diff = ChronoUnit.DAYS.between(today, date)
    TgCard(modifier) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }, enabled = diff > 0, modifier = Modifier.alpha(if (diff > 0) 1f else 0.35f)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "前一天", tint = c.text)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${date.monthValue} 月 ${date.dayOfMonth} 日 ${TodayText.weekday(date)}", style = MaterialTheme.typography.headlineSmall, color = c.text)
                val rel = when (diff) { 0L -> "今天"; 1L -> "明天"; 2L -> "后天"; else -> null }
                if (rel != null) {
                    Text(rel, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                } else {
                    Text(
                        "回到今天", style = MaterialTheme.typography.bodySmall, color = c.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onToday).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(onClick = { onShift(1) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "后一天", tint = c.text)
            }
        }
    }
}

/** Periods 1–13 as round toggles in two rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodPicker(selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 7) {
        (1..13).forEach { p ->
            val on = p in selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (on) c.text else c.bg)
                    .border(1.dp, if (on) c.text else c.textTertiary, CircleShape)
                    .clickable(role = Role.Checkbox, onClickLabel = "第 $p 节") { onToggle(p) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$p", style = MaterialTheme.typography.bodyMedium, color = if (on) c.bg else c.text)
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomFreeSlots, current: Int?, modifier: Modifier = Modifier) {
    val c = Tg.colors
    var expanded by remember { mutableStateOf(false) }
    val freeNow = current != null && current !in room.busyPeriods
    TgCard(modifier, onClick = { expanded = !expanded }, onClickLabel = if (expanded) "收起占用情况" else "查看每节占用") {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${room.buildingName} ${room.roomName}", style = MaterialTheme.typography.titleMedium, color = c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (current != null) {
                    Text(
                        if (freeNow) "此刻空着" else "此刻有课",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (freeNow) c.accent else c.textSecondary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(if (freeNow) c.accentSoft else c.bg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text("空闲 " + freeRangesText(room.freeRanges), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp))
            AnimatedVisibility(expanded, enter = expandVertically(tween(240)) + fadeIn(tween(240)), exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
                OccupancyBar(room.busyPeriods, current, Modifier.padding(top = 12.dp))
            }
        }
    }
}

/** 13 cells: free in the accent wash, busy in a faint text tone; the current period is outlined. */
@Composable
private fun OccupancyBar(busy: Set<Int>, highlight: Int?, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (1..13).forEach { p ->
                val isBusy = p in busy
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isBusy) c.text.copy(alpha = 0.12f) else c.accentSoft)
                        .then(if (p == highlight) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(6.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$p", style = MaterialTheme.typography.labelSmall, color = if (isBusy) c.textSecondary else c.accent)
                }
            }
        }
        Text("浅色为空闲，灰色为有课" + if (highlight != null) "，描边是现在这一节" else "", style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}
