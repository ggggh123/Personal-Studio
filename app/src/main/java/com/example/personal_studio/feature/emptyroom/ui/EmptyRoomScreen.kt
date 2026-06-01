package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
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

    // 客户端节次时钟 + 时间轴时刻(分钟);拖动后列表按该时刻空闲重排、网格高亮当前节。
    val clock = remember { PeriodClock(DefaultTimetable.PERIODS) }
    val nowMin = remember {
        val t = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        (t.hour * 60 + t.minute).coerceIn(AXIS_START, AXIS_END)
    }
    var atMinute by remember { mutableStateOf(nowMin) }

    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(cornerDim = 0.42f, centerGlow = 0.03f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("←", color = FoamMute) }
                Text("$ empty-room", color = Cyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(st.date, color = FoamDim, style = MaterialTheme.typography.labelMedium)
            }

            // 智能区:一键现在去自习 / 下节课后(免逐级选)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartButton("⚡ 现在去自习", Phosphor, Modifier.weight(1f)) { vm.onSmartNow() }
                SmartButton("↪ 下节课后", Amber, Modifier.weight(1f)) { vm.onAfterNextClass() }
            }

            // 校区 / 教学楼 筛选
            Spacer(Modifier.height(8.dp))
            FilterBar(st.campuses, st.buildings, st.selectedCampus, st.selectedBuilding,
                onCampus = { vm.onSelectCampus(it) }, onBuilding = { vm.onSelectBuilding(it); vm.onQuery() })

            // 条件筛 + 查询
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 3).forEach { h ->
                        Chip(if (h == 0) "不限" else "≥${h}h", selected = st.minFreeHours == h) { vm.onMinFreeHours(h) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                SmartButton("↻ 查询", Cyan, Modifier.width(76.dp)) { vm.onQuery() }
            }

            st.error?.let { Spacer(Modifier.height(6.dp)); Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }

            // 时间轴:拖动看任意时刻的空教室
            if (st.rooms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TimeAxis(atMinute) { atMinute = it }
            }

            Spacer(Modifier.height(8.dp))
            when {
                st.loading -> Text("查询中…", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                st.rooms.isEmpty() -> Text("点「现在去自习」/「下节课后」一键找,或选校区/楼后查询", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                else -> {
                    val display = st.rooms.sortedWith(
                        compareByDescending<RoomFreeSlots> { freeAt(it, atMinute, clock) }.thenBy { it.roomName },
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(display, key = { it.buildingName + it.roomName }) { RoomCard(it, atMinute, clock) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.border(1.dp, color).background(Deep).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, if (selected) Phosphor else Rule)
            .background(if (selected) Phosphor.copy(alpha = 0.12f) else Deep)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, color = if (selected) Phosphor else FoamMute, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
}

@Composable
private fun FilterBar(
    campuses: List<Campus>, buildings: List<Building>,
    selectedCampus: Campus?, selectedBuilding: Building?,
    onCampus: (Campus) -> Unit, onBuilding: (Building?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (campuses.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            campuses.forEach { c -> Chip(c.name, selectedCampus?.code == c.code) { onCampus(c) } }
        }
        if (buildings.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("全校区", selectedBuilding == null) { onBuilding(null) }
            buildings.forEach { b -> Chip(b.name, selectedBuilding?.code == b.code) { onBuilding(b) } }
        }
    }
}

/** 时间轴滑块:08:00–21:00,拖动选时刻。 */
@Composable
private fun TimeAxis(atMinute: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("时刻 ", color = FoamDim, style = MaterialTheme.typography.labelMedium)
            Text(minuteToHHmm(atMinute), color = Cyan, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = atMinute.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = AXIS_START.toFloat()..AXIS_END.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Phosphor, activeTrackColor = Phosphor, inactiveTrackColor = Rule,
            ),
        )
    }
}

@Composable
private fun RoomCard(room: RoomFreeSlots, atMinute: Int, clock: PeriodClock) {
    var expanded by remember { mutableStateOf(false) }
    val curPeriod = clock.periodAt(atMinute)
    val freeNow = freeAt(room, atMinute, clock)
    Column(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${room.buildingName} ${room.roomName}", color = Foam,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(
                if (freeNow) "此刻空" else "此刻占用",
                color = if (freeNow) Phosphor else Carmine, style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("空闲 " + prettyFree(room.freeRanges), color = FoamMute, style = MaterialTheme.typography.bodySmall)
        if (expanded) { Spacer(Modifier.height(8.dp)); OccupancyGrid(room.busyPeriods, curPeriod) }
    }
}

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

private const val AXIS_START = 8 * 60     // 08:00
private const val AXIS_END = 21 * 60      // 21:00

/** 某时刻教室是否空闲:该时刻所在节次不在忙集(课间也算空)。 */
private fun freeAt(room: RoomFreeSlots, minute: Int, clock: PeriodClock): Boolean {
    val p = clock.periodAt(minute)
    return p == null || p !in room.busyPeriods
}

private fun prettyFree(ranges: List<IntRange>): String =
    if (ranges.isEmpty()) "无" else ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}~${it.last}" } + " 节"

private fun minuteToHHmm(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
