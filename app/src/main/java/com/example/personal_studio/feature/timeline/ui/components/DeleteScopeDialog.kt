package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.timeline.DeleteCourseSeriesUseCase

@Composable
fun DeleteScopeDialog(
    seriesTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (DeleteCourseSeriesUseCase.Scope) -> Unit,
) {
    // Default to ALL: the entry button reads "删除整个系列", and for imported
    // semester schedules the natural intent is "remove the whole thing". The
    // prior FUTURE_ONLY default left historical occurrences behind, which kept
    // showing up on the Timeline (past days) even though the week-grid's
    // future weeks looked cleared — a confusing mismatch.
    var choice by remember { mutableStateOf(DeleteCourseSeriesUseCase.Scope.ALL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 $seriesTitle") },
        text = {
            Column {
                Row(Modifier.selectable(selected = choice == DeleteCourseSeriesUseCase.Scope.ALL,
                    onClick = { choice = DeleteCourseSeriesUseCase.Scope.ALL }).padding(4.dp)) {
                    RadioButton(selected = choice == DeleteCourseSeriesUseCase.Scope.ALL, onClick = null)
                    Text("删除全部（含已上的历史）", style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.selectable(selected = choice == DeleteCourseSeriesUseCase.Scope.FUTURE_ONLY,
                    onClick = { choice = DeleteCourseSeriesUseCase.Scope.FUTURE_ONLY }).padding(4.dp)) {
                    RadioButton(selected = choice == DeleteCourseSeriesUseCase.Scope.FUTURE_ONLY, onClick = null)
                    Text("仅删除未来（保留历史记录）", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
