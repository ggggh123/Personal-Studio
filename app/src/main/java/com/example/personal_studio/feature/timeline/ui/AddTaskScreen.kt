package com.example.personal_studio.feature.timeline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.ui.components.ValueBox
import com.example.personal_studio.feature.timeline.vm.AddTaskEvent
import com.example.personal_studio.feature.timeline.vm.AddTaskViewModel
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgDatePickerDialog
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.components.TgTimePickerDialog
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Which value a picker is editing. */
private enum class Slot { START, END }

/** Picked date + the slot's current (or default) time, as epoch millis in the phone's zone. */
internal fun withDate(current: Long?, date: LocalDate, defaultTime: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long {
    val time = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: defaultTime
    return LocalDateTime.of(date, time.withSecond(0).withNano(0)).atZone(zone).toInstant().toEpochMilli()
}

/** The slot's current (or today's) date at the picked time. */
internal fun withTime(current: Long?, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val date = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)
    return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

/**
 * 新建待办或日程 (＋ → 新建待办 → 其他时间…): a 待办 has a deadline, a 日程 a start, an end
 * and a place. Dates and times open their own pickers; the end follows the start by an
 * hour until it is set by hand.
 */
@Composable
fun AddTaskScreen(
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    vm: AddTaskViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Tg.colors
    val zone = remember { ZoneId.systemDefault() }
    var datePick by remember { mutableStateOf<Slot?>(null) }
    var timePick by remember { mutableStateOf<Slot?>(null) }
    var endTouched by remember { mutableStateOf(false) }
    KeyboardHandledByInsets()

    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            when (ev) {
                is AddTaskEvent.Saved -> onSaved(ev.itemId)
                AddTaskEvent.RequestNotifPermission -> onRequestNotifPermission()
            }
        }
    }
    val isTask = ui.type == TimelineType.TASK
    fun setStart(epoch: Long) {
        vm.onStartChange(epoch)
        val end = ui.endAtEpoch
        if (!isTask && (!endTouched || end == null || end <= epoch)) vm.onEndChange(epoch + 60 * 60_000L)
    }
    fun setEnd(epoch: Long) { endTouched = true; vm.onEndChange(epoch) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TgTopBar(route = "新建", subtitle = if (isTask) "新建待办\n到截止时间前会提醒你" else "新建日程\n开始前 30 分钟提醒", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TgSegmented(
                options = listOf("待办", "日程"),
                selectedIndex = if (isTask) 0 else 1,
                onSelect = { vm.onTypeChange(if (it == 0) TimelineType.TASK else TimelineType.CUSTOM) },
                modifier = Modifier.enterUp(true, 0),
            )
            TgCard(Modifier.enterUp(true, 40)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TgTextField(ui.title, vm::onTitleChange, label = "标题", placeholder = if (isTask) "例如 交实验报告" else "例如 社团例会",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.description, vm::onDescChange, label = "备注", singleLine = false, minLines = 2)
                    AnimatedVisibility(!isTask, enter = expandVertically(tween(240)) + fadeIn(tween(240)), exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
                        TgTextField(ui.location, vm::onLocationChange, label = "地点", placeholder = "可不填")
                    }
                }
            }
            TgCard(Modifier.enterUp(true, 80)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TimeRow(if (isTask) "截止" else "开始", ui.startAtEpoch, zone,
                        onDate = { datePick = Slot.START }, onTime = { timePick = Slot.START })
                    AnimatedVisibility(!isTask, enter = expandVertically(tween(240)) + fadeIn(tween(240)), exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
                        TimeRow("结束", ui.endAtEpoch, zone, onDate = { datePick = Slot.END }, onTime = { timePick = Slot.END })
                    }
                }
            }
            ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(start = 4.dp)) }
            if (!isTask && ui.startAtEpoch != null && ui.endAtEpoch != null && ui.endAtEpoch!! <= ui.startAtEpoch!!) {
                Text("结束要晚于开始。", style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(start = 4.dp))
            }
        }
        TgPillButton(
            if (ui.saving) "正在保存…" else "保存",
            onClick = vm::save,
            enabled = ui.saveEnabled,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        )
    }

    datePick?.let { slot ->
        val current = if (slot == Slot.START) ui.startAtEpoch else ui.endAtEpoch
        TgDatePickerDialog(
            title = when { slot == Slot.END -> "结束日期"; isTask -> "截止日期"; else -> "开始日期" },
            initial = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone),
            onPick = { d ->
                val default = if (isTask) LocalTime.of(23, 59) else LocalTime.of(9, 0)
                val epoch = withDate(current, d, default, zone)
                if (slot == Slot.START) setStart(epoch) else setEnd(epoch)
                datePick = null
            },
            onDismiss = { datePick = null },
        )
    }
    timePick?.let { slot ->
        val current = if (slot == Slot.START) ui.startAtEpoch else ui.endAtEpoch
        val t = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: if (isTask) LocalTime.of(23, 59) else LocalTime.of(9, 0)
        TgTimePickerDialog(
            title = when { slot == Slot.END -> "结束时间"; isTask -> "截止时间"; else -> "开始时间" },
            initialHour = t.hour, initialMinute = t.minute,
            onPick = { h, m ->
                val epoch = withTime(current, h, m, zone)
                if (slot == Slot.START) setStart(epoch) else setEnd(epoch)
                timePick = null
            },
            onDismiss = { timePick = null },
        )
    }
}

@Composable
private fun TimeRow(label: String, epoch: Long?, zone: ZoneId, onDate: () -> Unit, onTime: () -> Unit) {
    val c = Tg.colors
    val dt = epoch?.let { Instant.ofEpochMilli(it).atZone(zone) }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        ValueBox(
            dt?.let { "${it.monthValue} 月 ${it.dayOfMonth} 日 ${TodayText.weekday(it.toLocalDate())}" },
            placeholder = "选日期", onClick = onDate, label = "选择${label}日期",
        )
        ValueBox(
            dt?.let { "%02d:%02d".format(it.hour, it.minute) },
            placeholder = "时间", onClick = onTime, label = "选择${label}时间",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
