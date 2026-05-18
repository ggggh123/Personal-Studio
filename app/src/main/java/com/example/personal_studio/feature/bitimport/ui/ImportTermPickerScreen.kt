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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitimport.ImportViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam

@Composable
fun ImportTermPickerScreen(
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val selected = ui.selectedTerm ?: ui.currentTerm
    val isNonCurrent = selected?.code != ui.currentTerm?.code && ui.currentTerm != null

    WizardScaffold(
        stepNumber = 2, totalSteps = 4,
        title = "select-term",
        onBack = { /* keeps state; nav reset handled by host */ },
        primaryLabel = "抓取课表 →",
        onPrimary = vm::onProceedFromTermPicker,
        primaryEnabled = selected != null,
    ) {
        Text("当前学期: ${ui.currentTerm?.code ?: "?"}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        if (isNonCurrent) {
            Box(Modifier.fillMaxWidth().border(1.dp, Amber, RoundedCornerShape(4.dp))
                .background(Amber.copy(alpha = 0.10f), RoundedCornerShape(4.dp)).padding(12.dp)) {
                Text(
                    "⚠ 切换到非当前学期将使用现有学期开始日期来计算时间，可能不准确。" +
                        "建议先到 Settings → 学期 手动设置该学期开始日期。",
                    color = Foam, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(ui.terms) { term ->
                val isCurrent = term.code == ui.currentTerm?.code
                val label = buildString {
                    append(term.display ?: term.code)
                    if (isCurrent) append("  (当前)")
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    RadioButton(
                        selected = selected?.code == term.code,
                        onClick = { vm.onChangeTerm(term) },
                    )
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
