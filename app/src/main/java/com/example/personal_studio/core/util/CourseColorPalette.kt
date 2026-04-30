package com.example.personal_studio.core.util

import androidx.compose.ui.graphics.Color

/**
 * Maps a course title to one of 6 phosphor-leaning HSL hues, deterministic by hash.
 *
 * Hue band centred on phosphor green (~140°) ± 40°. Saturation pinned to 60%,
 * lightness to 55%, alpha 100% — values picked empirically against the Terminal
 * theme to feel cohesive without flattening into one indistinguishable green.
 */
object CourseColorPalette {

    private val HUES = floatArrayOf(120f, 140f, 100f, 160f, 80f, 180f)

    fun colorFor(courseTitle: String): Color {
        val h = HUES[Math.floorMod(courseTitle.hashCode(), HUES.size)]
        return Color.hsl(hue = h, saturation = 0.60f, lightness = 0.55f)
    }
}
