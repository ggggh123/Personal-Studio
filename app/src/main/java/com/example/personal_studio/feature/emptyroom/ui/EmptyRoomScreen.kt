package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.emptyroom.EmptyRoomQuery
import com.example.personal_studio.domain.emptyroom.PeriodClock
import com.example.personal_studio.domain.emptyroom.TimeFilter
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.feature.emptyroom.EmptyRoomEvent
import com.example.personal_studio.feature.emptyroom.EmptyRoomUiState
import com.example.personal_studio.feature.emptyroom.EmptyRoomViewModel
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.SpinningSyncIcon
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgDatePickerDialog
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.theme.Tg
import java.time.LocalDate

/**
 * 空教室: a condition card (day, place, the time the room must be free), then the rooms grouped
 * by building, each with its 13 periods drawn and how long it stays free. The place is chosen
 * in [BuildingSheet]; the campus and building list is kept on the phone, so only a query that
 * needs a building's occupancy for a new day signs in to the school.
 */
@Composable
fun EmptyRoomScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: EmptyRoomViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.events.collect { if (it is EmptyRoomEvent.NeedLogin) onNeedLogin() } }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val clock = remember(st.periods) { PeriodClock(st.periods) }
    val place = placeText(st.campus, st.campusBuildings, st.buildingCodes)

    Column(Modifier.fillMaxSize()) {
        TgTopBar(
            route = "空教室",
            subtitle = "空教室\n" + (place ?: "按校区、教学楼和节次查询"),
            onBack = onBack,
            trailing = if (st.targets.isNotEmpty()) {
                {
                    IconButton(onClick = vm::onRefresh, enabled = !st.loading) {
                        SpinningSyncIcon(st.loading, if (st.loading) Tg.colors.accent else Tg.colors.text,
                            contentDescription = if (st.loading) "正在查询" else "重新查询")
                    }
                }
            } else null,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        ) {
            item(key = "conditions") {
                Conditions(
                    st = st, place = place, clock = clock,
                    onDate = vm::onDate, onPickDate = { pickDate = true },
                    onPlace = { if (st.catalog != null) showSheet = true else if (!st.loggedIn) vm.onLogin() },
                    onFilter = vm::onTimeFilter, onCustom = vm::onCustomRange,
                    modifier = Modifier.enterUp(true, 0),
                )
            }
            item(key = "status") { StatusLine(st, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) }
            when {
                !st.loggedIn && st.catalog == null -> item(key = "login") {
                    TgEmptyState("登录后查空教室", "教学楼和占用情况来自教务系统，需要先登录学校账户。",
                        Modifier.padding(top = 12.dp), actionLabel = "登录", onAction = vm::onLogin)
                }
                st.catalog != null && st.campusCode == null -> item(key = "pick") {
                    TgEmptyState("先选地点", "选一个校区和要找的教学楼，下次打开会直接查。",
                        Modifier.padding(top = 12.dp), actionLabel = "选择教学楼", onAction = { showSheet = true })
                }
                st.results != null && st.results!!.matching == 0 -> item(key = "none") {
                    TgEmptyState("没有符合条件的教室", "换个时间段，或者多选几栋楼。", Modifier.padding(top = 12.dp))
                }
            }
            st.results?.groups?.forEach { (building, rooms) ->
                item(key = "b-$building") {
                    Text(
                        "$building · ${rooms.size} 间",
                        style = MaterialTheme.typography.titleMedium, color = Tg.colors.text,
                        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(rooms, key = { "r-" + it.buildingCode + "|" + it.roomName }) { room ->
                    RoomRow(room, st.isToday, if (st.isToday) clock.periodAt(st.nowMinute) else null,
                        Modifier.padding(bottom = 8.dp).animateItem())
                }
            }
            item(key = "end") { androidx.compose.foundation.layout.Box(Modifier.navigationBarsPadding()) }
        }
    }

    if (showSheet && st.catalog != null) {
        BuildingSheet(
            catalog = st.catalog!!,
            campusCode = st.campusCode,
            buildingCodes = st.buildingCodes,
            recent = st.recentBuildingCodes,
            onDone = { campus, codes -> showSheet = false; vm.onPickPlace(campus, codes) },
            onRefreshCatalog = vm::onRefreshCatalog,
            onDismiss = { showSheet = false },
        )
    }
    if (pickDate) {
        TgDatePickerDialog(
            title = "查哪一天",
            initial = st.date,
            onPick = { vm.onDate(it); pickDate = false },
            onDismiss = { pickDate = false },
            hint = "只能查今天和以后",
        )
    }
}

@Composable
private fun Conditions(
    st: EmptyRoomUiState,
    place: String?,
    clock: PeriodClock,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    onPlace: () -> Unit,
    onFilter: (TimeFilter) -> Unit,
    onCustom: (IntRange) -> Unit,
    modifier: Modifier,
) {
    val c = Tg.colors
    TgCard(modifier) {
        // 日期
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Text("日期", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("今天" to 0L, "明天" to 1L, "后天" to 2L).forEach { (label, plus) ->
                    TgChip(label, st.date == st.today.plusDays(plus), onClick = { onDate(st.today.plusDays(plus)) })
                }
                val far = st.date.isAfter(st.today.plusDays(2))
                TgChip(
                    if (far) "${st.date.monthValue} 月 ${st.date.dayOfMonth} 日" else "其他",
                    far, onClick = onPickDate, icon = Icons.Outlined.CalendarMonth,
                )
            }
        }
        Divider()
        // 地点
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClickLabel = "选择教学楼", onClick = onPlace)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("地点", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Text(
                place ?: if (st.catalogLoading) "正在读取教学楼…" else "选择校区和教学楼",
                style = MaterialTheme.typography.bodyLarge,
                color = if (place != null) c.text else c.accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
        Divider()
        // 要空着的时间
        Column(Modifier.padding(16.dp)) {
            Text("要空着的时间", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
            val filters = if (st.isToday) TimeFilter.entries.toList() else TimeFilter.entries.filter { it != TimeFilter.NOW }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                TgSegmented(
                    options = filters.map { it.label },
                    selectedIndex = filters.indexOf(st.effectiveFilter).coerceAtLeast(0),
                    onSelect = { onFilter(filters[it]) },
                    width = maxWidth,
                )
            }
            AnimatedVisibility(
                visible = st.timeFilter == TimeFilter.CUSTOM,
                enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                PeriodRangeBar(st.custom, clock, onCustom, Modifier.padding(top = 12.dp))
            }
            val hint = filterHint(st, clock)
            if (hint != null && st.timeFilter != TimeFilter.CUSTOM) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
            }
        }
    }
}

