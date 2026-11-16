package com.example.personal_studio.feature.scanner.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.feature.timeline.today.pressScale
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.theme.Tg

/**
 * A scanned document as a sheet of paper: the first page on a portrait card, with a second
 * sheet peeking out behind it when there is more than one page, then the title and
 * "N 页 · date". Long-press is optional (the chat picker has no menu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScanDocCover(
    doc: ScanDocumentSummary,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClickLabel = if (onLongClick != null) "重命名或删除" else null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.74f).padding(end = 6.dp, top = 6.dp)) {
            if (doc.pageCount > 1) {
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .offset(x = 5.dp, y = (-5).dp)
                        .graphicsLayer { rotationZ = 2.5f }
                        .clip(shape)
                        .background(c.surfaceRaised)
                        .border(1.dp, c.line, shape),
                )
            }
            ScanThumbnail(path = doc.coverPath, width = maxWidth, height = maxHeight, corner = 12.dp)
        }
        Text(
            doc.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
        Text(meta, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(start = 2.dp, top = 1.dp))
    }
}
