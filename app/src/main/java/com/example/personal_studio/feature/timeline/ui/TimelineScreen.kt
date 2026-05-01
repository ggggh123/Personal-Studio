package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.timeline.ComputeBubbleStateUseCase
import com.example.personal_studio.feature.timeline.ui.components.AddItemFab
import com.example.personal_studio.feature.timeline.ui.components.DayStripBar
import com.example.personal_studio.feature.timeline.ui.components.NowIndicator
import com.example.personal_studio.feature.timeline.ui.components.TimelineAxis
import com.example.personal_studio.feature.timeline.ui.components.TimelineAxisSpec
import com.example.personal_studio.feature.timeline.ui.components.TimelineBubble
import com.example.personal_studio.feature.timeline.vm.TimelineViewModel
import com.example.personal_studio.ui.theme.FoamDim
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TimelineScreen(
    onAddTask: () -> Unit,
    onAddCourse: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: TimelineViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val state = remember { ComputeBubbleStateUseCase() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // Header row: prev / day label / next
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::onPrevDay) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "prev") }
                Text(
                    text = ui.displayDay.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = vm::onNextDay) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "next") }
                TextButton(onClick = vm::onToday) { Text("今日") }
            }

            DayStripBar(
                weekStart = ui.weekStart,
                selectedDay = ui.displayDay,
                dayCounts = ui.dayCounts,
                onSelectDay = vm::onSelectDay,
            )

            // Body
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                TimelineAxis()
                if (ui.items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(TimelineAxisSpec.totalHeightDp.dp), contentAlignment = Alignment.Center) {
                        Text("今日无安排", color = FoamDim)
                    }
                }
                ui.items.forEach { item ->
                    val layout = computeBubbleLayout(item.startAt, zone)
                    Box(
                        Modifier
                            .padding(start = 56.dp, end = 16.dp)
                            .offset(y = layout.topDp.dp)
                            .fillMaxWidth(),
                    ) {
                        TimelineBubble(
                            item = item,
                            state = state(item, ui.nowEpoch),
                            onClick = remember(item.id, onOpenDetail) { { onOpenDetail(item.id) } },
                            outOfRange = layout.outOfRange,
                        )
                    }
                }
                if (ui.displayDay == LocalDate.now()) {
                    val displayDayStartEpoch = ui.displayDay.atStartOfDay(zone).toInstant().toEpochMilli()
                    NowIndicator(nowEpoch = ui.nowEpoch, displayDayStartEpoch = displayDayStartEpoch,
                        modifier = Modifier.padding(start = 56.dp, end = 0.dp))
                }
            }
        }

        AddItemFab(
            onAddTask = onAddTask,
            onAddCourse = onAddCourse,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

private data class BubbleLayout(val topDp: Float, val outOfRange: Boolean)

private fun computeBubbleLayout(startEpoch: Long, zone: ZoneId): BubbleLayout {
    val local = Instant.ofEpochMilli(startEpoch).atZone(zone).toLocalDateTime()
    val sevenAm = TimelineAxisSpec.START_HOUR * 60f
    val totalMinutes = ((TimelineAxisSpec.END_HOUR - TimelineAxisSpec.START_HOUR) + 0.5f) * 60f
    val endMinutes = sevenAm + totalMinutes
    val minutes = local.toLocalTime().hour * 60f + local.toLocalTime().minute
    val raw = ((minutes - sevenAm) / 60f) * TimelineAxisSpec.PX_PER_HOUR_DP
    return when {
        minutes < sevenAm -> BubbleLayout(0f, outOfRange = true)
        minutes > endMinutes -> BubbleLayout((totalMinutes / 60f) * TimelineAxisSpec.PX_PER_HOUR_DP - 56f, outOfRange = true)
        else -> BubbleLayout(raw, outOfRange = false)
    }
}
