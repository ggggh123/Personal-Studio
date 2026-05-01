package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.TimetableEditorViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun TimetableEditorScreen(
    onBack: () -> Unit,
    vm: TimetableEditorViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "timetable", subtitle = "$ ls timetable/", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })

        ui.error?.let { msg ->
            Text(msg, color = Foam, modifier = Modifier.padding(16.dp))
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                vm.consumedError()
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            items(ui.periods, key = { it.index }) { p ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${p.index}", color = Phosphor, modifier = Modifier.width(24.dp))
                    OutlinedTextField(
                        value = p.startHHmm,
                        onValueChange = { vm.onPeriodChange(p.index, startHHmm = it, endHHmm = null) },
                        label = { Text("起 HH:mm") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = p.endHHmm,
                        onValueChange = { vm.onPeriodChange(p.index, startHHmm = null, endHHmm = it) },
                        label = { Text("止 HH:mm") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::onAddRow) { Text("+ 添加节次") }
            OutlinedButton(onClick = vm::onRemoveLast) { Text("- 删除最后") }
            OutlinedButton(onClick = vm::onResetDefault) { Text("恢复默认") }
        }
        Button(onClick = vm::openConfirmDialog, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (ui.saving) "保存中…" else "保存")
        }
    }

    if (ui.confirmDialogVisible) {
        AlertDialog(
            onDismissRequest = vm::closeConfirmDialog,
            title = { Text("确认更新作息表？") },
            text = { Text("将更新所有未来课程的起止时间。") },
            confirmButton = {
                TextButton(onClick = { vm.save(onComplete = { _ -> /* Phase 5 reschedules */ }) }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = vm::closeConfirmDialog) { Text("取消") } },
        )
    }
}
