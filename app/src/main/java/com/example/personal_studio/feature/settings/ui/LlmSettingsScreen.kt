package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg

/**
 * 自定义接口: an OpenAI-compatible key, base URL and model id, each saved on its own, then a
 * connection test. Empty values fall back to the built-in endpoint; the built-in address is
 * never shown.
 */
@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val toast = LocalShellToast.current
    val activeModel = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL
    KeyboardHandledByInsets()

    Column(Modifier.fillMaxSize().imePadding()) {
        TgTopBar(route = "自定义接口", subtitle = "自定义接口\nOpenAI 兼容格式，留空的项使用内置值", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            TgGroupLabel("API 密钥", Modifier.enterUp(true, 0))
            Section(
                current = if (state.savedApiKey != null) "已保存自定义密钥" else "使用内置密钥",
                modifier = Modifier.enterUp(true, 0),
            ) {
                TgTextField(
                    state.apiKeyDraft, vm::onApiKeyDraftChanged,
                    placeholder = if (state.savedApiKey != null) "输入新的密钥以替换" else "粘贴密钥，例如 sk-…",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Actions(
                    save = { vm.onSaveApiKey(); toast?.show("密钥已保存") }, canSave = state.apiKeyDraft.isNotBlank(),
                    resetLabel = "清除", reset = { vm.onClearApiKey(); toast?.show("已改回内置密钥") }, canReset = state.savedApiKey != null,
                )
            }

            TgGroupLabel("接口地址", Modifier.enterUp(true, 40))
            Section(
                current = state.savedBaseUrl ?: "使用内置地址",
                modifier = Modifier.enterUp(true, 40),
            ) {
                TgTextField(
                    state.baseUrlDraft, vm::onBaseUrlDraftChanged,
                    placeholder = "https://…/v1",
                    hint = "会自动补上 /chat/completions，例如 openrouter.ai/api/v1",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Actions(
                    save = { vm.onSaveBaseUrl(); toast?.show("地址已保存") }, canSave = state.baseUrlDraft.isNotBlank(),
                    resetLabel = "恢复内置", reset = { vm.onResetBaseUrl(); toast?.show("已改回内置地址") }, canReset = state.savedBaseUrl != null,
                )
            }

            TgGroupLabel("模型", Modifier.enterUp(true, 80))
            Section(
                current = activeModel + if (state.savedModel == null) "（内置默认）" else "",
                modifier = Modifier.enterUp(true, 80),
            ) {
                TgTextField(
                    state.modelDraft, vm::onModelDraftChanged,
                    placeholder = "vendor/model-id",
                    hint = "接口支持的模型名，例如 google/gemini-2.0-flash-exp:free",
                )
                Actions(
                    save = { vm.onSaveModel(); toast?.show("模型已保存") }, canSave = state.modelDraft.isNotBlank(),
                    resetLabel = "恢复内置", reset = { vm.onResetModel(); toast?.show("已改回内置模型") }, canReset = state.savedModel != null,
                )
            }

            TgGroupLabel("连接", Modifier.enterUp(true, 120))
            ConnectionTestCard(state.testConnection, onTest = vm::onTestConnection, modifier = Modifier.enterUp(true, 120))
        }
    }
}

@Composable
private fun Section(current: String, modifier: Modifier, content: @Composable () -> Unit) {
    val c = Tg.colors
    TgCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前：$current", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            content()
        }
    }
}

@Composable
private fun Actions(save: () -> Unit, canSave: Boolean, resetLabel: String, reset: () -> Unit, canReset: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TgPillButton(resetLabel, onClick = reset, style = PillStyle.SECONDARY, enabled = canReset, modifier = Modifier.weight(1f))
        TgPillButton("保存", onClick = save, enabled = canSave, modifier = Modifier.weight(1f))
    }
}
