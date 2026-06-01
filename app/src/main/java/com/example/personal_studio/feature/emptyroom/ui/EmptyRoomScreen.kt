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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    LaunchedEffect(Unit) { vm.events.collect { if (it is EmptyRoomEvent.NeedLogin) onNeedLogin() } }

    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(cornerDim = 0.42f, centerGlow = 0.03f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("←", color = FoamMute) }
                Text("$ empty-room", color = Cyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(st.date, color = FoamDim, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmartButton("⚡ 现在去自习", Phosphor, Modifier.weight(1f)) { vm.onSmartNow() }
                SmartButton("↻ 查询", Cyan, Modifier.weight(1f)) { vm.onQuery() }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 2, 3).forEach { h ->
                    FilterChip(if (h == 0) "不限" else "≥${h}h", selected = st.minFreeHours == h) { vm.onMinFreeHours(h) }
                }
            }

            st.error?.let { Spacer(Modifier.height(6.dp)); Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(10.dp))

            when {
                st.loading -> Text("查询中…", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                st.rooms.isEmpty() -> Text("点上方「现在去自习」或「查询」找空教室", color = FoamDim, style = MaterialTheme.typography.labelMedium)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(st.rooms, key = { it.buildingName + it.roomName }) { RoomCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SmartButton(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.border(1.dp, color).background(Deep).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, if (selected) Phosphor else Rule)
            .background(if (selected) Phosphor.copy(alpha = 0.12f) else Deep)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, color = if (selected) Phosphor else FoamMute, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun RoomCard(room: RoomFreeSlots) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${room.buildingName} ${room.roomName}", color = Foam,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StatusBadge(room)
        }
        Spacer(Modifier.height(6.dp))
        Text("空闲 " + prettyFree(room.freeRanges), color = FoamMute, style = MaterialTheme.typography.bodySmall)
        if (expanded) { Spacer(Modifier.height(8.dp)); OccupancyGrid(room.busyPeriods) }
    }
}

@Composable
private fun StatusBadge(room: RoomFreeSlots) {
    val (text, color) = when {
        room.status.freeNow -> "现在空 · 到 ${minuteToHHmm(room.status.freeUntilMinuteOfDay)}" to Phosphor
        room.status.nextFreeMinuteOfDay != null -> "${minuteToHHmm(room.status.nextFreeMinuteOfDay)} 后空" to Cyan
        else -> "今天满" to FoamDim
    }
    Text(text, color = color, style = MaterialTheme.typography.labelMedium)
}

/** 节次×占用 可视化:13 个小格,忙=Carmine,空=Phosphor 淡底。 */
@Composable
private fun OccupancyGrid(busy: Set<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..13).forEach { p ->
            val isBusy = p in busy
            Box(
                Modifier.weight(1f).height(22.dp)
                    .background(if (isBusy) Carmine.copy(alpha = 0.30f) else Phosphor.copy(alpha = 0.18f))
                    .border(1.dp, if (isBusy) Carmine else Phosphor),
                contentAlignment = Alignment.Center,
            ) { Text("$p", color = if (isBusy) Carmine else Phosphor, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun prettyFree(ranges: List<IntRange>): String =
    if (ranges.isEmpty()) "无" else ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}~${it.last}" } + " 节"

private fun minuteToHHmm(m: Int?): String =
    if (m == null) "—" else "%02d:%02d".format(m / 60, m % 60)
