package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.llm.GenerationSettings
import com.example.personal_studio.core.llm.ReasoningLevel
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.theme.Tg

/** What each reasoning level does, in plain words. */
internal fun reasoningHint(level: ReasoningLevel): String = when (level) {
    ReasoningLevel.AUTO -> "交给服务决定。内置 DeepSeek 默认先思考（高档）再回答。"
    ReasoningLevel.OFF -> "不思考直接回答，最快；难题和计算更容易出错。"
    ReasoningLevel.LOW -> "少量思考，偏向速度。"
    ReasoningLevel.MEDIUM -> "适中。DeepSeek 没有“中”档，会按“高”处理。"
    ReasoningLevel.HIGH -> "充分思考，适合难题和推导，回答会慢一些。"
    ReasoningLevel.MAX -> "尽量多想，最慢、用量最大，留给特别难的问题。"
}

internal fun temperatureHint(value: Float?): String = when {
    value == null -> "自动：问答用 0.7；整理知识、生成标题用 0.3。"
    value <= 0.3f -> "很稳定：同一个问题的回答几乎不变，适合解题和整理。"
    value <= 1.0f -> "适中：表达有变化，内容仍然可靠。"
    else -> "很发散：更有创意，也更容易跑题或出错。"
}

internal fun maxTokensLabel(value: Int?): String = value?.let { "${it / 1024}K" } ?: "自动"

/** "推理 自动 · 温度 0.7": the settings in one line. */
internal fun generationSummary(g: GenerationSettings): String = listOfNotNull(
    "推理 ${g.reasoning.label}",
    "温度 ${g.temperature?.let { "%.1f".format(it) } ?: "自动"}",
    g.maxTokens?.let { "输出上限 ${maxTokensLabel(it)}" },
    g.topP?.let { "Top P ${"%.2f".format(it)}" },
).joinToString(" · ")

/**
 * 生成参数: reasoning level, temperature, output limit and top-p, each with what it does. They apply
 * to the built-in models and a custom endpoint alike; "自动" sends nothing and leaves it to the service.
 */
@Composable
internal fun GenerationSettingsCard(
    settings: GenerationSettings,
    onReasoning: (ReasoningLevel) -> Unit,
    onTemperature: (Float?) -> Unit,
    onMaxTokens: (Int?) -> Unit,
    onTopP: (Float?) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    TgCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "对内置模型和自定义接口都生效。拿不准时保持“自动”。",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f),
                )
                val changed = settings != GenerationSettings()
                Text(
                    "恢复默认", style = MaterialTheme.typography.bodyMedium,
                    color = if (changed) c.accent else c.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = changed, role = Role.Button, onClick = onReset)
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }

            Label("推理等级", Modifier.padding(top = 8.dp))
            val levels = ReasoningLevel.entries
            TgSegmented(
                levels.map { it.label }, levels.indexOf(settings.reasoning),
                onSelect = { onReasoning(levels[it]) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), width = 600.dp,
            )
            Hint(reasoningHint(settings.reasoning))

            SwitchRow(
                "温度", settings.temperature?.let { "%.1f".format(it) } ?: "自动",
                checked = settings.temperature != null,
                onChecked = { onTemperature(if (it) 0.7f else null) },
                modifier = Modifier.padding(top = 20.dp),
            )
            settings.temperature?.let { t ->
                StepSlider(t, 0f..GenerationSettings.TEMPERATURE_MAX, steps = 19, label = "温度", onDone = onTemperature)
            }
            Hint(temperatureHint(settings.temperature) + "开启思考时 DeepSeek 不使用温度。")

            Label("最大输出长度", Modifier.padding(top = 20.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf<Int?>(null) + GenerationSettings.MAX_TOKEN_CHOICES).forEach { n ->
                    TgChip(maxTokensLabel(n), selected = settings.maxTokens == n, onClick = { onMaxTokens(n) })
                }
            }
            Hint("一次回答最多生成多少（按 token 计，1 个汉字约 1 个）。太小会截断长回答；自动时由服务决定，Anthropic 格式自动为 32K。")

            SwitchRow(
                "Top P", settings.topP?.let { "%.2f".format(it) } ?: "自动",
                checked = settings.topP != null,
                onChecked = { onTopP(if (it) 0.9f else null) },
                modifier = Modifier.padding(top = 20.dp),
            )
            settings.topP?.let { p ->
                StepSlider(p, 0.05f..1f, steps = 18, label = "Top P", onDone = onTopP)
            }
            Hint("另一种控制随机性的方法，只从概率最高的一部分词里选。一般保持自动；设置后 Anthropic 格式不再发送温度。")
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Tg.colors.text, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Tg.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SwitchRow(title: String, value: String, checked: Boolean, onChecked: (Boolean) -> Unit, modifier: Modifier) {
    val c = Tg.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(if (checked) "自定义 · $value" else "自动", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        TgSwitch(checked, onChecked)
    }
}

/** A slider that saves when the finger lifts, not on every step. */
@Composable
private fun StepSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, onDone: (Float) -> Unit) {
    val c = Tg.colors
    var current by remember(value) { mutableFloatStateOf(value) }
    Slider(
        value = current,
        onValueChange = { current = it },
        onValueChangeFinished = { onDone(current) },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}
