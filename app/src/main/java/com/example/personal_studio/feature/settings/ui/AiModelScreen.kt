package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.core.llm.CuratedModel
import com.example.personal_studio.core.llm.CuratedModels
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun AiModelScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL
    var showWarning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "settings/ai-model", subtitle = "# AI 模型", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (CuratedModels.displayFor(active) == null) {
                Text("当前：$active（自定义）", color = Amber, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "选择一个内置模型（端点与密钥已内置）：",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            CuratedModels.ALL.forEach { m ->
                ModelRow(m, selected = m.code == active) { vm.selectCuratedModel(m.code) }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "⚙ AI 模型高级设置（自定义接口 / 密钥 / 模型）",
                color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable { showWarning = true }.padding(vertical = 8.dp),
            )
        }
    }

    if (showWarning) {
        TerminalConfirmDialog(
            title = "高级设置",
            message = "进行此操作之前，请确保你已经对通过 API 调用大模型的流程十分熟悉。",
            confirmLabel = "我已了解,继续",
            onConfirm = { showWarning = false; onOpenAdvanced() },
            onDismiss = { showWarning = false },
        )
    }
}

@Composable
private fun ModelRow(m: CuratedModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "● " else "○ ",
            color = if (selected) Phosphor else FoamMute,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            m.display,
            color = if (selected) Foam else FoamMute,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
