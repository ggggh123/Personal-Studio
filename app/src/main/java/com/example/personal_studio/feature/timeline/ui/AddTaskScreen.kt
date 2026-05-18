package com.example.personal_studio.feature.timeline.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.TimelineType
import com.example.personal_studio.feature.timeline.vm.AddTaskEvent
import com.example.personal_studio.feature.timeline.vm.AddTaskViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun AddTaskScreen(
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    vm: AddTaskViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            when (ev) {
                is AddTaskEvent.Saved -> onSaved(ev.itemId)
                AddTaskEvent.RequestNotifPermission -> onRequestNotifPermission()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "add", subtitle = "# new ddl / event", trailing = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
            }
        })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Type segmented control
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("DDL", selected = ui.type == TimelineType.TASK) { vm.onTypeChange(TimelineType.TASK) }
                TypeChip("事件", selected = ui.type == TimelineType.CUSTOM) { vm.onTypeChange(TimelineType.CUSTOM) }
            }

            OutlinedTextField(
                value = ui.title, onValueChange = vm::onTitleChange,
                label = { Text("标题", color = Phosphor) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            OutlinedTextField(
                value = ui.description, onValueChange = vm::onDescChange,
                label = { Text("描述（可选）", color = FoamDim) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            )

            DateTimePickerRow(
                label = if (ui.type == TimelineType.TASK) "截止时间" else "开始时间",
                epoch = ui.startAtEpoch, onPick = vm::onStartChange, ctx = ctx,
            )

            if (ui.type == TimelineType.CUSTOM) {
                DateTimePickerRow(
                    label = "结束时间", epoch = ui.endAtEpoch, onPick = vm::onEndChange, ctx = ctx,
                )
                OutlinedTextField(
                    value = ui.location, onValueChange = vm::onLocationChange,
                    label = { Text("地点（可选）", color = FoamDim) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            ui.error?.let { Text(it, color = Foam) }

            Button(onClick = vm::save, enabled = ui.saveEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.saving) "保存中…" else "保存")
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun DateTimePickerRow(
    label: String, epoch: Long?, onPick: (Long?) -> Unit, ctx: Context,
) {
    val display = epoch?.let {
        val dt = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
        "${dt.toLocalDate()} ${dt.toLocalTime().withSecond(0).withNano(0)}"
    } ?: "未设置"
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = Phosphor, modifier = Modifier.width(96.dp))
        Spacer(Modifier.width(8.dp))
        Text(display, color = Foam, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { showDateTimePicker(ctx, epoch, onPick) }) { Text("选择") }
    }
}

private fun showDateTimePicker(ctx: Context, current: Long?, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = current?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
        ?: LocalDateTime.now(zone).withSecond(0).withNano(0)
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, hour, minute ->
            val picked = LocalDateTime.of(y, m + 1, d, hour, minute)
            onPick(picked.atZone(zone).toInstant().toEpochMilli())
        }, initial.hour, initial.minute, true).show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}
