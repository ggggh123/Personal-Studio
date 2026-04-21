package com.example.personal_studio.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ChatPlaceholder() = Placeholder("Chat · coming in P1")

@Composable
fun ScannerPlaceholder() = Placeholder("Scanner · coming in P2")

@Composable
fun KnowledgePlaceholder() = Placeholder("Knowledge · coming in P3")

@Composable
fun TimelinePlaceholder() = Placeholder("Timeline · coming in P4")

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
