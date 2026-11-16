package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.delay

/**
 * Full-screen sheet for checking the AI-organised draft before it goes into the knowledge
 * base: title, category, the question (mistakes only), the summary and suggested links,
 * all editable. Hosts state from SaveToKnowledgeViewModel:
 *  - [onCancel] closes without saving (also while the model is still working)
 *  - [onConfirm] saves the possibly edited draft
 *  - [onRetry] asks the model again after an error
 *
 * Renders nothing in Idle or Saved; the caller navigates after Saved.
 */
@Composable
fun SavePreviewModal(
    state: SaveToKnowledgeUiState,
    onCancel: () -> Unit,
    onConfirm: (KbEntryDraft) -> Unit,
    onRetry: () -> Unit,
) {
    if (state is SaveToKnowledgeUiState.Idle || state is SaveToKnowledgeUiState.Saved) return

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Tg.colors.bg)) {
            when (state) {
                is SaveToKnowledgeUiState.Loading -> Working("正在整理要点", "模型会写出标题、分类和摘要，通常要十几秒。", onCancel)
                is SaveToKnowledgeUiState.Saving -> Working("正在存入", null, onCancel)
                is SaveToKnowledgeUiState.Error -> ErrorBlock(state.message, state.canRetry, onRetry, onCancel)
                is SaveToKnowledgeUiState.Preview -> PreviewBody(state.draft, onCancel, onConfirm)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Working(title: String, body: String?, onCancel: () -> Unit) {
    val c = Tg.colors
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(title) {
        seconds = 0
        while (true) { delay(1_000); seconds++ }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text)
            BlinkingCursor()
        }
        Text(
            (body?.let { "$it\n" } ?: "") + "已等待 $seconds 秒",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        TgPillButton("取消", onClick = onCancel, style = PillStyle.SECONDARY, modifier = Modifier.padding(top = 28.dp).widthIn(min = 120.dp))
    }
}

@Composable
private fun ErrorBlock(message: String, canRetry: Boolean, onRetry: () -> Unit, onCancel: () -> Unit) {
    val c = Tg.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("没能整理好", style = MaterialTheme.typography.headlineMedium, color = c.text)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(top = 10.dp))
        Text("可以再试一次；如果反复失败，检查设置里的模型配置和网络。", style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TgPillButton("取消", onClick = onCancel, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            if (canRetry) TgPillButton("重试", onClick = onRetry, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewBody(initial: KbEntryDraft, onCancel: () -> Unit, onConfirm: (KbEntryDraft) -> Unit) {
    val c = Tg.colors
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.categorySuggestion) }
    var question by remember { mutableStateOf(initial.standardizedQuestion.orEmpty()) }
    var summary by remember { mutableStateOf(initial.summaryMarkdown) }
    var related by remember { mutableStateOf(initial.relatedEntryTitles) }
    val isMistake = initial.standardizedQuestion != null

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "不保存，关闭", tint = c.text) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("存入知识库", style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text(if (isMistake) "错题 · 检查后保存" else "笔记 · 检查后保存", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            TgPillButton("保存", onClick = {
                onConfirm(
                    initial.copy(
                        title = title.trim().ifBlank { initial.title },
                        categorySuggestion = category.trim().ifBlank { "其它" },
                        standardizedQuestion = question.takeIf { it.isNotBlank() },
                        summaryMarkdown = summary,
                        relatedEntryTitles = related,
                    ),
                )
            })
        }
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(c.line))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (initial.isFallback) {
                item(key = "fallback") {
                    Text(
                        when (initial.fallbackReason) {
                            KbDraftFallbackReason.MISSING_REQUIRED_FIELDS -> "模型的结果缺了几项，已用原文补上，保存前请检查。"
                            else -> "模型的结果没能读懂，已用原文填好，保存前请检查。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                        modifier = Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(14.dp)).padding(14.dp),
                    )
                }
            }
            item(key = "title") { TgTextField(title, { title = it }, label = "标题", modifier = Modifier.enterUp(true, 0)) }
            item(key = "category") {
                TgTextField(category, { category = it }, label = "分类", hint = "没有这个分类时会自动新建", modifier = Modifier.enterUp(true, 40))
            }
            if (isMistake) {
                item(key = "question") { MarkdownField("题目", question, { question = it }, Modifier.enterUp(true, 80)) }
            }
            item(key = "summary") { MarkdownField(if (isMistake) "讲解" else "摘要", summary, { summary = it }, Modifier.enterUp(true, 120)) }
            if (related.isNotEmpty()) {
                item(key = "related") {
                    Column {
                        Text("关联条目", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            related.forEach { t -> RemovableChip(t) { related = related - t } }
                        }
                        Text("模型建议的已有条目，点 × 取消关联。", style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    }
                }
            }
        }
    }
}

/** Rendered Markdown with a 预览 / 源码 switch, so formulas can be checked before saving. */
@Composable
private fun MarkdownField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    var raw by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(
                if (raw) "看预览" else "改源码",
                style = MaterialTheme.typography.bodyMedium,
                color = c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { raw = !raw }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        AnimatedContent(raw, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "mdField") { isRaw ->
            if (isRaw) {
                TgTextField(value, onChange, singleLine = false, minLines = 5, modifier = Modifier.padding(top = 4.dp))
            } else {
                val shape = RoundedCornerShape(14.dp)
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().clip(shape).border(1.dp, c.line, shape).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (value.isBlank()) Text("（空）", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    else MathMarkdownView(markdown = value, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.height(36.dp).clip(shape).border(1.dp, c.textTertiary, shape).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp))
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "取消关联 $label", onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
