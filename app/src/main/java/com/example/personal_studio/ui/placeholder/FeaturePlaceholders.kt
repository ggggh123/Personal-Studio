package com.example.personal_studio.ui.placeholder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@Composable fun ScannerPlaceholder(onStartSmokeCapture: (() -> Unit)? = null) =
    Placeholder(
        command = "ls scans/",
        output = "no documents yet",
        hint = "camera + edge detection lands in P2",
        action = onStartSmokeCapture?.let { "[▸ test capture]" to it },
    )

@Composable fun KnowledgePlaceholder() = Placeholder("grep -r . kb/", "no entries yet",
    "archive a chat response in P3 to populate this index")

@Composable fun TimelinePlaceholder() = Placeholder("cat day.log", "no events today",
    "timeline + notifications arrive in P4")

@Composable
private fun Placeholder(
    command: String,
    output: String,
    hint: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Amber)) { append("user@study") }
                withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                withStyle(SpanStyle(color = Foam)) { append(command) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(output, style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(24.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                withStyle(SpanStyle(color = FoamDim)) { append(hint) }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                action.first,
                style = MaterialTheme.typography.bodyMedium,
                color = Phosphor,
                modifier = Modifier.clickable(onClick = action.second),
            )
        }
    }
}
