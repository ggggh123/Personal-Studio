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
import com.example.personal_studio.BuildConfig
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

/** True when the user's own endpoint, key or a model outside the built-in list is in use. */
internal fun SettingsUiState.usingCustomEndpoint(): Boolean =
    savedBaseUrl != null || savedApiKey != null || CuratedModels.displayFor(savedModel ?: BuildConfig.DEFAULT_LLM_MODEL) == null

/**
 * AI 模型: pick one of the built-in models (endpoint and key included), test the current
 * one, or go to the custom endpoint settings behind a warning.
 */
@Composable
fun AiModelScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    val active = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL
    val custom = state.usingCustomEndpoint()
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
            if (custom) {
                Text(
                    "正在使用自定义接口（模型 $active）。选择下面的内置模型会切回内置接口，并清除自定义的地址和密钥。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(c.accentSoft, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .enterUp(true, 0),
                )
            }
            TgGroupLabel("内置模型", Modifier.enterUp(true, 40))
            TgCard(Modifier.enterUp(true, 40)) {
                CuratedModels.ALL.forEachIndexed { i, m ->
                    val selected = !custom && m.code == active
                    TgSettingRow(
                        m.display, summary = m.code, first = i == 0,
                        onClick = { vm.selectCuratedModel(m.code) },
                        trailing = {
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = "当前模型", tint = c.accent, modifier = Modifier.size(20.dp))
                        },
                    )
                }
            }

            TgGroupLabel("连接", Modifier.enterUp(true, 80))
            ConnectionTestCard(state.testConnection, onTest = vm::onTestConnection, modifier = Modifier.enterUp(true, 80))

            TgGroupLabel("高级", Modifier.enterUp(true, 120))
            TgCard(Modifier.enterUp(true, 120)) {
                TgSettingRow(
                    "自定义接口", icon = Icons.Outlined.Tune, first = true,
                    summary = "使用自己的 API 地址、密钥和模型（OpenAI 兼容格式）",
                    onClick = { showWarning = true },
                )
            }
        }
    }

    if (showWarning) {
        TgConfirmDialog(
            title = "自定义接口",
            message = "适合已经熟悉通过 API 调用大模型的用户。填错地址或密钥会让问答和整理知识无法使用，随时可以回到这里选回内置模型。",
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
