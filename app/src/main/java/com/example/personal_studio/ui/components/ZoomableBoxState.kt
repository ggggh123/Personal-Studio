package com.example.personal_studio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Zoom + pan state for [zoomable] + [zoomTransform].
 *
 * Gesture writes hit plain `mutableStateOf` directly (synchronous, required
 * since `awaitEachGesture` is a restricted-suspension scope). Snap-back uses
 * a local [Animatable] to drive interpolation between start and target; the
 * animation's [Job] is stored on the state so a fresh gesture can cancel it.
 */
@Stable
class ZoomableBoxState internal constructor(
    val minScale: Float,
    val maxScale: Float,
) {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set

    /** Container (gesture) rect size; required for snap-back bounds math. */
    var containerSize by mutableStateOf(Size.Zero)
        internal set

    internal var settleJob: Job? = null

    /** Jump to 1x, centered. Safe to call from non-suspend contexts. */
    fun reset() {
        settleJob?.cancel()
        settleJob = null
        scale = 1f
        offset = Offset.Zero
    }

    internal fun set(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = newOffset
    }

    /**
     * Animate toward in-bounds scale/offset. Called after each gesture end;
     * if the user over-panned or left bounds due to kinetic overshoot, we
     * smoothly bounce back (~220ms). Guarded by [settleJob] so a new gesture
     * can cancel a running animation cleanly.
     */
    internal suspend fun settle() = coroutineScope {
        val targetScale = scale.coerceIn(minScale, maxScale)
        val targetOffset = clampedOffset(targetScale)
        if (scale == targetScale && offset == targetOffset) return@coroutineScope
        val startScale = scale
        val startOffset = offset
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(SETTLE_MS)) {
            val t = value
            scale = startScale + (targetScale - startScale) * t
            offset = Offset(
                startOffset.x + (targetOffset.x - startOffset.x) * t,
                startOffset.y + (targetOffset.y - startOffset.y) * t,
            )
        }
    }

    /**
     * For scale>1: offset allowed in `[container*(1-scale), 0]` per axis so
     * the scaled content always covers the container (no fly-off). For
     * scale≤1: content is smaller than the container → center it (offset=0).
     */
    private fun clampedOffset(scale: Float): Offset {
        if (containerSize == Size.Zero || scale <= 1f) return Offset.Zero
        val minX = containerSize.width * (1f - scale)
        val minY = containerSize.height * (1f - scale)
        return Offset(offset.x.coerceIn(minX, 0f), offset.y.coerceIn(minY, 0f))
    }

    companion object {
        private const val SETTLE_MS = 220
    }
}

@Composable
fun rememberZoomableBoxState(
    minScale: Float = 1f,
    maxScale: Float = 5f,
): ZoomableBoxState = remember { ZoomableBoxState(minScale, maxScale) }

/**
 * Pointer-input Modifier that drives [state].
 *
 * Gesture rules:
 * - **2+ fingers**: pinch to zoom around the centroid + pan. Consumed here.
 * - **1 finger, down-pos owned by `hitTest`**: pass through so a child (e.g.
 *   a corner-drag overlay) gets the events. Never consumes.
 * - **1 finger, elsewhere, scale > 1**: pan the view.
 * - **1 finger, elsewhere, scale = 1**: no-op (and does not consume).
 *
 * On gesture end, launches [ZoomableBoxState.settle] to clamp scale/offset
 * to the valid range with a ~220ms animation.
 */
fun Modifier.zoomable(
    state: ZoomableBoxState,
    hitTest: (Offset) -> Boolean = { false },
): Modifier = this
    .onSizeChanged { state.containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
    .pointerInput(state, hitTest) {
        coroutineScope {
            val scope = this
            awaitEachGesture {
                state.settleJob?.cancel()
                state.settleJob = null

                val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val ownedByChild = hitTest(firstDown.position)
                var handledHere = false
                do {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val active = event.changes.filter { it.pressed }
                    when {
                        active.size >= 2 -> {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val old = state.scale
                                val new = (old * zoomChange).coerceIn(state.minScale, state.maxScale)
                                val scaleDelta = if (old == 0f) 1f else new / old
                                val proposed = Offset(
                                    state.offset.x * scaleDelta + centroid.x * (1 - scaleDelta),
                                    state.offset.y * scaleDelta + centroid.y * (1 - scaleDelta),
                                ) + panChange
                                state.set(new, rubberBand(proposed, new, state.containerSize))
                                event.changes.forEach { it.consume() }
                                handledHere = true
                            }
                        }
                        active.size == 1 && !ownedByChild -> {
                            // Allow 1-finger pan at every scale. At scale > 1
                            // within bounds it's 1:1; outside bounds and at
                            // scale=1 it's rubber-banded so the user gets
                            // visible feedback and the snap-back animation
                            // on release has something to spring from.
                            val change = active[0]
                            if (change.positionChanged()) {
                                val delta = change.position - change.previousPosition
                                val proposed = state.offset + delta
                                state.set(state.scale, rubberBand(proposed, state.scale, state.containerSize))
                                change.consume()
                                handledHere = true
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })
                if (handledHere) {
                    state.settleJob = scope.launch { state.settle() }
                }
            }
        }
    }

/**
 * iOS-style rubber-band. Inside the valid `[min,max]` bounds per axis, offset
 * is untouched. Outside, movement is multiplied by [RUBBER_FACTOR] so the
 * image "sticks" and snap-back on release is dramatic.
 *
 * At scale≤1, bounds collapse to `[0,0]` on both axes → any non-zero offset
 * is always over-bound, so every drag feels stiff but visible and springs
 * back cleanly.
 */
private fun rubberBand(offset: Offset, scale: Float, container: Size): Offset {
    if (container == Size.Zero) return offset
    val minX: Float
    val minY: Float
    if (scale > 1f) {
        minX = container.width * (1f - scale)
        minY = container.height * (1f - scale)
    } else {
        minX = 0f
        minY = 0f
    }
    return Offset(
        rubberBandAxis(offset.x, minX, 0f),
        rubberBandAxis(offset.y, minY, 0f),
    )
}

private fun rubberBandAxis(v: Float, min: Float, max: Float): Float = when {
    v > max -> max + (v - max) * RUBBER_FACTOR
    v < min -> min + (v - min) * RUBBER_FACTOR
    else -> v
}

private const val RUBBER_FACTOR = 0.4f

/** Apply the state's transform as a visual layer. Uses `TransformOrigin(0,0)`. */
fun Modifier.zoomTransform(state: ZoomableBoxState): Modifier =
    this.graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
        transformOrigin = TransformOrigin(0f, 0f)
    }
