package com.example.personal_studio.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens of the 天光 design system. Every movement is a spring so it can be
 * interrupted mid-flight and continue from the current velocity; exits are faster
 * than entrances. Values match the design canvas (response / damping):
 * fast 0.26 s / 0.80, standard 0.40 s / 0.88, gentle 0.60 s / 0.95.
 */
object TgMotion {
    /** Presses, switches, segment indicators, the ＋ rotation. */
    fun <T> fast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.80f, stiffness = 584f)

    /** Page transitions, sheets, card-to-detail transforms. */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.88f, stiffness = 247f)

    /** Sun and moon rising, whole-page entrances, long slides. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.95f, stiffness = 110f)

    private val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Dismissals: accelerate out, quicker than the entrance. */
    fun <T> exit(): FiniteAnimationSpec<T> = tween(durationMillis = 280, easing = ExitEasing)

    /** Light/dark crossfade and other palette changes that should be seen. */
    const val COLOR_CHANGE_MS = 900

    /** Palette changes while the user scrubs through the day. */
    const val COLOR_SCRUB_MS = 120

    /** Easing back to the live palette after a scrub. */
    const val COLOR_RELEASE_MS = 600
}
