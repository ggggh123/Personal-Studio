package com.example.personal_studio.feature.settings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.llm.BuiltInLlm
import com.example.personal_studio.core.llm.CuratedModels
import com.example.personal_studio.feature.settings.vm.SettingsUiState
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.settings.vm.TestConnectionState
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgConfirmDialog
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSettingRow
import com.example.personal_studio.ui.theme.Tg

/** True when the person's own endpoint is in use (an address or key is saved). */
internal fun SettingsUiState.usingCustomEndpoint(): Boolean = custom

/**
 * AI 模型: the two built-in DeepSeek models (endpoint and key included), the generation settings
 * that apply to every request, a connection test, and the custom endpoint behind a warning.
 */
@Composable
fun AiModelScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    val endpoint = state.endpoint
    val custom = state.custom
    val noBuiltInKey = BuiltInLlm.bundled.apiKey.isBlank()
    var showWarning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TgTopBar(route = "AI 模型", subtitle = "AI 模型\n问答、整理知识和学情报告使用", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            val notice = when {
                custom && endpoint != null -> "正在使用自定义接口（${endpoint.format.label} · ${endpoint.model.ifBlank { "未填写模型" }}）。" +
                    "选择下面的内置模型会切回内置 DeepSeek，并清除自定义的地址和密钥。"
                noBuiltInKey -> "这个版本没有内置密钥，内置模型暂时不能用。请在下方“自定义接口”里填写自己的服务。"
                else -> null
            }
            notice?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = c.text,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(c.accentSoft, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .enterUp(true, 0),
                )
            }
            TgGroupLabel("内置模型 · DeepSeek", Modifier.enterUp(true, 40))
            TgCard(Modifier.enterUp(true, 40)) {
                CuratedModels.ALL.forEachIndexed { i, m ->
                    val selected = !custom && endpoint?.model == m.code
                    TgSettingRow(
                        m.display, summary = m.tagline, first = i == 0,
                        onClick = { vm.selectCuratedModel(m.code) },
                        trailing = {
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = "当前模型", tint = c.accent, modifier = Modifier.size(20.dp))
                        },
                    )
                }
            }

            TgGroupLabel("生成参数", Modifier.enterUp(true, 80))
            GenerationSettingsCard(
                state.generation,
                onReasoning = vm::onReasoningChanged,
                onTemperature = vm::onTemperatureChanged,
                onMaxTokens = vm::onMaxTokensChanged,
                onTopP = vm::onTopPChanged,
                onReset = vm::onResetGeneration,
                modifier = Modifier.enterUp(true, 80),
            )

            TgGroupLabel("连接", Modifier.enterUp(true, 120))
            ConnectionTestCard(state.testConnection, onTest = vm::onTestConnection, modifier = Modifier.enterUp(true, 120))

            TgGroupLabel("高级", Modifier.enterUp(true, 160))
            TgCard(Modifier.enterUp(true, 160)) {
                TgSettingRow(
                    "自定义接口", icon = Icons.Outlined.Tune, first = true,
                    summary = if (custom) "正在使用 · ${endpoint?.format?.label}" else "OpenAI 兼容、Anthropic、Google Gemini 格式，用自己的地址、密钥和模型",
                    onClick = { if (custom) onOpenAdvanced() else showWarning = true },
                )
            }
        }
    }

    if (showWarning) {
        TgConfirmDialog(
            title = "自定义接口",
            message = "适合已经有自己的大模型服务（或 API 密钥）的用户。填错地址或密钥会让问答和整理知识无法使用，随时可以回到这里选回内置模型。",
            confirmLabel = "继续",
            onConfirm = { showWarning = false; onOpenAdvanced() },
            onDismiss = { showWarning = false },
        )
    }
}

/** Sends one tiny request to the current endpoint and model, and shows how it went. */
@Composable
internal fun ConnectionTestCard(test: TestConnectionState, onTest: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    TgCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("测试连接", style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Text("向当前模型发一条很短的消息，检查地址、密钥和网络。", style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                TgPillButton(
                    "测试", onClick = onTest, style = PillStyle.SECONDARY,
                    enabled = test !is TestConnectionState.Running,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            AnimatedContent(test, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "test") { t ->
                when (t) {
                    TestConnectionState.Idle -> Unit
                    TestConnectionState.Running -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("正在连接", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        BlinkingCursor()
                    }
                    is TestConnectionState.Success -> Text(
                        "已连通 · 回复：" + t.replyPreview.ifBlank { "（空）" },
                        style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    is TestConnectionState.Failure -> Text(
                        "没有连通 · " + t.message,
                        style = MaterialTheme.typography.bodyMedium, color = c.error, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
