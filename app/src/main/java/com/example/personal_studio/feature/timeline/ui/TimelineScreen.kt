package com.example.personal_studio.feature.timeline.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.DayActivityCount
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.timeline.today.DayTimeline
import com.example.personal_studio.feature.timeline.today.DayStrip
import com.example.personal_studio.feature.timeline.today.DetailAction
import com.example.personal_studio.feature.timeline.today.ItemDetailContent
import com.example.personal_studio.feature.timeline.today.MorphSheet
import com.example.personal_studio.feature.timeline.today.MorphSheetState
import com.example.personal_studio.feature.timeline.today.NextItemCard
import com.example.personal_studio.feature.timeline.today.Taglines
import com.example.personal_studio.feature.timeline.today.TodayHeader
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.feature.timeline.today.WeekView
import com.example.personal_studio.feature.timeline.today.buildDayRows
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.today.nextCardText
import com.example.personal_studio.feature.timeline.today.nextUp
import com.example.personal_studio.feature.timeline.today.periodOf
import com.example.personal_studio.feature.timeline.today.semesterWeek
import com.example.personal_studio.feature.timeline.ui.components.NotifPermissionBanner
import com.example.personal_studio.feature.timeline.vm.TimelineUiState
import com.example.personal_studio.feature.timeline.vm.TimelineViewModel
import com.example.personal_studio.feature.timeline.vm.TodayView
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.shell.InShellOverlay
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.LocalDaylightPreview
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import com.example.personal_studio.ui.theme.daylight.SolarCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private data class DetailRequest(val id: Long, val origin: Rect?)

private data class DayContent(val day: LocalDate, val items: List<TimelineItem>)

/**
 * 今日: header with horizon and tagline, the next-item card, the week's date strip, then the
 * selected day as a timeline (swipe sideways for the next / previous day) or the week grid.
 * Items open as a sheet that grows out of the tapped row.
 */
