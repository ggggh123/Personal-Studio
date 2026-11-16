package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.timeline.DeleteCourseSeriesUseCase
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.feature.timeline.vm.CourseSeriesEditEvent
import com.example.personal_studio.feature.timeline.vm.CourseSeriesEditViewModel
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.SheetAction
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.flow.collectLatest

/**
 * One course (all its classes): edit the name, teacher, room, credits and notes for every
 * class at once, or delete it — only the classes still to come, or all of them.
 */
@Composable
fun CourseSeriesEditScreen(
    seriesId: Long,
    onBack: () -> Unit,
    vm: CourseSeriesEditViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Tg.colors
    val toast = LocalShellToast.current
    KeyboardHandledByInsets()
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    LaunchedEffect(Unit) {
        vm.events.collectLatest { ev -> if (ev is CourseSeriesEditEvent.Closed) onBack() }
    }
    // A save that finishes without an error gets a short confirmation.
    var wasSaving by remember { mutableStateOf(false) }
    LaunchedEffect(ui.saving, ui.error) {
        if (wasSaving && !ui.saving && ui.error == null) toast?.show("已保存，所有 ${ui.occurrenceCount} 次课都已更新")
        wasSaving = ui.saving
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TerminalTopBar(
            route = "课程",
            subtitle = ui.title.ifBlank { "课程" } + "\n${ui.occurrenceCount} 次课 · 第 ${ui.minWeek}–${ui.maxWeek} 周",
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        ) {
            TgCard(Modifier.enterUp(true, 0)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TgTextField(ui.title, vm::onTitleChange, label = "课名", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.instructor, vm::onInstructorChange, label = "老师", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.location, vm::onLocationChange, label = "地点", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    TgTextField(ui.credits, vm::onCreditsChange, label = "学分",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                    TgTextField(ui.notes, vm::onNotesChange, label = "备注", singleLine = false, minLines = 2)
                }
            }
            Text(
                "改动会应用到这门课的每一次课。要换上课时间，请删除后重新添加。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp).enterUp(true, 60),
            )
            ui.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TgPillButton("删除课程", onClick = vm::openDeleteDialog, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            TgPillButton(if (ui.saving) "正在保存…" else "保存", onClick = vm::save, enabled = ui.saveEnabled, modifier = Modifier.weight(1f))
        }
    }

    if (ui.deleteDialogVisible) {
        TerminalBottomSheet(onDismiss = vm::closeDeleteDialog, header = "删除「${ui.title}」？") {
            SheetAction(
                "只删除以后的课", icon = Icons.Outlined.EventBusy, hint = "已经上过的课留在记录里",
                onClick = { vm.closeDeleteDialog(); vm.confirmDelete(DeleteCourseSeriesUseCase.Scope.FUTURE_ONLY) },
            )
            SheetAction(
                "删除全部 ${ui.occurrenceCount} 次课", icon = Icons.Outlined.DeleteOutline, destructive = true, hint = "包括已经上过的，无法恢复",
                onClick = { vm.closeDeleteDialog(); vm.confirmDeleteAll() },
            )
        }
    }
}
