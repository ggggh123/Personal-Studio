package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.feature.knowledge.ui.KbText
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.theme.Tg

/**
 * A knowledge entry in the list: title, one line of the summary (or the question for a
 * mistake), and category · time. Mistakes carry a small 错题 tag and their photo.
 */
@Composable
fun KbEntryRow(entry: KbEntry, now: Long, onClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val snippet = remember(entry.summaryMarkdown, entry.standardizedQuestion) { KbText.snippet(entry) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick(entry.id) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.isMistake) {
                    Text(
                        "错题",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accent,
                        modifier = Modifier.background(c.accentSoft, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (snippet.isNotBlank()) {
                Text(
                    snippet, style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                KbText.meta(entry, now), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (entry.originalImagePath != null) {
            ScanThumbnail(path = entry.originalImagePath, width = 52.dp, height = 64.dp, corner = 10.dp)
        }
    }
}