@Composable
fun TimelineScreen(
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenCourseSeries: (Long) -> Unit,
    vm: TimelineViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val c = Tg.colors
    val daylight = Tg.daylight
    val preview = LocalDaylightPreview.current
    DisposableEffect(preview) { onDispose { preview(null) } }

    // The entrance (rising sun, staggered blocks) plays once per visit of the back stack entry.
    var played by rememberSaveable { mutableStateOf(false) }
    val play = remember { !played }
    LaunchedEffect(Unit) {
        delay(1_600)
        played = true
    }

    var detail by remember { mutableStateOf<DetailRequest?>(null) }
    // Outlives the sheet: "标记完成" ticks the row only after the sheet has folded away.
    val screenScope = rememberCoroutineScope()
    val open: (TimelineItem, Rect?) -> Unit = { item, origin -> detail = DetailRequest(item.id, origin) }

    val solar = remember { SolarCalculator() }
    val sun = daylight?.sun ?: solar.sunDay(ui.today)
    val liveHour = daylight?.liveHour ?: hourOf(ui.now, zone)
    val shownHour = daylight?.hour ?: liveHour
    val facts = remember(ui.todayItems, ui.tomorrowItems, ui.now, liveHour) {
        Taglines.facts(ui.todayItems, ui.tomorrowItems, ui.now, liveHour)
    }
    val tagline = Taglines.pick(periodOf(shownHour, sun), shownHour, facts, ui.today.dayOfYear)

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            TodayHeader(
                date = ui.today,
                week = semesterWeek(ui.semesterStart, ui.today),
                hour = liveHour,
                sun = sun,
                tagline = tagline,
                syncLabel = when {
                    ui.syncing -> "同步中"
                    ui.syncKnown -> ui.lastSyncAt?.let { "已同步 · ${TodayText.ago(it, ui.now)}" } ?: "一键同步"
                    else -> null
                },
                play = play,
                onOpenSettings = onOpenSettings,
                onOpenSync = onOpenSync,
                onPreview = preview,
            )
            NotificationsHint()
            NextItemCard(
                text = remember(ui.todayItems, ui.tomorrowItems, ui.now) { nextCardText(nextUp(ui.todayItems, ui.tomorrowItems, ui.now), zone) },
                onOpen = { origin ->
                    val next = nextUp(ui.todayItems, ui.tomorrowItems, ui.now)
                    val id = nextCardText(next, zone).itemId
                    if (id != null) detail = DetailRequest(id, origin)
                },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).enterUp(play, 180),
            )
            DayStrip(
                weekStart = ui.weekStart,
                selected = ui.selectedDay,
                today = ui.today,
                counts = remember(ui.weekItems) { countsByDay(ui.weekItems, zone) },
                onSelect = vm::onSelectDay,
                modifier = Modifier.padding(top = 18.dp).enterUp(play, 240),
            )
            SectionHeader(ui, onView = vm::onView, onToday = vm::onToday)
            AnimatedContent(
                targetState = ui.view,
                transitionSpec = {
                    val toWeek = targetState == TodayView.WEEK
                    (fadeIn(tween(300)) + scaleIn(TgMotion.standard(), initialScale = if (toWeek) 1.03f else 0.97f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(TgMotion.standard(), targetScale = if (toWeek) 0.97f else 1.03f)) using
                        SizeTransform(clip = false)
                },
                label = "todayView",
            ) { view ->
                when (view) {
                    TodayView.DAY -> SwipeableDay(ui, zone, play, open, vm)
                    TodayView.WEEK -> WeekView(
                        weekStart = ui.weekStart,
                        today = ui.today,
                        now = ui.now,
                        zone = zone,
                        periods = ui.periods,
                        items = ui.weekItems,
                        onPrevWeek = { vm.onShiftWeek(-1) },
                        onNextWeek = { vm.onShiftWeek(1) },
                        onThisWeek = vm::onToday,
                        onOpen = open,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // Content scrolls under the transparent status bar; a strip of page colour keeps the
        // clock readable once the header has moved up.
        val veil by androidx.compose.animation.core.animateFloatAsState(if (scroll.value > 0) 0.97f else 0f, tween(200), label = "veil")
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .graphicsLayer { alpha = veil }
                .background(c.bg),
        )
    }

    detail?.let { request ->
        DetailSheet(
            request = request,
            ui = ui,
            zone = zone,
            vm = vm,
            screenScope = screenScope,
            onOpenCourseSeries = onOpenCourseSeries,
            onDismissed = { detail = null },
        )
    }
}

@Composable
private fun SectionHeader(ui: TimelineUiState, onView: (TodayView) -> Unit, onToday: () -> Unit) {
    val c = Tg.colors
    val isWeek = ui.view == TodayView.WEEK
    val day = ui.selectedDay
    val title = when {
        isWeek -> semesterWeek(ui.semesterStart, ui.weekStart.plusDays(6))?.let { "第 $it 周" } ?: "本周课表"
        day == ui.today -> "今天"
        day == ui.today.plusDays(1) -> "明天"
        day == ui.today.minusDays(1) -> "昨天"
        else -> "${TodayText.weekday(day)} · ${day.monthValue} 月 ${day.dayOfMonth} 日"
    }
    val count = if (!isWeek && ui.dayItemsDay == day && ui.dayItems.isNotEmpty()) "${ui.dayItems.size} 项" else ""
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (count.isNotEmpty()) {
                Text(count, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.textSecondary, modifier = Modifier.padding(start = 8.dp))
            }
            val away = if (isWeek) ui.weekStart != ui.today.minusDays(ui.today.dayOfWeek.value - 1L) else day != ui.today
            androidx.compose.animation.AnimatedVisibility(
                visible = away,
                enter = fadeIn(TgMotion.fast()) + scaleIn(TgMotion.fast(), initialScale = 0.8f),
                exit = fadeOut(TgMotion.exit()) + scaleOut(TgMotion.exit(), targetScale = 0.8f),
            ) {
                Text(
                    "回到今天",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToday)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
        TgSegmented(
            options = listOf("日", "周"),
            selectedIndex = if (isWeek) 1 else 0,
            onSelect = { onView(if (it == 1) TodayView.WEEK else TodayView.DAY) },
            width = 108.dp,
        )
    }
}

/** The day's timeline; a sideways drag follows the finger and past 60 dp turns the day. */
@Composable
private fun SwipeableDay(
    ui: TimelineUiState,
    zone: ZoneId,
    play: Boolean,
    open: (TimelineItem, Rect?) -> Unit,
    vm: TimelineViewModel,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 60.dp.toPx() }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dx = drag.value
                        if (abs(dx) > threshold) {
                            vm.onShiftDay(if (dx < 0) 1 else -1)
                            scope.launch { drag.snapTo(0f) }
                        } else {
                            scope.launch { drag.animateTo(0f, TgMotion.fast()) }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, TgMotion.fast()) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + amount * 0.85f) }
                    },
                )
            }
            .graphicsLayer { translationX = drag.value },
    ) {
        AnimatedContent(
            targetState = DayContent(ui.dayItemsDay, ui.dayItems),
            contentKey = { it.day },
            transitionSpec = {
                val forward = targetState.day.isAfter(initialState.day)
                (slideInHorizontally(TgMotion.standard<IntOffset>()) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(TgMotion.exit<IntOffset>()) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160))) using
                    SizeTransform(clip = false)
            },
            label = "day",
        ) { content ->
            val rows = remember(content, ui.today, ui.now, ui.checkPhases) {
                buildDayRows(content.items, content.day, ui.today, ui.now, holding = ui.checkPhases.keys)
            }
            DayTimeline(
                rows = rows,
                now = ui.now,
                zone = zone,
                checkPhases = ui.checkPhases,
                flashId = ui.flashId,
                showDone = ui.showDone,
                play = play,
                onOpen = open,
                onCheck = vm::onCheck,
                onUndo = vm::onUndo,
                onToggleDone = vm::onToggleShowDone,
            )
        }
    }
}

