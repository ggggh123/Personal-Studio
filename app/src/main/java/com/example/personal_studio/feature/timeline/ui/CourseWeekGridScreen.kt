package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.util.CourseColorPalette
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.feature.timeline.vm.CourseWeekGridViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

private const val PERIOD_COL_DP = 32   // left column width holding period numbers
private const val ROW_HEIGHT_DP = 56   // per-period cell height
private const val WEEKDAY_HEADER_DP = 32

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CourseWeekGridScreen(
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    vm: CourseWeekGridViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "weekgrid", subtitle = "$ ls week/", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })

        // ── Week-navigation header ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::onPrevWeek) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "prev week")
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "第 ${ui.displayWeekIndex} 周",
                        style = MaterialTheme.typography.titleMedium,
                        color = Foam,
                    )
                    if (ui.isCurrentWeek) {
                        Box(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .background(Phosphor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                .border(1.dp, Phosphor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("今", color = Phosphor, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (ui.semesterStart != null) {
                    Text(
                        text = "${ui.weekStart} ~ ${ui.weekEnd}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FoamDim,
                    )
                }
            }
            IconButton(onClick = vm::onNextWeek) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "next week")
            }
            TextButton(onClick = vm::onCurrentWeek, enabled = ui.semesterStart != null) {
                Text("本周")
            }
        }

        Divider(color = FoamDim)

        // ── Body ───────────────────────────────────────────────────────────────
        when {
            ui.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("loading…", color = FoamDim)
                }
            }
            ui.needsSemesterStart -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "请先到 Settings → 学期设置 设置学期起始日期",
                        color = Foam,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            ui.periods.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("作息表为空", color = FoamDim)
                }
            }
            else -> {
                WeekGridBody(ui = ui, onOpenItem = onOpenItem)
            }
        }
    }
}

@Composable
private fun WeekGridBody(
    ui: com.example.personal_studio.feature.timeline.vm.CourseWeekGridUiState,
    onOpenItem: (Long) -> Unit,
) {
    val periodCount = ui.periods.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ── Weekday header row ──────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(WEEKDAY_HEADER_DP.dp),
        ) {
            Box(Modifier.width(PERIOD_COL_DP.dp).fillMaxWidth(0f))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val cellWidth = maxWidth / 7f
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAY_LABELS.forEach { label ->
                        Box(
                            Modifier.width(cellWidth).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = Phosphor, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Divider(color = FoamDim)

        // ── Grid body ──────────────────────────────────────────────────────────
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height((ROW_HEIGHT_DP * periodCount).dp),
        ) {
            val totalWidth = maxWidth
            val gridWidth = totalWidth - PERIOD_COL_DP.dp
            val cellWidth = gridWidth / 7f

            // Period number column
            Column(
                Modifier
                    .width(PERIOD_COL_DP.dp)
                    .height((ROW_HEIGHT_DP * periodCount).dp),
            ) {
                ui.periods.forEachIndexed { idx, _ ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT_DP.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ui.periods[idx].index.toString(),
                            color = FoamMute,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // Vertical separators between weekday columns
            for (col in 0..7) {
                Box(
                    Modifier
                        .offset(x = PERIOD_COL_DP.dp + cellWidth * col)
                        .width(1.dp)
                        .height((ROW_HEIGHT_DP * periodCount).dp)
                        .background(FoamDim.copy(alpha = 0.3f))
                )
            }
            // Horizontal separators between rows
            for (row in 0..periodCount) {
                Box(
                    Modifier
                        .offset(x = PERIOD_COL_DP.dp, y = (ROW_HEIGHT_DP * row).dp)
                        .width(gridWidth)
                        .height(1.dp)
                        .background(FoamDim.copy(alpha = 0.3f))
                )
            }

            // Course cells.
            //
            // We use a `periodIndex → list-index` map so that a course whose
            // configured `periodIndex` does not appear in `ui.periods` (e.g. a
            // legacy row created before the user shrank the timetable) is
            // simply skipped rather than mis-positioned.
            val periodOrdinal: Map<Int, Int> = ui.periods.mapIndexed { idx, p -> p.index to idx }.toMap()
            ui.coursesByCell.forEach { (key, item) ->
                val (weekday, _) = key
                val startOrdinal = periodOrdinal[item.periodIndex ?: -1] ?: return@forEach
                val endOrdinal = periodOrdinal[item.periodEndIndex ?: item.periodIndex ?: -1] ?: startOrdinal
                if (weekday !in 1..7) return@forEach
                val span = (endOrdinal - startOrdinal + 1).coerceAtLeast(1)
                val xOffset = PERIOD_COL_DP.dp + cellWidth * (weekday - 1)
                val yOffset = (ROW_HEIGHT_DP * startOrdinal).dp
                CourseCell(
                    item = item,
                    onClick = { onOpenItem(item.id) },
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(cellWidth)
                        .height((ROW_HEIGHT_DP * span).dp)
                        .padding(2.dp),
                )
            }
        }
    }
}

@Composable
private fun CourseCell(
    item: TimelineItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = CourseColorPalette.colorFor(item.title)
    val periodLabel = formatPeriodRange(item.periodIndex, item.periodEndIndex)
    Column(
        modifier
            .background(color.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            item.title,
            color = Foam,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            periodLabel,
            color = FoamMute,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.location?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = FoamDim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "第 3 节" or "第 3-4 节". Falls back to "—" if either index is null
 *  (shouldn't happen for COURSE rows but the contract is defensive). */
private fun formatPeriodRange(start: Int?, end: Int?): String = when {
    start == null -> "—"
    end == null || end == start -> "第 $start 节"
    else -> "第 $start-$end 节"
}

