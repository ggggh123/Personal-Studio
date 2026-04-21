package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Rule

/**
 * Wraps AI-response content with a `── gemini ──` header and a subtle left rule. If
 * [footer] is non-null (typically on Done), renders it beneath the content.
 */
@Composable
fun AiFrame(
    header: String = "gemini",
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .drawBehind {
                drawLine(
                    color = Rule,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 2f,
                )
            }
            .padding(start = 10.dp),
    ) {
        Row {
            Text(
                text = "── ",
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
            )
            Text(
                text = " ",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "─".repeat(20),
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )
        }
        Spacer(Modifier.height(6.dp))
        content()
        if (footer != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )
        }
    }
}
