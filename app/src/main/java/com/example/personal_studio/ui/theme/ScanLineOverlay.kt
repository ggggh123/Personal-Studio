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
import kotlin.math.hypot

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
 * CRT vignette: a subtle phosphor-tinted glow lifts the center and a corner-heavy
 * radial dim pulls the edges down. Both layers are applied on top of content so the
 * effect reads as "screen has a hot spot," not "content is faded." Call AFTER [scanLines]
 * in the modifier chain to layer correctly.
 *
 * - [cornerDim]: how dark the far corners become (0.0 off, 0.6 very pronounced).
 * - [centerGlow]: how much phosphor lift the center gets (0.0 off, 0.08 noticeable).
 */
fun Modifier.vignette(
    cornerDim: Float = 0.55f,
    centerGlow: Float = 0.05f,
): Modifier =
    this.drawWithCache {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfDiag = hypot(size.width, size.height) / 2f
        // Corner dim: transparent up to ~40% of the diagonal, then ramps to black at
        // the far corners. Covers past the rect bounds so corner alpha is fully realized.
        val cornerBrush = Brush.radialGradient(
            colorStops = arrayOf(
                0.40f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = cornerDim),
            ),
            center = center,
            radius = halfDiag * 1.05f,
        )
        // Center glow: tight phosphor-tinted spot that softens before ~60% of the diagonal.
        val glowBrush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Phosphor.copy(alpha = centerGlow),
                1.0f to Color.Transparent,
            ),
            center = center,
            radius = halfDiag * 0.55f,
        )
        onDrawWithContent {
            drawContent()
            drawRect(glowBrush)
            drawRect(cornerBrush)
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
