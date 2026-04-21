package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Phosphor
import kotlinx.coroutines.delay

/**
 * Hybrid typewriter effect for streaming LLM output.
 *
 * Contract:
 * - The first [charByCharLimit] characters type out one-by-one at [charIntervalMs] each.
 * - After the prefix has played, every subsequent text growth is rendered instantly.
 * - Count is **monotonic within a single streaming message** — it never rewinds when
 *   [text] grows. If [text] shrinks (brand-new message / session reset), we rewind to 0.
 * - A blinking cursor trails the last visible character while text is still growing.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    charByCharLimit: Int = 20,
    charIntervalMs: Long = 25L,
    showCursor: Boolean = true,
    style: TextStyle = LocalTextStyle.current,
) {
    // Do NOT key remember() on [text] — that resets the counter every streaming chunk,
    // which looks like repeated "first few characters" flickering.
    var visibleCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        // If [text] shrank below what we've already shown, it's a brand-new message.
        // Rewind so the typewriter effect replays from the beginning.
        if (text.length < visibleCount) visibleCount = 0

        // Char-by-char phase for the first [charByCharLimit] characters.
        while (visibleCount < minOf(charByCharLimit, text.length)) {
            delay(charIntervalMs)
            visibleCount = (visibleCount + 1).coerceAtMost(text.length)
        }

        // Past the prefix — jump instantly to the tail of what's been streamed so far.
        if (visibleCount >= charByCharLimit && visibleCount < text.length) {
            visibleCount = text.length
        }
    }

    Row(modifier = modifier) {
        Text(text.take(visibleCount), style = style)
        if (showCursor) {
            Spacer(Modifier.width(2.dp))
            BlinkingCursor(color = Phosphor)
        }
    }
}
