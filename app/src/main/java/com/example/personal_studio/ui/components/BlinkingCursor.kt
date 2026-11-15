package com.example.personal_studio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/**
 * "Still working" mark: a small accent dot that breathes (1.2 s ease in/out). Replaces the
 * old blinking block cursor; the name is kept so callers did not change. Use it only next
 * to something that is in progress, never as decoration.
 */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = Tg.colors.accent,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    Box(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .size(6.dp)
            .clip(CircleShape)
            // Alpha is read in the draw phase only, so the pulse never recomposes.
            .drawBehind { drawRect(color.copy(alpha = alpha)) },
    )
}
