package com.example.personal_studio.feature.bitimport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun WizardScaffold(
    stepNumber: Int,
    totalSteps: Int,
    title: String,
    onBack: (() -> Unit)?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Void).statusBarsPadding().navigationBarsPadding()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("$ $title", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("[$stepNumber/$totalSteps]", color = FoamDim, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(color = FoamDim.copy(alpha = 0.3f))
        // Body
        Column(
            Modifier.weight(1f).padding(20.dp),
            content = content,
        )
        // Bottom bar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("← 返回", color = FoamDim) }
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel, color = FoamDim) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onPrimary, enabled = primaryEnabled) {
                Text(primaryLabel)
            }
        }
    }
}
