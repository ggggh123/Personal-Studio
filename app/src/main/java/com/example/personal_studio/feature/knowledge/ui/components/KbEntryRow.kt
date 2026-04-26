package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KbEntryRow(entry: KbEntry, onClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick(entry.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▎", color = Phosphor)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                buildAnnotatedString {
                    if (entry.isMistake) {
                        withStyle(SpanStyle(color = Cyan)) { append("⊕ ") }
                    }
                    withStyle(SpanStyle(color = Foam)) { append(entry.title) }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append(entry.categoryName ?: "其它")
                        append(" · ")
                        append(formatRelative(entry.createdAt))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatRelative(ts: Long): String {
    val diff = (System.currentTimeMillis() - ts) / 1000
    return when {
        diff < 60 -> "${diff}s前"
        diff < 3600 -> "${diff / 60}m前"
        diff < 86400 -> "${diff / 3600}h前"
        diff < 604800 -> "${diff / 86400}d前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    }
}