@Composable
private fun DetailSheet(
    request: DetailRequest,
    ui: TimelineUiState,
    zone: ZoneId,
    vm: TimelineViewModel,
    screenScope: kotlinx.coroutines.CoroutineScope,
    onOpenCourseSeries: (Long) -> Unit,
    onDismissed: () -> Unit,
) {
    // Keep showing the last known item while the sheet closes (e.g. after deleting it).
    var lastItem by remember { mutableStateOf<TimelineItem?>(null) }
    val found = ui.findItem(request.id)
    if (found != null) lastItem = found
    val item = found ?: lastItem
    if (item == null) {
        LaunchedEffect(Unit) { onDismissed() }
        return
    }
    val toast = LocalShellToast.current
    var confirmDelete by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<MorphSheetState?>(null) }

    InShellOverlay {
        MorphSheet(origin = request.origin, title = item.title, onDismissed = onDismissed) { state ->
            sheet = state
            val actions = buildList {
                when (item.type) {
                    TimelineType.COURSE -> {
                        add(DetailAction("删除本次", primary = false) { confirmDelete = true })
                        item.seriesId?.let { sid ->
                            add(DetailAction("管理整个系列", primary = true) {
                                state.close()
                                onOpenCourseSeries(sid)
                            })
                        }
                    }
                    TimelineType.TASK -> {
                        add(DetailAction("删除", primary = false) { confirmDelete = true })
                        add(DetailAction(if (item.isDone) "标记未完成" else "标记完成", primary = true) {
                            state.close()
                            screenScope.launch {
                                delay(420)
                                if (item.isDone) vm.onUndo(item.id) else vm.onCheck(item.id)
                            }
                        })
                    }
                    TimelineType.EXAM -> add(DetailAction(if (item.isDone) "取消已考" else "标记已考", primary = true) {
                        vm.onSetDone(item.id, !item.isDone)
                        if (!item.isDone) toast?.show("已标记为已考，只记录在本机")
                    })
                    TimelineType.CUSTOM -> {
                        add(DetailAction("删除", primary = false) { confirmDelete = true })
                        add(DetailAction(if (item.isDone) "标记未完成" else "标记完成", primary = true) {
                            vm.onSetDone(item.id, !item.isDone)
                        })
                    }
                }
            }
            ItemDetailContent(item = item, now = ui.now, zone = zone, actions = actions)
        }
        if (confirmDelete) {
            TerminalConfirmDialog(
                title = if (item.type == TimelineType.COURSE) "删除这次课？" else "删除这一项？",
                message = if (item.type == TimelineType.COURSE) "只删除 ${dateLabel(item, zone)} 这一次，其他周次不变。"
                else "“${item.title}”会从日程中移除，提醒也会一并取消。",
                confirmLabel = "删除",
                onConfirm = {
                    confirmDelete = false
                    vm.onDelete(item.id)
                    sheet?.close()
                    toast?.show("已删除")
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

/** API 33+: a dismissible hint while notifications are off, linking to the system setting. */
@Composable
private fun NotificationsHint() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    // Re-checked on every resume: the permission dialog and the system settings both pause us.
    var granted by remember { mutableStateOf(check()) }
    LifecycleResumeEffect(Unit) {
        granted = check()
        onPauseOrDispose { }
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (granted || dismissed) return
    Box(Modifier.padding(top = 14.dp)) {
        NotifPermissionBanner(
            onOpenSettings = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                )
            },
            onDismiss = { dismissed = true },
        )
    }
}

private fun countsByDay(items: List<TimelineItem>, zone: ZoneId): Map<LocalDate, DayActivityCount> =
    items.groupBy { Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() }.mapValues { (_, list) ->
        DayActivityCount(
            courses = list.count { it.type == TimelineType.COURSE },
            tasks = list.count { (it.type == TimelineType.TASK || it.type == TimelineType.EXAM) && !it.isDone },
        )
    }

private fun hourOf(epoch: Long, zone: ZoneId): Double {
    val t = Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime()
    return t.hour + t.minute / 60.0
}

private fun dateLabel(item: TimelineItem, zone: ZoneId): String {
    val d = Instant.ofEpochMilli(item.startAt).atZone(zone).toLocalDate()
    return "${d.monthValue} 月 ${d.dayOfMonth} 日"
}
