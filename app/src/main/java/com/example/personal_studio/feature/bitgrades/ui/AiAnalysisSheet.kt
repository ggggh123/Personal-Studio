package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisSheet(
    text: String,
    analyzing: Boolean,
    error: String?,
    onAskInChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("$ ai-analysis", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = Carmine) }
            Text(text.ifBlank { if (analyzing) "分析中..." else "" }, color = Foam,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
            Spacer(Modifier.height(12.dp))
            if (!analyzing && text.isNotBlank()) {
                Button(onAskInChat, Modifier.fillMaxWidth()) { Text("在聊天里追问 →") }
            }
        }
    }
}
