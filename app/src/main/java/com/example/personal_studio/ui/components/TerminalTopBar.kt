package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/**
 * Header for pages below the tabs (天光 sub-screen bar): a back arrow when [onBack] is set,
 * a serif title, an optional caption line, the [trailing] actions, and an inset hairline.
 *
 * The signature is kept from the old terminal bar so callers did not need to change: the
 * first line of [subtitle] is the title (a leading `#` is dropped), further lines join into
 * the caption, and [route] is the title only when there is no subtitle. Status-bar padding
 * is applied here, so screens using this bar should not add their own.
 */
@Composable
fun TerminalTopBar(
    route: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (title, caption) = remember(route, subtitle) { titleAndCaption(route, subtitle) }
    val colors = Tg.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                // With a back button its 12 dp inner padding lines the arrow up on 20 dp.
                .padding(start = if (onBack != null) 8.dp else 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.text,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Box(modifier = Modifier.padding(start = 12.dp)) { trailing() }
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
    }
}

internal fun titleAndCaption(route: String, subtitle: String?): Pair<String, String> {
    val lines = subtitle.orEmpty().lines()
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
    val title = lines.firstOrNull() ?: route
    return title to lines.drop(1).joinToString(" · ")
}
