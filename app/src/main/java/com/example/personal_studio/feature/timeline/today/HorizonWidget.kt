package com.example.personal_studio.feature.timeline.today

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import com.example.personal_studio.ui.theme.daylight.MoonPhase
import com.example.personal_studio.ui.theme.daylight.SolarCalculator
import com.example.personal_studio.ui.theme.daylight.SunDay
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The small horizon in the 今日 header. Drawn in a 120 × 66 unit space (the design canvas's
 * viewBox) scaled to 132 × 72 dp: the day's sun path as a dotted arc, the sun at its real
 * elevation over 北京, and at night the moon (real phase, position by night progress) under
 * a few twinkling stars. Ground at y = 50 hides whatever is below the horizon.
 *
 * Opening the page lets sun and moon rise into place; a tap toggles the sunrise / sunset
 * line; long-press and drag scrubs through the day (15 units per hour) while [onScrub]
 * previews the palette at that hour.
 */
@Composable
fun HorizonWidget(
    date: LocalDate,
    hour: Double,
    sun: SunDay,
    rise: Boolean,
    onTap: () -> Unit,
    onScrub: (Double?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val view = LocalView.current
    val solar = remember { SolarCalculator() }
    val density = LocalDensity.current
    val unitPx = with(density) { (132.dp / 120f).toPx() }

    // Bodies start 16 units below the ground and rise with the gentle spring.
    val below = remember { Animatable(if (rise) 16f else 0f) }
    LaunchedEffect(Unit) { below.animateTo(0f, TgMotion.gentle()) }
    val halo by animateFloatAsState(if (below.targetValue == 0f) 0.17f else 0f, tween(900, delayMillis = 600), label = "halo")

    // While scrubbing the drawn hour follows the finger exactly; on release it eases back.
    var scrubbing by remember { mutableStateOf(false) }
    var dragHours by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    val shownHour = hour + if (scrubbing) dragHours else settle.value
    val latestHour by rememberUpdatedState(hour)
    val latestSun by rememberUpdatedState(sun)
    val latestScrub by rememberUpdatedState(onScrub)
    var justScrubbed by remember { mutableStateOf(false) }
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            settle.stop()
        } else if (dragHours != 0f) {
            settle.snapTo(dragHours)
            dragHours = 0f
            settle.animateTo(0f, TgMotion.gentle())
        }
    }

    val stars = rememberInfiniteTransition(label = "stars")
    val twinkle = STARS.mapIndexed { i, s ->
        stars.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(s.periodMs / 2, delayMillis = s.delayMs), RepeatMode.Reverse),
            label = "star$i",
        )
    }

    val h = ((shownHour % 24) + 24) % 24
    val nightLength = 24 - sun.sunset + sun.sunrise
    val u = (((h - sun.sunset) % 24) + 24) % 24 / nightLength
    val starsOn = u > 0.04 && u < 0.96
    val starAlpha by animateFloatAsState(if (starsOn) 1f else 0f, tween(1400), label = "starAlpha")
    val sunOn = h > sun.sunrise - 0.6 && h < sun.sunset + 0.6
    val sunAlpha by animateFloatAsState(if (sunOn) 1f else 0f, tween(600), label = "sunAlpha")
    val moonOn = u <= 1.06
    val moonAlpha by animateFloatAsState(if (moonOn) 1f else 0f, tween(600), label = "moonAlpha")
    val nearRise = scrubbing && abs(h - sun.sunrise) < 0.1
    val nearSet = scrubbing && abs(h - sun.sunset) < 0.1
    val riseR by animateFloatAsState(if (nearRise) 2.8f else 1.5f, TgMotion.fast(), label = "riseR")
    val setR by animateFloatAsState(if (nearSet) 2.8f else 1.5f, TgMotion.fast(), label = "setR")

    val track = remember(date, sun) {
        (0..24).map { i ->
            val th = sun.sunrise + (sun.sunset - sun.sunrise) * i / 24.0
            Offset(xOf(th, sun), (50 - max(-1.0, solar.elevationAt(date, th)) * 0.45).toFloat())
        }
    }
    val sunPos = Offset(xOf(h, sun), (50 - solar.elevationAt(date, h) * 0.45).toFloat())
    val moonPos = Offset(
        (10 + 100 * min(u, 1.06)).toFloat(),
        (if (u <= 1) 50 - sin(PI * u) * 24 else 50 + (u - 1) * 70).toFloat(),
    )
    val phase = remember(date, h.toInt()) {
        MoonPhase.fraction(date.atStartOfDay(solar.zone).toInstant().plusSeconds((h * 3600).toLong()))
    }

    Canvas(
        modifier
            .size(132.dp, 72.dp)
            .semantics {
                contentDescription = description
                onClick(label = "查看日出日落") { onTap(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (justScrubbed) justScrubbed = false else onTap()
                })
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        justScrubbed = true
                        dragHours = settle.value
                        scrubbing = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        latestScrub(latestHour + dragHours)
                    },
                    onDragEnd = { scrubbing = false; latestScrub(null) },
                    onDragCancel = { scrubbing = false; latestScrub(null) },
                    onDrag = { change, amount ->
                        change.consume()
                        val before = latestHour + dragHours
                        // 15 units of width per hour of the day.
                        dragHours += amount.x / unitPx * 4f / 60f
                        val after = latestHour + dragHours
                        if (crosses(before, after, latestSun.sunrise) || crosses(before, after, latestSun.sunset)) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        latestScrub(after)
                    },
                )
            },
    ) {
        scale(unitPx, pivot = Offset.Zero) {
            // Sun path.
            val path = Path().apply {
                track.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(
                path, c.textTertiary,
                style = Stroke(width = 0.9f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 2.6f))),
            )
            // Stars.
            if (starAlpha > 0f) {
                STARS.forEachIndexed { i, s ->
                    drawCircle(c.text, s.r, Offset(s.x, s.y), alpha = starAlpha * twinkle[i].value)
                }
            }
            // Moon: soft halo plus the lit part for today's phase.
            if (moonAlpha > 0f) {
                translate(moonPos.x, moonPos.y + below.value) {
                    drawCircle(c.textSecondary, 6.4f, Offset.Zero, alpha = 0.22f * moonAlpha)
                    drawPath(moonPath(phase, 6f), c.text, alpha = moonAlpha)
                }
            }
            // Sun.
            if (sunAlpha > 0f) {
                val p = sunPos.copy(y = sunPos.y + below.value)
                drawCircle(c.accent, 10.5f, p, alpha = halo * sunAlpha)
                drawCircle(c.accent, 6f, p, alpha = sunAlpha)
            }
            // Ground, horizon, sunrise and sunset marks.
            drawRect(c.bg, topLeft = Offset(-12f, 50f), size = androidx.compose.ui.geometry.Size(144f, 20f))
            drawLine(c.line, Offset(-2f, 50f), Offset(122f, 50f), strokeWidth = 1f)
            drawCircle(c.textSecondary, riseR, Offset(10f, 50f))
            drawCircle(c.textSecondary, setR, Offset(110f, 50f))
        }
    }
}

