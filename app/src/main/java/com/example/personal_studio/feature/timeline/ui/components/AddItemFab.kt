package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

/** day 界面右下角"新建"入口:终端方块按钮(Deep 底 + Phosphor 边框 + 绿色 +),
 *  点开终端底部表选择 加 DDL/事件 或 录入课程。 */
@Composable
fun AddItemFab(
    onAddTask: () -> Unit,
    onAddCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(56.dp)
            .background(Deep, RectangleShape)
            .border(1.dp, Phosphor, RectangleShape)
            .clickable { showSheet = true },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Phosphor, style = MaterialTheme.typography.headlineMedium)
    }
    if (showSheet) {
        TerminalBottomSheet(onDismiss = { showSheet = false }, header = "新建") {
            FabActionLine("▸ 加 DDL/事件") { showSheet = false; onAddTask() }
            FabActionLine("▸ 录入课程") { showSheet = false; onAddCourse() }
        }
    }
}

@Composable
private fun FabActionLine(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Foam,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
