package com.example.personal_studio.feature.timeline.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.util.CourseColorPalette
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.feature.timeline.vm.CourseWeekGridViewModel
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

private const val PERIOD_COL_DP = 36     // left column width holding period numbers
private const val ROW_HEIGHT_DP = 56     // per-period cell height
private const val WEEKDAY_HEADER_DP = 28
private const val GAP_ROW_DP = 56        // 午休 / 晚饭 — same height as a normal period row

/** Gaps are inserted AFTER these period indices: between 5 and 6 (午休) and
 *  between 10 and 11 (晚饭). Detection is per-index, so a shorter user
 *  timetable (no period 10) simply won't render that gap. */
private val GAP_AFTER_PERIODS = setOf(5, 10)
private fun gapLabel(afterPeriod: Int): String = when (afterPeriod) {
    5 -> "午"
    10 -> "晚"
    else -> ""
}

/** Default visible weekday columns. The grid renders 7 columns total but
 *  the column width is computed against this number, so Mon-Fri fit on
 *  screen and Sat-Sun are revealed by horizontal scroll. */
private const val VISIBLE_WEEKDAY_COLS = 5

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CourseWeekGridScreen(
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    vm: CourseWeekGridViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()       // keep header below the system status bar (clock / battery)
            .navigationBarsPadding(),  // keep grid above the system 3-button nav
    ) {
        // ── Slim header (replaces TerminalTopBar) ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
            }
            IconButton(onClick = vm::onPrevWeek, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "prev week")
            }
            Text(
                text = "第 ${ui.displayWeekIndex} 周",
                color = Foam,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (ui.semesterStart != null) {
                Text(
                    text = "${ui.weekStart} ~ ${ui.weekEnd}",
                    color = FoamDim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 4.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (ui.isCurrentWeek) {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .background(Phosphor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .border(1.dp, Phosphor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text("今", color = Phosphor, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = vm::onNextWeek, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "next week")
            }
            // Quick jump back to current week — phosphor-tinted to stand out.
            IconButton(
                onClick = vm::onCurrentWeek,
                enabled = ui.semesterStart != null,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Today,
                    contentDescription = "this week",
                    tint = if (ui.semesterStart != null) Phosphor else FoamDim,
                )
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

    // Pre-compute Y offsets for every period (taking gap rows into account)
    // and the total grid height so cell positions can be absolute.
    val periodTops = remember(ui.periods) {
        var acc = 0
        ui.periods.map { p ->
            val y = acc
            acc += ROW_HEIGHT_DP
            if (p.index in GAP_AFTER_PERIODS) acc += GAP_ROW_DP
            y
        }
    }
    val totalGridHeightDp = remember(ui.periods) {
        var acc = 0
        ui.periods.forEach { p ->
            acc += ROW_HEIGHT_DP
            if (p.index in GAP_AFTER_PERIODS) acc += GAP_ROW_DP
        }
        acc
    }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val parentWidthDp = maxWidth
        val weekdayCellDp = (parentWidthDp - PERIOD_COL_DP.dp) / VISIBLE_WEEKDAY_COLS
        val totalContentWidthDp = PERIOD_COL_DP.dp + weekdayCellDp * 7

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(vScroll),
        ) {
            // Weekday header row — horizontally scrolls together with the grid body.
            // Today's weekday gets a phosphor breathing badge when the current
            // week is being displayed, so the user instantly sees "today is X".
            val todayWeekdayCode: Int? = if (ui.isCurrentWeek) {
                java.time.LocalDate.now().dayOfWeek.value
            } else null
            Row(
                Modifier
                    .horizontalScroll(hScroll)
                    .width(totalContentWidthDp)
                    .height(WEEKDAY_HEADER_DP.dp),
            ) {
                Spacer(Modifier.width(PERIOD_COL_DP.dp))
                WEEKDAY_LABELS.forEachIndexed { i, label ->
                    val isToday = (i + 1) == todayWeekdayCode
                    Box(
                        Modifier.width(weekdayCellDp).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isToday) {
                            BreathingTodayBadge {
                                Text(label, color = Phosphor, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(label, color = Phosphor, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Divider(color = FoamDim)

            Box(
                Modifier
                    .horizontalScroll(hScroll)
                    .width(totalContentWidthDp)
                    .height(totalGridHeightDp.dp),
            ) {
                // ── Period column (number labels + gap labels) ──────────────────
                Box(
                    Modifier.width(PERIOD_COL_DP.dp).height(totalGridHeightDp.dp),
                ) {
                    ui.periods.forEachIndexed { idx, p ->
                        // Period number cell
                        Box(
                            Modifier
                                .offset(y = periodTops[idx].dp)
                                .fillMaxWidth()
                                .height(ROW_HEIGHT_DP.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                p.index.toString(),
                                color = FoamMute,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        // Gap label (only for periods that trigger a gap)
                        if (p.index in GAP_AFTER_PERIODS) {
                            Box(
                                Modifier
                                    .offset(y = (periodTops[idx] + ROW_HEIGHT_DP).dp)
                                    .fillMaxWidth()
                                    .height(GAP_ROW_DP.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    gapLabel(p.index),
                                    color = FoamDim,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                // ── Vertical separators (8 lines for 7 weekday columns) ─────────
                for (col in 0..7) {
                    Box(
                        Modifier
                            .offset(x = PERIOD_COL_DP.dp + weekdayCellDp * col)
                            .width(1.dp)
                            .height(totalGridHeightDp.dp)
                            .background(FoamDim.copy(alpha = 0.3f)),
                    )
                }

                // ── Horizontal separators (top of every period row + bottom edge)
                periodTops.forEach { y ->
                    Box(
                        Modifier
                            .offset(x = PERIOD_COL_DP.dp, y = y.dp)
                            .width(weekdayCellDp * 7)
                            .height(1.dp)
                            .background(FoamDim.copy(alpha = 0.3f)),
                    )
                }
                // Top edge of each gap row (between a GAP_AFTER_PERIODS row and
                // its following period row). Without this the gap row visually
                // bleeds into the period above it.
                ui.periods.forEachIndexed { idx, p ->
                    if (p.index in GAP_AFTER_PERIODS) {
                        Box(
                            Modifier
                                .offset(
                                    x = PERIOD_COL_DP.dp,
                                    y = (periodTops[idx] + ROW_HEIGHT_DP).dp,
                                )
                                .width(weekdayCellDp * 7)
                                .height(1.dp)
                                .background(FoamDim.copy(alpha = 0.3f)),
                        )
                    }
                }
                // Bottom edge
                Box(
                    Modifier
                        .offset(x = PERIOD_COL_DP.dp, y = totalGridHeightDp.dp)
                        .width(weekdayCellDp * 7)
                        .height(1.dp)
                        .background(FoamDim.copy(alpha = 0.3f)),
                )

                // ── Course cells ───────────────────────────────────────────────
                val periodOrdinal: Map<Int, Int> = remember(ui.periods) {
                    ui.periods.mapIndexed { idx, p -> p.index to idx }.toMap()
                }
                ui.coursesByCell.forEach { (key, item) ->
                    val (weekday, _) = key
                    val startOrdinal = periodOrdinal[item.periodIndex ?: -1] ?: return@forEach
                    val endOrdinal = periodOrdinal[item.periodEndIndex ?: item.periodIndex ?: -1]
                        ?: startOrdinal
                    if (weekday !in 1..7) return@forEach
                    val xOffset = PERIOD_COL_DP.dp + weekdayCellDp * (weekday - 1)
                    val yOffset = periodTops[startOrdinal].dp
                    val cellHeight =
                        (periodTops[endOrdinal] + ROW_HEIGHT_DP - periodTops[startOrdinal]).dp
                    CourseCell(
                        item = item,
                        onClick = { onOpenItem(item.id) },
                        modifier = Modifier
                            .offset(x = xOffset, y = yOffset)
                            .width(weekdayCellDp)
                            .height(cellHeight)
                            .padding(2.dp),
                    )
                }
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
    val locationStr = item.location?.takeIf { it.isNotBlank() }
    Column(
        modifier
            .background(color.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // All three lines wrap freely — no maxLines cap, no ellipsis.
        // Title stays left-aligned (default); period + location are centred
        // horizontally inside the cell to match the official layout style.
        Text(
            item.title,
            color = Foam,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            periodLabel,
            color = FoamMute,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (locationStr != null) {
            Text(
                locationStr,
                color = FoamDim,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "3节" or "3-5节" — compact, no leading 第 / no spaces. Falls back to "—"
 *  if either index is null (defensive; shouldn't happen for COURSE rows). */
private fun formatPeriodRange(start: Int?, end: Int?): String = when {
    start == null -> "—"
    end == null || end == start -> "${start}节"
    else -> "$start-${end}节"
}

/** Phosphor-bordered box around today's weekday label, with the alpha of the
 *  border + background pulsing on a 1.2s cycle so the user's eye lands on it
 *  immediately. Used only on the current week's grid header. */
@Composable
private fun BreathingTodayBadge(content: @Composable () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "today-pulse")
    val phase by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "phase",
    )
    Box(
        Modifier
            .border(1.dp, Phosphor.copy(alpha = phase), RoundedCornerShape(4.dp))
            .background(Phosphor.copy(alpha = phase * 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        content()
    }
}
