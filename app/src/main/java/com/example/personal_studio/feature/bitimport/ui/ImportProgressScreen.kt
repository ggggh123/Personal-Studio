package com.example.personal_studio.feature.bitimport.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitimport.ImportViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Tg

/** Step 1 of the import: each finished step gets a check, the running one a breathing dot. Back is held. */
@Composable
fun ImportProgressScreen(
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    BackHandler {} // the school session is mid-flight; leaving here would strand it

    WizardScaffold(
        stepNumber = 1, totalSteps = 3,
        title = "导入课表",
        caption = "正在获取课表",
        onBack = null,
        primaryLabel = "请稍候",
        onPrimary = {}, primaryEnabled = false,
    ) {
        ui.progressSteps.forEachIndexed { idx, label ->
            val done = idx < ui.progressSteps.size - 1
            Row(Modifier.padding(vertical = 8.dp).enterUp(true, 0), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (done) {
                        Box(Modifier.size(22.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = "完成", tint = c.accent, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        BlinkingCursor()
                    }
                }
                Text(
                    label.removeSuffix("...").removeSuffix("…"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) c.textSecondary else c.text,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text("通常需要 5–15 秒，完成后会先给你看要导入的内容。", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