private fun crosses(a: Double, b: Double, mark: Double): Boolean {
    fun norm(x: Double) = ((x % 24) + 24) % 24
    val lo = min(a, b)
    val hi = max(a, b)
    if (hi - lo >= 24) return true
    val m = norm(mark)
    val start = norm(lo)
    val span = hi - lo
    val d = ((m - start) % 24 + 24) % 24
    return d in 0.0..span && span > 0
}

private fun xOf(hour: Double, sun: SunDay): Float =
    (10 + 100 * (hour - sun.sunrise) / (sun.sunset - sun.sunrise)).toFloat()

/**
 * The lit part of a moon of radius [r] centred at the origin; [p] is the phase fraction
 * (0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter). Same construction as the design
 * canvas: a half disc on the lit side closed by a half ellipse along the terminator.
 */
internal fun moonPath(p: Double, r: Float): Path {
    val rx = (r * abs(cos(2 * PI * p))).toFloat().coerceAtLeast(0.01f)
    val disc = Rect(-r, -r, r, r)
    val terminator = Rect(-rx, -r, rx, r)
    return Path().apply {
        moveTo(0f, -r)
        if (p < 0.5) {
            arcTo(disc, -90f, 180f, false) // right half, top → bottom
            arcTo(terminator, 90f, if (p < 0.25) -180f else 180f, false)
        } else {
            arcTo(disc, -90f, -180f, false) // left half, top → bottom
            arcTo(terminator, 90f, if (p < 0.75) -180f else 180f, false)
        }
        close()
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val periodMs: Int, val delayMs: Int)

private val STARS = listOf(
    Star(20f, 12f, 0.95f, 3400, 0),
    Star(41f, 6f, 0.8f, 4100, 700),
    Star(63f, 15f, 0.9f, 3700, 1400),
    Star(86f, 8f, 0.8f, 4600, 300),
    Star(104f, 22f, 0.7f, 3900, 2000),
)
