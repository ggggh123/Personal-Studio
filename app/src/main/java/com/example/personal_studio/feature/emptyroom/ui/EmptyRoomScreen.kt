package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.feature.emptyroom.EmptyRoomEvent
import com.example.personal_studio.feature.emptyroom.EmptyRoomViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

@Composable
fun EmptyRoomScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: EmptyRoomViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.events.collect { if (it is EmptyRoomEvent.NeedLogin) onNeedLogin() }
    }

    // 客户端节次时钟 + 进屏时刻(分钟):用于教室卡「此刻空/占用」与网格高亮当前节。
    val clock = remember { PeriodClock(DefaultTimetable.PERIODS) }
    val nowMin = remember {
        val t = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        t.hour * 60 + t.minute
    }
    val todayStr = remember {
        java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
    val isToday = st.date == todayStr

    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(cornerDim = 0.42f, centerGlow = 0.03f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("←", color = FoamMute) }
                Text("$ empty-room", color = Cyan, style = MaterialTheme.typography.titleMedium)
            }

            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 查询日期(可前后步进)
                item {
                    Spacer(Modifier.height(4.dp))
                    StepLabel("查询日期")
                    Spacer(Modifier.height(6.dp))
                    DateRow(st.date, todayStr, onShift = { vm.onShiftDate(it) }, onToday = { vm.onResetToday() })
                }

                // ① 选校区:整行大条目,可多选
                item {
                    Spacer(Modifier.height(8.dp))
                    StepLabel("① 选择校区(可多选)")
                }
                if (st.campuses.isEmpty()) {
                    item { Hint("加载校区中…") }
                } else {
                    items(st.campuses, key = { "C" + it.code }) { c ->
                        SelectRow(c.name, c.code in st.selectedCampusCodes) { vm.onToggleCampus(c) }
                    }
                }

                // ② 选教学楼:含「全部」,可多选
                if (st.selectedCampusCodes.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)); StepLabel("② 选择教学楼(可多选)") }
                    item { SelectRow("全部教学楼", st.selectedBuildingCodes.isEmpty()) { vm.onSelectAllBuildings() } }
                    items(st.buildings, key = { "B" + it.code }) { b ->
                        SelectRow(b.name, b.code in st.selectedBuildingCodes) { vm.onToggleBuilding(b) }
                    }

                    // ③ 须空闲的节次(可选)
                    item {
                        Spacer(Modifier.height(4.dp))
                        StepLabel("③ 须空闲的节次(可选)")
                        Spacer(Modifier.height(2.dp))
                        Hint("留空=不限;选中的节次须全部空闲")
                        Spacer(Modifier.height(6.dp))
                        PeriodPicker(st.requiredFreePeriods) { vm.onTogglePeriod(it) }
                    }

                    // ④ 查询
                    item {
                        Spacer(Modifier.height(8.dp))
                        QueryButton(if (st.loading) "查询中…" else "↻ 查询空教室", enabled = !st.loading) { vm.onQuery() }
                    }
                }

                st.error?.let { item { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) } }

                // 结果
                item {
                    when {
                        st.loading -> Hint("查询中…")
                        st.selectedCampusCodes.isEmpty() -> Hint("请先选择校区")
                        !st.queried -> Hint(
                            "已选 " + (if (st.selectedBuildingCodes.isEmpty()) "全部教学楼" else "${st.selectedBuildingCodes.size} 栋楼") +
                                ",点上方「查询空教室」",
                        )
                        st.rooms.isEmpty() -> Hint("该范围当日无空闲教室记录")
                        else -> Text("共 ${st.rooms.size} 间", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (st.queried && !st.loading) {
                    items(st.rooms, key = { it.buildingCode + "|" + it.roomName }) { RoomCard(it, nowMin, clock, showNow = isToday) }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(text, color = Cyan, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = FoamDim, style = MaterialTheme.typography.bodyMedium)
}

/** 整行大条目(多选):选中态加粗 phosphor 边框 + 淡底 + ☑ 标记;文字左对齐、完整显示。 */
@Composable
private fun SelectRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, if (selected) Phosphor else Rule)
            .background(if (selected) Phosphor.copy(alpha = 0.10f) else Deep)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "☑" else "☐",
            color = if (selected) Phosphor else FoamMute, style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text, color = if (selected) Foam else FoamMute, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 节次多选:1..13 等宽方格,选中高亮 phosphor。 */
@Composable
private fun PeriodPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..13).forEach { p ->
            val sel = p in selected
            Box(
                Modifier.weight(1f).height(34.dp)
                    .border(if (sel) 2.dp else 1.dp, if (sel) Phosphor else Rule)
                    .background(if (sel) Phosphor.copy(alpha = 0.18f) else Deep)
                    .clickable { onToggle(p) },
                contentAlignment = Alignment.Center,
            ) { Text("$p", color = if (sel) Phosphor else FoamMute, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun QueryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) Cyan else FoamMute
    Box(
        Modifier.fillMaxWidth().border(1.dp, color).background(Deep)
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
}

@Composable
private fun RoomCard(room: RoomFreeSlots, nowMin: Int, clock: PeriodClock, showNow: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    // 仅当查询日期为今天时,「此刻」与当前节高亮才有意义。
    val curPeriod = if (showNow) clock.periodAt(nowMin) else null
    val freeNow = curPeriod == null || curPeriod !in room.busyPeriods
    Column(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${room.buildingName} ${room.roomName}", color = Foam,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (showNow) {
                Spacer(Modifier.width(10.dp))
                Text(
                    if (freeNow) "此刻空" else "此刻占用",
                    color = if (freeNow) Phosphor else Carmine, style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("空闲 " + prettyFree(room.freeRanges), color = FoamMute, style = MaterialTheme.typography.bodySmall)
        if (expanded) { Spacer(Modifier.height(8.dp)); OccupancyGrid(room.busyPeriods, curPeriod) }
    }
}

/** 日期步进行:◀ 周X yyyy-MM-dd ▶,显示「今天/明天/后天」;非今天时可一键回今天。 */
@Composable
private fun DateRow(date: String, todayStr: String, onShift: (Int) -> Unit, onToday: () -> Unit) {
    val d = java.time.LocalDate.parse(date)
    val today = java.time.LocalDate.parse(todayStr)
    val daysFromToday = java.time.temporal.ChronoUnit.DAYS.between(today, d)
    val rel = when (daysFromToday) {
        0L -> "今天"; 1L -> "明天"; 2L -> "后天"; else -> null
    }
    val atToday = daysFromToday == 0L
    Row(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("◀", enabled = !atToday) { onShift(-1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${weekdayCn(d.dayOfWeek.value)}  $date", color = Foam, style = MaterialTheme.typography.titleMedium)
            if (rel != null) {
                Text(rel, color = Cyan, style = MaterialTheme.typography.labelSmall)
            } else {
                Text("↺ 回到今天", color = Amber, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { onToday() })
            }
        }
        StepperButton("▶", enabled = true) { onShift(1) }
    }
}

@Composable
private fun StepperButton(sym: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) Cyan else Rule
    Box(
        Modifier.border(1.dp, color).background(Deep)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(sym, color = color, style = MaterialTheme.typography.titleMedium) }
}

private fun weekdayCn(dow: Int): String =
    "周" + listOf("一", "二", "三", "四", "五", "六", "日")[(dow - 1).coerceIn(0, 6)]

/** 节次×占用 可视化:13 格,忙=Carmine,空=Phosphor 淡底;当前时刻所在节加亮边框。 */
@Composable
private fun OccupancyGrid(busy: Set<Int>, highlight: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..13).forEach { p ->
            val isBusy = p in busy
            val isHi = p == highlight
            Box(
                Modifier.weight(1f).height(22.dp)
                    .background(if (isBusy) Carmine.copy(alpha = 0.30f) else Phosphor.copy(alpha = 0.18f))
                    .border(if (isHi) 2.dp else 1.dp, if (isHi) Cyan else if (isBusy) Carmine else Phosphor),
                contentAlignment = Alignment.Center,
            ) { Text("$p", color = if (isBusy) Carmine else Phosphor, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun prettyFree(ranges: List<IntRange>): String =
    if (ranges.isEmpty()) "无" else ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}~${it.last}" } + " 节"
