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
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

/**
 * Renders nothing when [related] is empty. Otherwise shows a divider header
 * "─── 相关条目 ───" followed by one clickable row per related entry, each
 * with a Phosphor leader/arrow and the entry title.
 */
@Composable
fun RelatedEntriesSection(
    related: List<KbEntry>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (related.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("─────── 相关条目 ───────", color = FoamDim, style = MaterialTheme.typography.bodySmall)
        related.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▎", color = Phosphor)
                Text(entry.title, color = Foam, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("→", color = Phosphor)
            }
        }
    }
}
