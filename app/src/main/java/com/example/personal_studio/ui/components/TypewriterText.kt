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
 * Hybrid typewriter effect:
 * - First [charByCharLimit] characters play back one-by-one at [charIntervalMs] per char.
 * - After the prefix has played, the remainder of the string appears instantly.
 * - While streaming (text still growing), a blinking cursor trails the last rendered character.
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
    // Total characters currently visible. Reset whenever text prefix shrinks (e.g. new message).
    var visibleCount by remember(text) { mutableIntStateOf(0) }

    // Char-by-char phase, driven off the current text's length
    LaunchedEffect(text) {
        val prefixGoal = minOf(charByCharLimit, text.length)
        while (visibleCount < prefixGoal) {
            delay(charIntervalMs)
            visibleCount = (visibleCount + 1).coerceAtMost(text.length)
        }
        // After prefix phase, render everything instantly
        if (visibleCount >= charByCharLimit) {
            visibleCount = text.length
        }
    }

    // When text grows past the prefix (streaming chunks), jump to the new length immediately
    LaunchedEffect(text.length) {
        if (visibleCount >= charByCharLimit) visibleCount = text.length
    }

    Row(modifier = modifier) {
        Text(text.take(visibleCount), style = style)
        if (showCursor) {
            Spacer(Modifier.width(2.dp))
            BlinkingCursor(color = Phosphor)
        }
    }
}
