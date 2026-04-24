package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

/**
 * Terminal-style top bar. Renders `studio:~/<route> $` on the top row, a bright
 * phosphor dashed separator (insert with horizontal gutters so the line does not
 * bleed to screen edges), and an optional `# ...` comment-style subtitle below —
 * used to surface session + model metadata on chat / settings screens. Status bar
 * padding is applied here, so screens that render this bar should NOT add their
 * own `statusBarsPadding()`.
 */
@Composable
fun TerminalTopBar(
    route: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) { append("studio:") }
                    withStyle(SpanStyle(color = Foam)) { append("~/") }
                    withStyle(SpanStyle(color = Foam)) { append(route) }
                    append(" ")
                    withStyle(SpanStyle(color = Phosphor)) { append("$") }
                },
                style = MaterialTheme.typography.bodyMedium,
                // Clamp to one line and ellipsize — long routes (e.g. scan
                // filenames) would otherwise consume the trailing slot's
                // width and force trailing Text to wrap per-character.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Box { trailing?.invoke() }
        }
        // Phosphor dashed divider with side gutters — deliberately inset from the
        // screen edges so it reads as a terminal rule, not a Material app-bar edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 20.dp)
                .drawBehind {
                    drawLine(
                        color = Phosphor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                    )
                }
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
}
