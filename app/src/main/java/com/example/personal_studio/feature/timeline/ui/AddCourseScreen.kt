package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.timeline.ui.components.SemesterStartModal
import com.example.personal_studio.feature.timeline.vm.AddCourseEvent
import com.example.personal_studio.feature.timeline.vm.AddCourseViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AddCourseScreen(
    onBack: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    vm: AddCourseViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            if (ev is AddCourseEvent.RequestNotifPermission) onRequestNotifPermission()
        }
    }

    if (ui.needsSemesterStart) {
        SemesterStartModal(
            onPicked = vm::onSemesterStartPicked,
            onDismiss = onBack,
        )
    }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "add", subtitle = "# new course series", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ui.savedToast?.let {
                Text(it, color = Phosphor)
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2000)
                    vm.consumedToast()
                }
            }

            if (ui.conflicts.isNotEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text("${ui.conflicts.size} 节冲突（仍可保存）", color = Amber) },
                )
            }

            OutlinedTextField(value = ui.title, onValueChange = vm::onTitleChange,
                label = { Text("课名（必填）", color = Phosphor) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = ui.instructor, onValueChange = vm::onInstructorChange,
                label = { Text("老师（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = ui.location, onValueChange = vm::onLocationChange,
                label = { Text("地点（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = ui.notes, onValueChange = vm::onNotesChange,
                label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp))

            Text("星期", color = Phosphor)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7).forEach { (label, code) ->
                    FilterChip(selected = code in ui.weekdays, onClick = { vm.onToggleWeekday(code) }, label = { Text(label) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberStepper("节次起", ui.periodStart, 1, ui.maxPeriod, vm::onPeriodStart, Modifier.weight(1f))
                NumberStepper("节次止", ui.periodEnd, ui.periodStart, ui.maxPeriod, vm::onPeriodEnd, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberStepper("周次起", ui.weekStart, 1, ui.maxWeek, vm::onWeekStart, Modifier.weight(1f))
                NumberStepper("周次止", ui.weekEnd, ui.weekStart, ui.maxWeek, vm::onWeekEnd, Modifier.weight(1f))
            }

            ui.error?.let { Text(it, color = Foam) }

            Button(onClick = vm::save, enabled = ui.saveEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.saving) "保存中…" else "保存")
            }
        }
    }
}

@Composable
private fun NumberStepper(
    label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, color = Phosphor)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("-") }
            Spacer(Modifier.width(8.dp))
            Text(value.toString(), color = Foam)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
        }
    }
}
