package com.example.personal_studio.feature.bitimport.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitimport.ImportViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun ImportProgressScreen(
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    BackHandler {} // swallow back during long-running flow

    WizardScaffold(
        stepNumber = 3, totalSteps = 4,
        title = "fetching...",
        onBack = null,
        primaryLabel = "稍候...",
        onPrimary = {}, primaryEnabled = false,
    ) {
        ui.progressSteps.forEachIndexed { idx, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val mark = if (idx < ui.progressSteps.size - 1) "✓" else "○"
                Text(mark, color = Phosphor)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("(此屏不可返回，请稍候 5-15 秒)",
            color = FoamDim, style = MaterialTheme.typography.labelMedium)
    }
}
