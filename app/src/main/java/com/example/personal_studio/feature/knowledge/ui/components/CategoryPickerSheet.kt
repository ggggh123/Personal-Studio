package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/**
 * ModalBottomSheet for picking an existing category or creating a new one.
 * Stateless — caller provides categories + the currently-selected id +
 * callbacks for pick/create/dismiss.
 *
 * Tap an existing row → onPick(category) + onDismiss
 * Type a name + tap [create] → onCreate(name) + onDismiss
 * Drag/back-press → onDismiss
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<KbCategory>,
    selectedId: Long?,
    onPick: (KbCategory) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Void) {
        Column(Modifier.padding(16.dp)) {
            Text("$ select category", color = FoamDim, style = MaterialTheme.typography.bodySmall)
            LazyColumn {
                items(categories, key = { it.id }) { c ->
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
                    "[create]",
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
}
