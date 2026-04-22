package com.example.personal_studio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Phosphor

/**
 * 2-step square cursor. On/off at 1Hz. Used as a streaming tail or input-line cursor.
 */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = Phosphor,
) {
    val transition = rememberInfiniteTransition(label = "cursor-blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier = modifier
            .size(width = 8.dp, height = 14.dp)
            .background(color.copy(alpha = alpha)),
    )
}
