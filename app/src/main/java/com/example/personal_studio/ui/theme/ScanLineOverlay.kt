package com.example.personal_studio.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Adds a faint static CRT scan-line pattern over the content it wraps.
 * The effect is deliberately subtle (~2.5% alpha) — it reads as texture,
 * not decoration. Static: do not animate.
 */
fun Modifier.scanLines(spacingDp: Float = 3f, alpha: Float = 0.025f): Modifier =
    this.drawWithCache {
        val stripe = Phosphor.copy(alpha = alpha)
        val spacingPx = spacingDp.dp.toPx()
        onDrawWithContent {
            drawContent()
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = stripe,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += spacingPx
            }
        }
    }

/**
 * Adds a soft radial vignette (darker at corners). Call AFTER scanLines in the modifier chain
 * to layer correctly (scan lines are beneath the vignette so both are visible).
 */
fun Modifier.vignette(strength: Float = 0.35f): Modifier =
    this.drawWithCache {
        val brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = strength)),
            radius = maxOf(size.width, size.height),
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }

/**
 * Convenience wrapper: applies both scanLines + vignette at the app background layer.
 */
@Composable
fun TerminalBackdrop(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().scanLines().vignette()) {
        content()
    }
}
