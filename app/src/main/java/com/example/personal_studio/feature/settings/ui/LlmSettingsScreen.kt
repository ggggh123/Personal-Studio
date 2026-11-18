package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.personal_studio.core.llm.ApiFormat
import com.example.personal_studio.core.llm.BuiltInLlm
import com.example.personal_studio.core.llm.BundledLlm
import com.example.personal_studio.core.llm.LlmEndpoints
import com.example.personal_studio.feature.settings.vm.SettingsUiState
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg

/** A well-known address for a format, offered as a one-tap fill. */
internal data class AddressSuggestion(val label: String, val url: String)

internal fun addressSuggestions(format: ApiFormat): List<AddressSuggestion> = when (format) {
    ApiFormat.OPENAI -> listOf(
        AddressSuggestion("DeepSeek", "https://api.deepseek.com"),
        AddressSuggestion("OpenAI", "https://api.openai.com/v1"),
        AddressSuggestion("OpenRouter", "https://openrouter.ai/api/v1"),
        AddressSuggestion("硅基流动", "https://api.siliconflow.cn/v1"),
        AddressSuggestion("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    )
    ApiFormat.ANTHROPIC -> listOf(
        AddressSuggestion("Anthropic", "https://api.anthropic.com"),
        AddressSuggestion("DeepSeek", "https://api.deepseek.com/anthropic"),
    )
    ApiFormat.GEMINI -> listOf(
        AddressSuggestion("Google AI Studio", "https://generativelanguage.googleapis.com"),
    )
}

internal fun formatHint(format: ApiFormat): String = when (format) {
    ApiFormat.OPENAI -> "最通用：OpenAI、DeepSeek、OpenRouter、硅基流动、通义千问、Kimi、智谱、本地 Ollama 等都支持。地址填到 /v1 这一级，会自动补上 /chat/completions。"
    ApiFormat.ANTHROPIC -> "Claude 官方接口，以及提供 Anthropic 兼容接口的服务（如 DeepSeek 的 /anthropic）。地址会自动补上 /v1/messages。"
    ApiFormat.GEMINI -> "Google AI Studio 的原生接口，密钥在 aistudio.google.com 申请。地址留空即可，会自动补上 /v1beta/models/…。"
}

internal fun modelPlaceholder(format: ApiFormat): String = when (format) {
    ApiFormat.OPENAI -> "例如 deepseek-flash、gpt-5.5、qwen-max"
    ApiFormat.ANTHROPIC -> "例如 claude-sonnet-4-6"
    ApiFormat.GEMINI -> "例如 gemini-3.5-flash"
}

/** What the screen says is in effect for each field of the custom endpoint. */
internal data class CustomEndpointStatus(val address: String, val key: String, val model: String, val active: String)

internal fun customEndpointStatus(state: SettingsUiState, bundled: BundledLlm): CustomEndpointStatus {
    val format = state.savedFormat
    val address = state.savedBaseUrl ?: LlmEndpoints.defaultBaseUrl(format, bundled)
    val builtInHost = LlmEndpoints.host(address)?.let { it == LlmEndpoints.host(bundled.baseUrl) } == true && bundled.apiKey.isNotBlank()
    return CustomEndpointStatus(
        address = state.savedBaseUrl ?: "未填写，使用 $address",
        key = when {
            state.savedApiKey != null -> "已保存自定义密钥"
            builtInHost -> "未填写，使用内置 DeepSeek 密钥"
            else -> "未填写（这个地址需要你自己的密钥）"
        },
        model = state.savedModel ?: if (builtInHost) "未填写，使用 ${bundled.model}" else "未填写（必填）",
        active = if (state.custom) "已启用：问答和整理知识正在使用这里的接口。"
        else "尚未启用：保存地址或密钥后，就会改用这里的接口。",
    )
}

/**
 * 自定义接口: the format, address, key and model of the person's own LLM service, each saved on its
 * own, with the common addresses one tap away and a connection test. The built-in key is never
 * shown, and is only used when the address is on the built-in host.
 */
@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val toast = LocalShellToast.current
    val c = Tg.colors
    val format = state.savedFormat
    val status = customEndpointStatus(state, BuiltInLlm.bundled)
    KeyboardHandledByInsets()

    Column(Modifier.fillMaxSize().imePadding()) {
        TgTopBar(route = "自定义接口", subtitle = "自定义接口\n用自己的大模型服务", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                status.active + "按顺序选格式、填地址、密钥和模型名，最后测试连接。回到上一页选内置模型，随时可以切回。",
                style = MaterialTheme.typography.bodyMedium, color = c.text,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(c.accentSoft, RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .enterUp(true, 0),
            )

            TgGroupLabel("1 · 接口格式", Modifier.enterUp(true, 20))
            TgCard(Modifier.enterUp(true, 20)) {
                Column(Modifier.padding(16.dp)) {
                    val formats = ApiFormat.entries
                    TgSegmented(
                        listOf("OpenAI 兼容", "Anthropic", "Gemini"), formats.indexOf(format),
                        onSelect = { vm.onFormatSelected(formats[it]) },
                        modifier = Modifier.fillMaxWidth(), width = 600.dp,
                    )
                    Text(formatHint(format), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp))
                }
            }

            TgGroupLabel("2 · 接口地址", Modifier.enterUp(true, 40))
            Section(current = status.address, modifier = Modifier.enterUp(true, 40)) {
                TgTextField(
                    state.baseUrlDraft, vm::onBaseUrlDraftChanged,
                    placeholder = addressSuggestions(format).first().url,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    addressSuggestions(format).forEach { s ->
                        TgChip(s.label, selected = state.baseUrlDraft == s.url, onClick = { vm.onBaseUrlDraftChanged(s.url) })
                    }
                }
                Actions(
                    save = { vm.onSaveBaseUrl(); toast?.show("地址已保存") }, canSave = state.baseUrlDraft.isNotBlank(),
                    resetLabel = "清除", reset = { vm.onResetBaseUrl(); toast?.show("已清除地址") }, canReset = state.savedBaseUrl != null,
                )
            }

            TgGroupLabel("3 · API 密钥", Modifier.enterUp(true, 60))
            Section(current = status.key, modifier = Modifier.enterUp(true, 60)) {
                TgTextField(
                    state.apiKeyDraft, vm::onApiKeyDraftChanged,
                    placeholder = if (state.savedApiKey != null) "输入新的密钥以替换" else "粘贴密钥，例如 sk-…",
                    hint = "只保存在这台手机上，不会显示出来。",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Actions(
                    save = { vm.onSaveApiKey(); toast?.show("密钥已保存") }, canSave = state.apiKeyDraft.isNotBlank(),
                    resetLabel = "清除", reset = { vm.onClearApiKey(); toast?.show("已清除密钥") }, canReset = state.savedApiKey != null,
                )
            }

            TgGroupLabel("4 · 模型", Modifier.enterUp(true, 80))
            Section(current = status.model, modifier = Modifier.enterUp(true, 80)) {
                TgTextField(
                    state.modelDraft, vm::onModelDraftChanged,
                    placeholder = modelPlaceholder(format),
                    hint = "填服务商文档里的模型 ID（区分大小写），不是网页上显示的名字。",
                )
                Actions(
                    save = { vm.onSaveModel(); toast?.show("模型已保存") }, canSave = state.modelDraft.isNotBlank(),
                    resetLabel = "清除", reset = { vm.onResetModel(); toast?.show("已清除模型") }, canReset = state.savedModel != null,
                )
            }

            TgGroupLabel("5 · 测试连接", Modifier.enterUp(true, 100))
            ConnectionTestCard(state.testConnection, onTest = vm::onTestConnection, modifier = Modifier.enterUp(true, 100))
            Text(
                "推理等级、温度和输出长度在上一页的“生成参数”里设置，对这里的接口同样生效。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp).enterUp(true, 100),
            )
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
