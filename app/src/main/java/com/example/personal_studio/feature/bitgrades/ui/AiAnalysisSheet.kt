package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.MarkdownText
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgBottomSheet
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.theme.Tg

/**
 * AI 学情报告: the model's reading of the grade book, streamed in; when it is done, ask a
 * follow-up in 问答 or generate again. A cached report says when it was made.
 */
@Composable
fun AiAnalysisSheet(
    text: String,
    analyzing: Boolean,
    error: String?,
    generatedAt: Long?,
    fromCache: Boolean,
    onAskInChat: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    TgBottomSheet(onDismiss = onDismiss, header = "AI 学情报告") {
        Column(Modifier.fillMaxWidth()) {
            if (generatedAt != null && !analyzing) {
                Text(
                    (if (fromCache) "上次生成于 " else "生成于 ") + TodayText.ago(generatedAt, System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.padding(bottom = 10.dp))
            }
            if (text.isBlank()) {
                if (analyzing) {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("正在读你的成绩", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        BlinkingCursor()
                    }
                }
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    MarkdownText(text = text)
                    if (analyzing) BlinkingCursor(Modifier.padding(top = 6.dp))
                }
            }
            if (!analyzing && text.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TgPillButton("重新生成", onClick = onRegenerate, icon = Icons.Outlined.Refresh, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
                    TgPillButton("去问答追问", onClick = onAskInChat, icon = Icons.Outlined.ChatBubbleOutline, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
