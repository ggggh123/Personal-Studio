package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.timeline.ui.components.DeleteScopeDialog
import com.example.personal_studio.feature.timeline.vm.CourseSeriesEditEvent
import com.example.personal_studio.feature.timeline.vm.CourseSeriesEditViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CourseSeriesEditScreen(
    seriesId: Long,
    onBack: () -> Unit,
    vm: CourseSeriesEditViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            if (ev is CourseSeriesEditEvent.Closed) onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "edit", subtitle = "# course series", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${ui.occurrenceCount} 节 · 第 ${ui.minWeek}-${ui.maxWeek} 周",
                color = FoamDim, style = MaterialTheme.typography.labelMedium)

            OutlinedTextField(value = ui.title, onValueChange = vm::onTitleChange,
                label = { Text("课名", color = Phosphor) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = ui.instructor, onValueChange = vm::onInstructorChange,
                label = { Text("老师") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = ui.location, onValueChange = vm::onLocationChange,
                label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = ui.credits,
                onValueChange = vm::onCreditsChange,
                label = { Text("学分") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(value = ui.notes, onValueChange = vm::onNotesChange,
                label = { Text("备注") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))

            Text("如需调整时间，请删除并重新创建", color = FoamDim, style = MaterialTheme.typography.labelSmall)

            ui.error?.let { Text(it, color = Foam) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::save, enabled = ui.saveEnabled) {
                    Text(if (ui.saving) "保存中…" else "保存")
                }
                OutlinedButton(onClick = vm::openDeleteDialog) { Text("删除整个系列") }
            }
        }
    }

    if (ui.deleteDialogVisible) {
        DeleteScopeDialog(
            seriesTitle = ui.title,
            onDismiss = vm::closeDeleteDialog,
            onConfirm = vm::confirmDelete,
        )
    }
}
