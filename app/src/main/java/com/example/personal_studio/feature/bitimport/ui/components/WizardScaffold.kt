package com.example.personal_studio.feature.bitimport.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgProgressTrack
import com.example.personal_studio.ui.theme.TgMotion

/**
 * Frame of the course-import steps: title bar (with a back arrow when [onBack] is set), a
 * thin progress track for [stepNumber] of [totalSteps], the step's content, and a bottom
 * bar with an optional secondary pill and the primary one.
 */
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
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress by animateFloatAsState(stepNumber.toFloat() / totalSteps, TgMotion.gentle(), label = "wizardStep")
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TerminalTopBar(route = title, subtitle = title + (caption?.let { "\n$it" } ?: ""), onBack = onBack)
        TgProgressTrack(progress, Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp), content = content)
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (secondaryLabel != null && onSecondary != null) {
                TgPillButton(secondaryLabel, onClick = onSecondary, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            }
            TgPillButton(primaryLabel, onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.weight(1f))
        }
    }
}
