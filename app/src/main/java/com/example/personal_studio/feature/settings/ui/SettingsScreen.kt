package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.settings.vm.TestConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
            Text(
                "留空将使用构建时内置的默认 key（release 包可能未内置）。填写后优先使用你的 key。",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = vm::onApiKeyDraftChanged,
                placeholder = {
                    Text(if (state.savedApiKey != null) "•••• 已设置（输入以覆盖）" else "粘贴你的 Gemini API key")
                },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveApiKey, enabled = state.apiKeyDraft.isNotBlank()) {
                    Text("保存")
                }
                OutlinedButton(onClick = vm::onClearApiKey, enabled = state.savedApiKey != null) {
                    Text("清除")
                }
            }

            Spacer(Modifier.width(8.dp))
            Text("连通性测试", style = MaterialTheme.typography.titleMedium)
            Text("点击下面按钮让 Gemini 回一句话，验证 key 与网络。", style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = vm::onTestConnection,
                enabled = state.testConnection !is TestConnectionState.Running,
            ) {
                if (state.testConnection is TestConnectionState.Running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("测试中…")
                } else {
                    Text("测试 Gemini")
                }
            }

            when (val tc = state.testConnection) {
                TestConnectionState.Idle -> Unit
                TestConnectionState.Running -> Unit
                is TestConnectionState.Success -> Text(
                    "✓ 成功：${tc.replyPreview}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is TestConnectionState.Failure -> Text(
                    "✕ 失败：${tc.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
