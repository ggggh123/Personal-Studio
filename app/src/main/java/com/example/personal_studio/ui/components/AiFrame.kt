package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

/**
 * Color used for the AI frame border + header rule. Intentionally softer than the top-bar
 * phosphor divider so the bordered box reads as grouping, not as a loud bright outline
 * (an earlier version at full `Phosphor` made the box feel like a button).
 */
private val FrameStroke = Phosphor.copy(alpha = 0.45f)

/**
 * Visual container for an AI response. Renders the content inside a softly phosphor-bordered
 * box with a faint phosphor fill, a `── <HEADER> ────...` banner along the top, and an
 * optional footer line rendered BELOW the box. The header's trailing rule is drawn as a
 * flex-fill line (not `─` characters) so long model ids never overflow to a second line.
 */
@Composable
fun AiFrame(
    header: String = "llm",
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Phosphor.copy(alpha = 0.04f))
                .border(1.dp, FrameStroke)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "── ",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrameStroke,
                    maxLines = 1,
                )
                Text(
                    text = header.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                // Flex-fill rule. Using a drawn line (instead of repeating the `─`
                // glyph) means the header row stays on one line regardless of how long
                // the model id is — the rule just shrinks to whatever width remains.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .drawBehind {
                            drawLine(
                                color = FrameStroke,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1f,
                            )
                        }
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
        if (footer != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
