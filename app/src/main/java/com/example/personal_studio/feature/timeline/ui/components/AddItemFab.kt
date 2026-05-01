package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemFab(
    onAddTask: () -> Unit,
    onAddCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    ExtendedFloatingActionButton(
        text = { Text("+") },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        onClick = { showSheet = true },
        modifier = modifier,
    )
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Button(
                onClick = { showSheet = false; onAddTask() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text("加 DDL/事件")
            }
            Button(
                onClick = { showSheet = false; onAddCourse() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text("录入课程")
            }
        }
    }
}
