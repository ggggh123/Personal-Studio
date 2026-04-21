package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// TEMPORARY STUB — replaced by the full implementation in Task 14/15.
@Composable
fun ChatDetailScreen(sessionId: Long, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "chat detail · session $sessionId · wiring in T14-T15",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
