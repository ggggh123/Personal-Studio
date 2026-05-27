package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.MarkdownText
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // 标题行 + 时间戳
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$ ai-analysis", color = Phosphor, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (generatedAt != null && !analyzing) {
                    Text(
                        (if (fromCache) "上次生成于 " else "生成于 ") + relTime(generatedAt),
                        color = FoamMute, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            error?.let {
                Text(it, color = Carmine, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
            if (text.isBlank()) {
                Text(
                    if (analyzing) "分析中..." else "",
                    color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                ) {
                    MarkdownText(text = text)
                }
            }
            Spacer(Modifier.height(12.dp))
            // 底部按钮区:有内容且不在分析 → 显示「重新生成」+「在聊天里追问」
            if (!analyzing && text.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onRegenerate, Modifier.weight(1f)) { Text("↻ 重新生成") }
                    Button(onAskInChat, Modifier.weight(1f)) { Text("在聊天里追问 →") }
                }
            }
        }
    }
}

/** 相对时间:刚刚 / N 分钟前 / N 小时前 / N 天前 / yyyy-MM-dd HH:mm。 */
private fun relTime(t: Long): String {
    val diff = System.currentTimeMillis() - t
    val min = diff / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "${min}分钟前"
        min < 24 * 60 -> "${min / 60}小时前"
        min < 7 * 24 * 60 -> "${min / (24 * 60)}天前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))
    }
}
