package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.util.SemesterTimeMapper
import com.example.personal_studio.feature.settings.vm.SemesterSettingsViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun SemesterSettingsScreen(
    onBack: () -> Unit,
    vm: SemesterSettingsViewModel = hiltViewModel(),
) {
    val current by vm.current.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "semester", subtitle = "# semester start", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前学期起始日：${current?.toString() ?: "未设置"}", color = Foam)
            Button(onClick = {
                val initial = current ?: java.time.LocalDate.now()
                android.app.DatePickerDialog(ctx, { _, y, m, d ->
                    val picked = java.time.LocalDate.of(y, m + 1, d)
                    vm.setStart(SemesterTimeMapper.normalizeSemesterStart(picked))
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }) { Text("选择") }
            Text("自动回退到所选日期所在周的周一", color = Phosphor, style = MaterialTheme.typography.labelSmall)
        }
    }
}
