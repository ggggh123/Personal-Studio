package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.io.File

/**
 * Mistake-list row: 80×80 thumbnail of the staged original image, title (single
 * line), standardizedQuestion 1-line excerpt, plus meta footer. Whole row clickable.
 */
@Composable
fun KbMistakeRow(entry: KbEntry, onClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick(entry.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(80.dp).clip(RoundedCornerShape(2.dp)).background(Void),
            contentAlignment = Alignment.Center,
        ) {
            entry.originalImagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp),
                )
            } ?: Text("IMG", color = FoamDim, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                entry.title,
                color = Foam,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.standardizedQuestion.orEmpty(),
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.categoryName ?: "其它"} · entry #${entry.id}",
                color = Phosphor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
