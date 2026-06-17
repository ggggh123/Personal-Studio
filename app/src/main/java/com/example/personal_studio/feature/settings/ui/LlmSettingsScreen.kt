package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.settings.vm.TestConnectionState
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Olive
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void

@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val activeModel = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(
            route = "settings/llm",
            subtitle = "# AI 模型",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── 密钥 ──
            SectionHeader("## 密钥")
            Text(
                "# OpenAI 兼容端点密钥;留空用内置默认。",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = vm::onApiKeyDraftChanged,
                placeholder = {
                    Text(
                        if (state.savedApiKey != null) "**** [已设置] — 输入以替换" else "粘贴 API 密钥 (sk-...)",
                        color = FoamDim, style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = { Text("API_KEY", color = Cyan, style = MaterialTheme.typography.labelSmall) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveApiKey, enabled = state.apiKeyDraft.isNotBlank(), colors = terminalPrimaryButton()) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = vm::onClearApiKey, enabled = state.savedApiKey != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam)) {
                    Text("清除", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── 接口地址 ──
            SectionHeader("## 接口地址")
            Text(
                "# OpenAI 兼容地址,自动补 /chat/completions。例: openrouter.ai/api/v1",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            val activeBaseUrl = state.savedBaseUrl ?: BuildConfig.DEFAULT_API_BASE_URL
            KeyValueRow(
                key = "当前",
                value = activeBaseUrl + if (state.savedBaseUrl == null) "  (默认)" else "",
                valueColor = if (state.savedBaseUrl == null) FoamMute else Foam,
            )
            OutlinedTextField(
                value = state.baseUrlDraft,
                onValueChange = vm::onBaseUrlDraftChanged,
                placeholder = { Text("https://.../v1", color = FoamDim, style = MaterialTheme.typography.bodyMedium) },
                label = { Text("API_BASE_URL", color = Cyan, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveBaseUrl, enabled = state.baseUrlDraft.isNotBlank(), colors = terminalPrimaryButton()) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = vm::onResetBaseUrl, enabled = state.savedBaseUrl != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam)) {
                    Text("恢复默认", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── 模型 ──
            SectionHeader("## 模型")
            Text(
                "# 端点支持的模型 id。例: google/gemini-2.0-flash-exp:free",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            KeyValueRow(
                key = "当前",
                value = activeModel + if (state.savedModel == null) "  (默认)" else "",
                valueColor = if (state.savedModel == null) FoamMute else Foam,
            )
            OutlinedTextField(
                value = state.modelDraft,
                onValueChange = vm::onModelDraftChanged,
                placeholder = { Text("vendor/model-id", color = FoamDim, style = MaterialTheme.typography.bodyMedium) },
                label = { Text("LLM_MODEL", color = Cyan, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveModel, enabled = state.modelDraft.isNotBlank(), colors = terminalPrimaryButton()) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = vm::onResetModel, enabled = state.savedModel != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam)) {
                    Text("恢复默认", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── 连接测试 ──
            SectionHeader("## 连接测试")
            Text(
                "向当前端点 + 模型发一次请求,验证密钥与网络。",
                style = MaterialTheme.typography.bodySmall, color = FoamDim,
            )
            Button(
                onClick = vm::onTestConnection,
                enabled = state.testConnection !is TestConnectionState.Running,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Void),
            ) {
                if (state.testConnection is TestConnectionState.Running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp), color = Void)
                    Spacer(Modifier.size(8.dp))
                    Text("连接中…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("测试连接 ↵", style = MaterialTheme.typography.labelLarge)
                }
            }
            when (val tc = state.testConnection) {
                TestConnectionState.Idle, TestConnectionState.Running -> Unit
                is TestConnectionState.Success -> StatusLine(ok = true, body = tc.replyPreview)
                is TestConnectionState.Failure -> StatusLine(ok = false, body = tc.message)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = Phosphor)
}

@Composable
private fun DashedDivider() {
    Spacer(
        Modifier.fillMaxWidth().height(1.dp).drawBehind {
            drawLine(
                color = Rule, start = Offset(0f, 0f), end = Offset(size.width, 0f),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
    )
}

@Composable
private fun KeyValueRow(key: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.labelSmall, color = Phosphor, modifier = Modifier.width(140.dp))
        Text("= ", style = MaterialTheme.typography.bodyMedium, color = FoamDim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun StatusLine(ok: Boolean, body: String) {
    val tag = if (ok) "成功" else "失败"
    val tagColor = if (ok) Olive else Carmine
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = tagColor)) { append("[$tag] ") }
            withStyle(SpanStyle(color = Foam)) { append(body) }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun terminalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Phosphor, unfocusedBorderColor = Rule, cursorColor = Phosphor,
)

@Composable
private fun terminalPrimaryButton() = ButtonDefaults.buttonColors(
    containerColor = Phosphor, contentColor = Void, disabledContainerColor = Rule, disabledContentColor = FoamDim,
)