/** What the chosen time means right now: "第 4 节 · 10:45–11:30", "第 6–10 节 · 13:20–17:40". */
private fun filterHint(st: EmptyRoomUiState, clock: PeriodClock): String? {
    val req = EmptyRoomQuery.required(st.effectiveFilter, st.custom, st.isToday, st.nowMinute, clock)
    return when {
        req.passed -> "这段时间今天已经过去，列出全部教室"
        req.periods.isEmpty() -> if (st.effectiveFilter == TimeFilter.ANY) "列出全部教室，按空闲多少排序" else null
        else -> rangeText(req.periods.min()..req.periods.max(), clock)
    }
}

@Composable
private fun Divider() {
    val c = Tg.colors
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 1.dp, max = 1.dp)
            .drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
    )
}

@Composable
private fun StatusLine(st: EmptyRoomUiState, modifier: Modifier) {
    val c = Tg.colors
    val r = st.results
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val text = when {
            st.error != null -> st.error
            st.catalogLoading && st.catalog == null -> "正在读取校区和教学楼"
            st.loading -> if (r == null) "正在查询" else "正在更新"
            r != null && r.matching > 0 -> buildString {
                append("符合 ${r.matching} 间")
                if (r.matching != r.total) append("（共 ${r.total} 间）")
                if (r.freeNow != null) append(" · 此刻空着 ${r.freeNow} 间")
                if (st.date != st.today) append(" · ${st.date.monthValue} 月 ${st.date.dayOfMonth} 日 ${TodayText.weekday(st.date)}")
            }
            else -> null
        }
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (st.error != null) c.error else c.textSecondary)
            if (st.error == null && (st.loading || (st.catalogLoading && st.catalog == null))) BlinkingCursor()
        }
    }
}

@Composable
private fun RoomRow(room: RoomFreeSlots, today: Boolean, current: Int?, modifier: Modifier) {
    val c = Tg.colors
    val freeNow = today && room.status.freeNow
    TgCard(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.roomName, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(EmptyRoomQuery.statusText(room, today), style = MaterialTheme.typography.bodySmall,
                    color = if (freeNow) c.accent else c.textSecondary)
            }
            OccupancyStrip(room.busyPeriods, current, Modifier.padding(top = 8.dp))
        }
    }
}
