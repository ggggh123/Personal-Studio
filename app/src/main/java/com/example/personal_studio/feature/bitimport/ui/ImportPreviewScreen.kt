package com.example.personal_studio.feature.bitimport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitimport.ImportViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam

@Composable
fun ImportPreviewScreen(
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    WizardScaffold(
        stepNumber = 4, totalSteps = 4,
        title = "confirm-import",
        onBack = null,
        primaryLabel = "确认导入",
        onPrimary = vm::onConfirm,
        primaryEnabled = ui.previewItems.isNotEmpty() && !ui.writing,
        secondaryLabel = "取消",
        onSecondary = vm::onCancel,
    ) {
        Text("学期: ${ui.previewTerm?.code ?: "?"}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("课程: ${ui.previewItems.map { it.seriesId }.toSet().size} 门")
        Text("节次: ${ui.previewItems.size} 节 (展开后)")
        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "[收起列表 ▲]" else "[展开列表 ▼]")
        }
        if (expanded) {
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 240.dp)) {
                items(ui.previewItems.distinctBy { it.seriesId }) { item ->
                    Text(
                        "${item.title} · ${item.instructor ?: "—"} · " +
                            "周${item.weekdayCode} ${item.periodIndex}-${item.periodEndIndex}节",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (ui.countToReplace > 0) {
            Box(Modifier.fillMaxWidth().border(1.dp, Amber, RoundedCornerShape(4.dp))
                .background(Amber.copy(alpha = 0.10f), RoundedCornerShape(4.dp)).padding(12.dp)) {
                Text(
                    "⚠ 将覆盖学期 ${ui.previewTerm?.code} 已有的 ${ui.countToReplace} 条旧导入数据。\n" +
                        "   MANUAL 手输的课程和其他学期不受影响。",
                    color = Foam,
                )
            }
        }
    }
}
