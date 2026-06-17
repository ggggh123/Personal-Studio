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
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun TimetableEditorScreen(
    onBack: () -> Unit,
    vm: TimetableEditorViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TerminalTopBar(route = "timetable", subtitle = "$ ls timetable/", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })

        Text(
            "※ 当前已按照北京理工大学作息表自动设置，不建议手动更改，否则可能导致课程表等功能发生异常",
            color = Amber,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

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

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = vm::onAddRow,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("+ 添加节次", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = vm::onRemoveLast,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("- 删除最后", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = vm::onResetDefault,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("恢复默认", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
        }
        Button(
            onClick = vm::openConfirmDialog,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Text(if (ui.saving) "保存中…" else "保存")
        }
    }

    if (ui.confirmDialogVisible) {
        TerminalConfirmDialog(
            title = "确认更新作息表",
            message = "将更新所有未来课程的起止时间。",
            confirmLabel = "确认",
            onConfirm = { vm.save(onComplete = { _ -> /* Phase 5 reschedules */ }) },
            onDismiss = vm::closeConfirmDialog,
        )
    }
}
