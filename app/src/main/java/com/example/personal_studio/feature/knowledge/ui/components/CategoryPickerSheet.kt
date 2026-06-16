package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

/**
 * 终端风底部弹层:选已有分类或新建。无状态——调用方给 categories + 选中 id + 回调。
 * 点已有行 → onPick + onDismiss;输入名 + [新建] → onCreate + onDismiss;拖/返回 → onDismiss。
 * 分类用 forEach(数量少),不用 LazyColumn——TerminalBottomSheet 内是普通 Column,
 * 放 LazyColumn 会高度无界。
 */
@Composable
fun CategoryPickerSheet(
    categories: List<KbCategory>,
    selectedId: Long?,
    onPick: (KbCategory) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    TerminalBottomSheet(onDismiss = onDismiss, header = "选择分类") {
        categories.forEach { c ->
            val sel = c.id == selectedId
            Text(
                if (sel) "▎ ${c.name}  ✓" else "▎ ${c.name}",
                color = if (sel) Phosphor else Foam,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(c); onDismiss() }
                    .padding(vertical = 8.dp),
            )
        }
        Text(
            "$ + 新建分类",
            color = FoamDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier.weight(1f).padding(8.dp),
            )
            Text(
                "[新建]",
                color = if (newName.isBlank()) FoamDim else Phosphor,
                modifier = Modifier
                    .clickable(enabled = newName.isNotBlank()) {
                        onCreate(newName.trim())
                        newName = ""
                        onDismiss()
                    }
                    .padding(8.dp),
            )
        }
    }
}
