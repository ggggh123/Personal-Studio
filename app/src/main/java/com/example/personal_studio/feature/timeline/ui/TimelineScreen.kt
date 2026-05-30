package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.domain.timeline.ComputeBubbleStateUseCase
import com.example.personal_studio.feature.timeline.ui.components.AddItemFab
import com.example.personal_studio.feature.timeline.ui.components.DayStripBar
import com.example.personal_studio.feature.timeline.ui.components.NowIndicator
import com.example.personal_studio.feature.timeline.ui.components.TimelineAxis
import com.example.personal_studio.feature.timeline.ui.components.TimelineAxisSpec
import com.example.personal_studio.feature.timeline.ui.components.TimelineBubble
import com.example.personal_studio.feature.timeline.vm.TimelineViewModel
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onAddTask: () -> Unit,
    onAddCourse: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenWeekGrid: () -> Unit = {},
    onOpenAssignments: () -> Unit = {},
    onOpenExams: () -> Unit = {},
    vm: TimelineViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val state = remember { ComputeBubbleStateUseCase() }
    var expandedCluster by remember { mutableStateOf<List<TimelineItem>?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                var dismissed by remember { mutableStateOf(false) }
                if (!granted && !dismissed) {
                    com.example.personal_studio.feature.timeline.ui.components.NotifPermissionBanner(
                        onOpenSettings = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            ctx.startActivity(intent)
                        },
                        onDismiss = { dismissed = true },
                    )
                }
            }

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
                TextButton(onClick = onOpenAssignments) { Text("作业 ↗") }
                TextButton(onClick = onOpenExams) { Text("考试 ↗") }
                IconButton(onClick = onOpenWeekGrid) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "week grid")
                }
            }

            DayStripBar(
                weekStart = ui.weekStart,
                selectedDay = ui.displayDay,
                dayCounts = ui.dayCounts,
                onSelectDay = vm::onSelectDay,
            )

            // Body — 24h vertical axis with breathing room at top + bottom.
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(20.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val sidePadStart = 56.dp
                        val sidePadEnd = 16.dp
                        val availableWidth = maxWidth - sidePadStart - sidePadEnd
                        val (slots, chips) = remember(ui.items) { layoutItems(ui.items, zone) }

                        TimelineAxis()
                        if (ui.items.isEmpty()) {
                            Box(
                                Modifier.fillMaxWidth().height(TimelineAxisSpec.totalHeightDp.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("今日无安排", color = FoamDim)
                            }
                        }
                        slots.forEach { slot ->
                            val itemWidth = availableWidth * slot.widthFraction
                            val xOffset = sidePadStart + availableWidth * slot.widthFraction * slot.column
                            Box(
                                Modifier
                                    .offset(x = xOffset, y = slot.topDp.dp)
                                    .width(itemWidth)
                                    .padding(end = if (slot.widthFraction < 1f) 6.dp else 0.dp),
                            ) {
                                TimelineBubble(
                                    item = slot.item,
                                    state = state(slot.item, ui.nowEpoch),
                                    onClick = remember(slot.item.id, onOpenDetail) { { onOpenDetail(slot.item.id) } },
                                    outOfRange = slot.outOfRange,
                                    heightDp = slot.heightDp,
                                )
                            }
                        }
                        chips.forEach { chip ->
                            ClusterOverflowChip(
                                count = chip.count,
                                onClick = { expandedCluster = chip.hiddenItems },
                                modifier = Modifier
                                    .offset(x = maxWidth - sidePadEnd - 44.dp, y = chip.topDp.dp),
                            )
                        }
                        if (ui.displayDay == LocalDate.now()) {
                            val displayDayStartEpoch = ui.displayDay.atStartOfDay(zone).toInstant().toEpochMilli()
                            NowIndicator(
                                nowEpoch = ui.nowEpoch,
                                displayDayStartEpoch = displayDayStartEpoch,
                                modifier = Modifier.padding(start = 56.dp, end = 0.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(60.dp))
                }
            }
        }

        AddItemFab(
            onAddTask = onAddTask,
            onAddCourse = onAddCourse,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    expandedCluster?.let { items ->
        ModalBottomSheet(onDismissRequest = { expandedCluster = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "$ ls cluster/",
                    color = Phosphor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${items.size} 项被折叠 · 点击进入详情",
                    color = FoamDim,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    ClusterItemRow(
                        item = item,
                        zone = zone,
                        onClick = {
                            expandedCluster = null
                            onOpenDetail(item.id)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class BubbleLayout(val topDp: Float, val outOfRange: Boolean)

private fun computeBubbleLayout(startEpoch: Long, zone: ZoneId): BubbleLayout {
    val local = Instant.ofEpochMilli(startEpoch).atZone(zone).toLocalDateTime()
    val startMinutes = TimelineAxisSpec.START_HOUR * 60f
    val totalMinutes = (TimelineAxisSpec.END_HOUR - TimelineAxisSpec.START_HOUR) * 60f
    val endMinutes = startMinutes + totalMinutes
    val minutes = local.toLocalTime().hour * 60f + local.toLocalTime().minute
    val raw = ((minutes - startMinutes) / 60f) * TimelineAxisSpec.PX_PER_HOUR_DP
    return when {
        // With a 24h axis, regular events are always in-range; the guards below
        // remain as defense-in-depth in case a future config narrows the window.
        minutes < startMinutes -> BubbleLayout(0f, outOfRange = true)
        minutes > endMinutes -> BubbleLayout((totalMinutes / 60f) * TimelineAxisSpec.PX_PER_HOUR_DP - 56f, outOfRange = true)
        else -> BubbleLayout(raw, outOfRange = false)
    }
}

/** Returns the bubble's intended height for COURSE / EXAM rows (so the bottom
 *  edge aligns with `endAt` on the axis — both are timed blocks). Other types
 *  fall back to wrap-content so DDLs (TASK) and short events stay readable. */
private fun computeBubbleHeight(item: TimelineItem): Dp? {
    if ((item.type != TimelineType.COURSE && item.type != TimelineType.EXAM) || item.endAt == null) return null
    val durationMs = item.endAt - item.startAt
    if (durationMs <= 0) return null
    val durationHours = durationMs / 3_600_000f
    // Floor the bubble at 40dp so a tiny mis-recorded course is still readable.
    val raw = (durationHours * TimelineAxisSpec.PX_PER_HOUR_DP).dp
    return if (raw < 40.dp) 40.dp else raw
}

// ─── Conflict layout ────────────────────────────────────────────────────────

private data class BubbleSlot(
    val item: TimelineItem,
    val topDp: Float,
    val heightDp: Dp?,
    val outOfRange: Boolean,
    val column: Int,          // 0 or 1
    val widthFraction: Float, // 1f for solo, 0.5f for split
)

private data class OverflowChip(
    val topDp: Float,
    val count: Int,
    val hiddenItems: List<TimelineItem>,
)

/**
 * Two-column splitting for overlapping items.
 *
 * Steps:
 * 1. Each item gets a span [startAt, endAt or startAt + 45min] (TASK has null
 *    endAt so we synthesise a 45-min visual span so DDLs near each other count
 *    as overlapping).
 * 2. Sort by start, then group items whose spans transitively overlap.
 * 3. Solo group → full width; pair → half width × 2 columns; 3+ → first 2 in
 *    columns + a "+N" chip placed at the third item's startAt y.
 */
private fun layoutItems(
    items: List<TimelineItem>,
    zone: ZoneId,
): Pair<List<BubbleSlot>, List<OverflowChip>> {
    if (items.isEmpty()) return emptyList<BubbleSlot>() to emptyList()
    val virtualSpanMs = 45L * 60_000L

    data class Span(val item: TimelineItem, val start: Long, val end: Long)

    val spans = items
        .map { Span(it, it.startAt, it.endAt ?: (it.startAt + virtualSpanMs)) }
        .sortedBy { it.start }

    val groups = mutableListOf<MutableList<Span>>()
    for (s in spans) {
        val last = groups.lastOrNull()
        if (last != null && last.maxOf { it.end } > s.start) last.add(s)
        else groups.add(mutableListOf(s))
    }

    val slots = mutableListOf<BubbleSlot>()
    val chips = mutableListOf<OverflowChip>()
    for (group in groups) {
        when (group.size) {
            1 -> slots += makeSlot(group[0].item, column = 0, widthFraction = 1f, zone)
            2 -> {
                slots += makeSlot(group[0].item, column = 0, widthFraction = 0.5f, zone)
                slots += makeSlot(group[1].item, column = 1, widthFraction = 0.5f, zone)
            }
            else -> {
                slots += makeSlot(group[0].item, column = 0, widthFraction = 0.5f, zone)
                slots += makeSlot(group[1].item, column = 1, widthFraction = 0.5f, zone)
                val hiddenItems = group.drop(2).map { it.item }
                val third = group[2].item
                val topDp = computeBubbleLayout(third.startAt, zone).topDp
                chips += OverflowChip(topDp, group.size - 2, hiddenItems)
            }
        }
    }
    return slots to chips
}

private fun makeSlot(
    item: TimelineItem,
    column: Int,
    widthFraction: Float,
    zone: ZoneId,
): BubbleSlot {
    val layout = computeBubbleLayout(item.startAt, zone)
    return BubbleSlot(item, layout.topDp, computeBubbleHeight(item), layout.outOfRange, column, widthFraction)
}

@Composable
private fun ClusterOverflowChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clickable(onClick = onClick)
            .background(Void.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .border(1.dp, Phosphor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("+$count", color = Phosphor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ClusterItemRow(
    item: TimelineItem,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val timeStr = remember(item.startAt, item.endAt) {
        val start = Instant.ofEpochMilli(item.startAt).atZone(zone).toLocalDateTime().toLocalTime()
        val end = item.endAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toLocalTime() }
        if (end == null) "%02d:%02d".format(start.hour, start.minute)
        else "%02d:%02d - %02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeStr,
            color = FoamMute,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = (item.courseName?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") +
                item.title + (item.location?.let { "  ·  $it" } ?: ""),
            color = Foam,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text("→", color = Phosphor, style = MaterialTheme.typography.bodyMedium)
    }
}
