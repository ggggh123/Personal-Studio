package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/**
 * Full-screen modal for previewing + editing the AI-generated KB draft before commit.
 * Caller hosts the SaveToKnowledgeViewModel and provides:
 *  - [state] from vm.uiState
 *  - [onCancel] dismiss without saving
 *  - [onConfirm] commits the (possibly edited) draft
 *  - [onRetry] re-runs the LLM call (only meaningful in Error state)
 *
 * The modal renders nothing when state is Idle or Saved — caller manages
 * post-Saved navigation via a LaunchedEffect.
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
        Box(Modifier.fillMaxSize().background(Void)) {
            when (state) {
                is SaveToKnowledgeUiState.Loading -> Loading(onCancel)
                is SaveToKnowledgeUiState.Saving -> Saving(onCancel)
                is SaveToKnowledgeUiState.Error -> ErrorBlock(state.message, onRetry, onCancel)
                is SaveToKnowledgeUiState.Preview -> PreviewBody(state.draft, onCancel, onConfirm)
                else -> Unit  // Idle / Saved short-circuited above
            }
        }
    }
}

@Composable private fun Loading(onCancel: () -> Unit) =
    InflightSpinner(label = "$ thinking...", onCancel = onCancel)

@Composable private fun Saving(onCancel: () -> Unit) =
    InflightSpinner(label = "$ writing entry...", onCancel = onCancel)

@Composable private fun InflightSpinner(label: String, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Phosphor)
        Spacer(Modifier.height(16.dp))
        Text(label, color = FoamDim)
        Spacer(Modifier.height(24.dp))
        // Visible escape from a slow LLM call. Without this the only way out is the
        // back gesture, which Android 14 predictive-back hides for non-opt-in apps.
        Text(
            "[cancel]",
            color = FoamDim,
            modifier = Modifier.clickable { onCancel() }.padding(8.dp),
        )
    }
}

@Composable private fun ErrorBlock(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("! llm error: $message", color = Carmine)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "[retry]",
                color = Phosphor,
                modifier = Modifier.clickable { onRetry() }.padding(8.dp),
            )
            Text(
                "[cancel]",
                color = FoamDim,
                modifier = Modifier.clickable { onCancel() }.padding(8.dp),
            )
        }
    }
}

@Composable
private fun PreviewBody(initial: KbEntryDraft, onCancel: () -> Unit, onConfirm: (KbEntryDraft) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.categorySuggestion) }
    var standardizedQuestion by remember { mutableStateOf(initial.standardizedQuestion.orEmpty()) }
    var summary by remember { mutableStateOf(initial.summaryMarkdown) }
    var editingSummaryRaw by remember { mutableStateOf(false) }
    var related by remember { mutableStateOf(initial.relatedEntryTitles) }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(Void).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "cancel", tint = FoamDim)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "archive draft",
                style = MaterialTheme.typography.titleMedium,
                color = Foam,
                modifier = Modifier.weight(1f),
            )
            Text(
                "[save]",
                color = Phosphor,
                modifier = Modifier
                    .clickable {
                        onConfirm(
                            initial.copy(
                                title = title.trim().ifBlank { initial.title },
                                categorySuggestion = category.trim().ifBlank { "其它" },
                                standardizedQuestion = standardizedQuestion.takeIf { it.isNotBlank() },
                                summaryMarkdown = summary,
                                relatedEntryTitles = related,
                            ),
                        )
                    }
                    .padding(8.dp),
            )
        }

        // Fallback banner
        if (initial.isFallback) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Amber)) { append("⚠ ") }
                    withStyle(SpanStyle(color = Foam)) {
                        append(
                            when (initial.fallbackReason) {
                                KbDraftFallbackReason.JSON_PARSE_FAILED -> "AI 解析失败，已用原文兜底，请检查"
                                KbDraftFallbackReason.MISSING_REQUIRED_FIELDS -> "AI 输出字段缺失，已用原文兜底，请检查"
                                null -> "已用 fallback，请检查"
                            },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Void)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { FieldLabel("title"); LineField(title) { title = it } }
            item { FieldLabel("category"); LineField(category) { category = it } }
            if (standardizedQuestion.isNotBlank() || initial.standardizedQuestion != null) {
                item {
                    FieldLabel("standardized question")
                    MultilineField(standardizedQuestion) { standardizedQuestion = it }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("summary", Modifier.weight(1f))
                    Text(
                        if (editingSummaryRaw) "[preview]" else "[edit raw markdown]",
                        color = FoamDim,
                        modifier = Modifier
                            .clickable { editingSummaryRaw = !editingSummaryRaw }
                            .padding(8.dp),
                    )
                }
                if (editingSummaryRaw) {
                    MultilineField(summary) { summary = it }
                } else {
                    MathMarkdownView(markdown = summary, modifier = Modifier.fillMaxWidth())
                }
            }
            if (related.isNotEmpty()) {
                item { FieldLabel("related (AI-suggested)") }
                items(related) { relatedTitle ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("▎ $relatedTitle", color = Foam, modifier = Modifier.weight(1f))
                        Text(
                            "✕",
                            color = Carmine,
                            modifier = Modifier
                                .clickable { related = related - relatedTitle }
                                .padding(8.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        "$ $text",
        color = FoamDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable private fun LineField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
        cursorBrush = SolidColor(Phosphor),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
}

@Composable private fun MultilineField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
        cursorBrush = SolidColor(Phosphor),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
}
